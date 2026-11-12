// analise de dominios recebe decisoes sobre os enderecos solicitados
package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.DominioDTO;
import com.example.CampusLink.service.DominioService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

// controle de sites permitidos esta classe verifica o administrador antes de aprovar ou recusar pedidos
@Controller
public class AdminDominiosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminDominiosController.class);

    private static final String URL_LOGIN_ADMIN = "/gestao/8f3c1d7a-2b94-4e61-a5c8-7d2f9b4a6e31";

    private final DominioService dominioService;

    // preparar controle de dominios este construtor recebe o servico de enderecos permitidos
    public AdminDominiosController(DominioService dominioService) {
        this.dominioService = dominioService;
    }

    // listar dominios administrativos este metodo mostra os pedidos pendentes e os enderecos aprovados
    @GetMapping("/admin/dominios")
    public String listar(HttpSession session, Model model) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // carregar dominios administrativos busca pedidos e aprovacoes e apresenta listas vazias em caso de falha
        try {
            model.addAttribute("pendentes", dominioService.listarPendentes());
            model.addAttribute("aprovados", dominioService.listarAprovados());

        } catch (RuntimeException e) {
            logger.error("erro ao carregar domínios", e);
            model.addAttribute("mensagemErro", "Não foi possível carregar os pedidos de site.");
            model.addAttribute("pendentes", new ArrayList<DominioDTO>());
            model.addAttribute("aprovados", new ArrayList<DominioDTO>());
        }

        return "Usuarios/Admin/dominiosAdmin";
    }

    // aprovar dominio pela tela este metodo encaminha a aprovacao do endereco solicitado
    @PostMapping("/admin/dominios/{id}/aprovar")
    public String aprovar(@PathVariable Long id,
                          @RequestParam(required = false) String comentarioAdmin,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

        return analisar(id, true, comentarioAdmin, session, redirectAttributes);
    }

    // rejeitar dominio pela tela este metodo encaminha a recusa do endereco solicitado
    @PostMapping("/admin/dominios/{id}/rejeitar")
    public String rejeitar(@PathVariable Long id,
                           @RequestParam(required = false) String comentarioAdmin,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {

        return analisar(id, false, comentarioAdmin, session, redirectAttributes);
    }

    // analisar dominio pela administracao este metodo confere o acesso e registra a decisao informada
    private String analisar(Long id, boolean aprovar, String comentarioAdmin,
                            HttpSession session, RedirectAttributes redirectAttributes) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // analisar pedido de dominio tenta aprovar ou rejeitar o endereco e informa dados invalidos ou falhas
        try {
            dominioService.analisar(id, aprovar, comentarioAdmin);
            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    aprovar ? "Site liberado." : "Pedido recusado.");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());

        } catch (RuntimeException e) {
            logger.error("erro ao analisar solicitação de domínio {}", id, e);
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível analisar a solicitação.");
        }

        return "redirect:/admin/dominios";
    }

    // validar administrador dos dominios este metodo confere o perfil e o email da conta conectada
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