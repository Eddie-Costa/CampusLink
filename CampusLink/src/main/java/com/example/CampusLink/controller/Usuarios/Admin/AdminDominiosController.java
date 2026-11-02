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

@Controller
public class AdminDominiosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminDominiosController.class);

    private static final String URL_LOGIN_ADMIN = "/gestao/8f3c1d7a-2b94-4e61-a5c8-7d2f9b4a6e31";

    private final DominioService dominioService;

    public AdminDominiosController(DominioService dominioService) {
        this.dominioService = dominioService;
    }

    @GetMapping("/admin/dominios")
    public String listar(HttpSession session, Model model) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            model.addAttribute("pendentes", dominioService.listarPendentes());
            model.addAttribute("aprovados", dominioService.listarAprovados());

        } catch (RuntimeException e) {
            logger.error("erro ao carregar domínios", e);
            model.addAttribute("mensagemErro", "Não foi possível carregar as solicitações de domínio.");
            model.addAttribute("pendentes", new ArrayList<DominioDTO>());
            model.addAttribute("aprovados", new ArrayList<DominioDTO>());
        }

        return "Usuarios/Admin/dominiosAdmin";
    }

    @PostMapping("/admin/dominios/{id}/aprovar")
    public String aprovar(@PathVariable Long id,
                          @RequestParam(required = false) String comentarioAdmin,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

        return analisar(id, true, comentarioAdmin, session, redirectAttributes);
    }

    @PostMapping("/admin/dominios/{id}/rejeitar")
    public String rejeitar(@PathVariable Long id,
                           @RequestParam(required = false) String comentarioAdmin,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {

        return analisar(id, false, comentarioAdmin, session, redirectAttributes);
    }

    private String analisar(Long id, boolean aprovar, String comentarioAdmin,
                            HttpSession session, RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            dominioService.analisar(id, aprovar, comentarioAdmin);
            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    aprovar ? "Domínio aprovado." : "Solicitação rejeitada.");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());

        } catch (RuntimeException e) {
            logger.error("erro ao analisar solicitação de domínio {}", id, e);
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível analisar a solicitação.");
        }

        return "redirect:/admin/dominios";
    }

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