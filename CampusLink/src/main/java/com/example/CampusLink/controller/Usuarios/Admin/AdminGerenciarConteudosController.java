// catalogo administrativo permite consultar os conteudos de todos os professores

package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.service.ConteudoService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// controle da lista de materiais esta classe aplica filtros e paginacao para o administrador
@Controller
public class AdminGerenciarConteudosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminGerenciarConteudosController.class);

    private final ConteudoService conteudoService;

    // preparar listagem administrativa de materiais este construtor recebe o servico de conteudos
    public AdminGerenciarConteudosController(ConteudoService conteudoService) {
        this.conteudoService = conteudoService;
    }


    // listar conteudos administrativos este metodo aplica busca situacao e paginacao e pode devolver apenas a lista
    @GetMapping("/admin/conteudos")
    public String listarConteudos(
            HttpSession session,
            Model model,
            @RequestParam(value = "pagina", defaultValue = "0") int pagina,
            @RequestParam(value = "busca", defaultValue = "") String busca,
            @RequestParam(value = "status", defaultValue = "todos") String status,
            @RequestParam(value = "fragmento", defaultValue = "false") boolean fragmento
    ) {

        // proteger gerenciamento de conteudos exige login administrativo antes de listar materiais
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/admin/login";
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        pagina = Math.max(0, pagina);
        busca = busca == null ? "" : busca.trim();
        status = status == null ? "todos" : status.trim().toLowerCase(Locale.ROOT);

        // corrigir filtro de conteudos usa todas as situacoes quando o valor recebido nao e reconhecido
        if (!"ativo".equals(status) && !"suspenso_denuncia".equals(status) && !"removido".equals(status)) {
            status = "todos";
        }

        // carregar conteudos administrativos calcula paginacao e filtros e trata falhas de carregamento
        try {
            int totalFiltrados = conteudoService.contarFiltradosParaAdmin(busca, status);
            int totalPaginas = Math.max(1, (totalFiltrados + 9) / 10);

            // limitar pagina consultada usa a ultima pagina quando o numero pedido ultrapassa os resultados
            if (pagina >= totalPaginas) {
                pagina = totalPaginas - 1;
            }

            List<ConteudoDTO> conteudos = conteudoService.listarTodosParaAdminPaginado(pagina, busca, status);

            model.addAttribute("conteudos", conteudos);
            model.addAttribute("busca", busca);
            model.addAttribute("statusSelecionado", status);
            model.addAttribute("totalFiltrados", totalFiltrados);
            model.addAttribute("paginaAtual", pagina);
            model.addAttribute("proximaPagina", pagina + 1);
            model.addAttribute("totalPaginas", totalPaginas);
            model.addAttribute("temMais", (long) pagina * 10 + conteudos.size() < totalFiltrados);

            // responder apenas lista devolve somente a area de conteudos quando a consulta pede uma parte da pagina
            if (fragmento) {
                return "Usuarios/Admin/conteudosAdmin :: blocoConteudos";
            }

        } catch (SQLException | RuntimeException e) {
            logger.error("erro ao carregar os conteudos para o administrador", e);

            // informar falha da lista retorna erro ao navegador quando o carregamento parcial falha
            if (fragmento) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível carregar os conteúdos.", e);
            }

            model.addAttribute("mensagemErro", "Não foi possível carregar os conteúdos.");
            model.addAttribute("conteudos", new ArrayList<ConteudoDTO>());
            model.addAttribute("busca", busca);
            model.addAttribute("statusSelecionado", status);
            model.addAttribute("totalFiltrados", 0);
            model.addAttribute("paginaAtual", 0);
            model.addAttribute("proximaPagina", 1);
            model.addAttribute("totalPaginas", 1);
            model.addAttribute("temMais", false);
        }

        return "Usuarios/Admin/conteudosAdmin";
    }


    // validar administrador da lista de conteudos este metodo confere o perfil e o email da conta conectada
    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        // conferir perfil administrativo rejeita sessoes com outro tipo de usuario
        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        // conferir dados administrativos rejeita um usuario que nao possui os dados de administrador
        if (!(usuarioLogado instanceof loginAdminDTO)) {
            return false;
        }

        // conferir email da sessao rejeita valores que nao sao texto
        if (!(emailSessao instanceof String)) {
            return false;
        }

        loginAdminDTO admin = (loginAdminDTO) usuarioLogado;

        // exigir email do administrador rejeita contas sem email preenchido
        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        return admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}
