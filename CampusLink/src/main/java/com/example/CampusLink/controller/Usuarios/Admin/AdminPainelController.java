package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import com.example.CampusLink.repository.AdminUsuarioRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.sql.SQLException;

@Controller
public class AdminPainelController {

    private static final Logger logger = LoggerFactory.getLogger(AdminPainelController.class);

    private final AdminUsuarioRepository adminUsuarioRepository;

    public AdminPainelController(AdminUsuarioRepository adminUsuarioRepository) {
        this.adminUsuarioRepository = adminUsuarioRepository;
    }

    @GetMapping("/admin/painel")
    public String painelAdmin(HttpSession session, Model model) {

        // permite acessar somente se estiver logado como administrador
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        boolean master = false;
        try {
            usuarioAdminDTO admin = adminUsuarioRepository.buscarAdministradorPorEmail((String) session.getAttribute("email2FA"));
            master = admin != null && admin.isMaster();
        } catch (SQLException e) {
            logger.error("Erro ao verificar se o administrador é master.", e);
        }
        model.addAttribute("adminMaster", master);

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