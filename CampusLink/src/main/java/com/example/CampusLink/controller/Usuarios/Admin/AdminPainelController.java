package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPainelController {

    private static final String URL_LOGIN_ADMIN = "/gestao/8f3c1d7a-2b94-4e61-a5c8-7d2f9b4a6e31";

    @GetMapping("/admin/painel")
    public String painelAdmin(HttpSession session) {

        // verifica se existe um usuario logado
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // permite somente o perfil de administrador
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        return "Usuarios/Admin/painelAdmin";
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