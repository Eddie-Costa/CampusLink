
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

@Controller
public class AdminGerenciarConteudosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminGerenciarConteudosController.class);

    private final ConteudoService conteudoService;

    public AdminGerenciarConteudosController(ConteudoService conteudoService) {
        this.conteudoService = conteudoService;
    }

    // lista os conteudos de todos os professores
    @GetMapping("/admin/conteudos")
    public String listarConteudos(
            HttpSession session,
            Model model,
            @RequestParam(value = "pagina", defaultValue = "0") int pagina,
            @RequestParam(value = "busca", defaultValue = "") String busca,
            @RequestParam(value = "status", defaultValue = "todos") String status,
            @RequestParam(value = "fragmento", defaultValue = "false") boolean fragmento
    ) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/admin/login";
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        pagina = Math.max(0, pagina);
        busca = busca == null ? "" : busca.trim();
        status = status == null ? "todos" : status.trim().toLowerCase(Locale.ROOT);

        if (!"ativo".equals(status) && !"suspenso_denuncia".equals(status) && !"removido".equals(status)) {
            status = "todos";
        }

        try {
            int totalFiltrados = conteudoService.contarFiltradosParaAdmin(busca, status);
            int totalPaginas = Math.max(1, (totalFiltrados + 9) / 10);

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

            if (fragmento) {
                return "Usuarios/Admin/conteudosAdmin :: blocoConteudos";
            }

        } catch (SQLException | RuntimeException e) {
            logger.error("erro ao carregar os conteudos para o administrador", e);

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

    // verifica se o usuario da sessao e um administrador
    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        if (!(usuarioLogado instanceof loginAdminDTO)) {
            return false;
        }

        if (!(emailSessao instanceof String)) {
            return false;
        }

        loginAdminDTO admin = (loginAdminDTO) usuarioLogado;

        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        return admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}
