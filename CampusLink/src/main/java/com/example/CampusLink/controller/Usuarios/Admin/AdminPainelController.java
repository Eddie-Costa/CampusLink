// inicio administrativo apresenta o acesso as ferramentas de gestao
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

// controle do painel esta classe exige identidade administrativa para abrir a pagina
@Controller
public class AdminPainelController {

    private static final Logger logger = LoggerFactory.getLogger(AdminPainelController.class);

    private final AdminUsuarioRepository adminUsuarioRepository;

    public AdminPainelController(AdminUsuarioRepository adminUsuarioRepository) {
        this.adminUsuarioRepository = adminUsuarioRepository;
    }

    // abrir painel administrativo este metodo permite a tela inicial apenas ao administrador
    @GetMapping("/admin/painel")
    public String painelAdmin(HttpSession session, Model model) {


        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
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

    // validar administrador do painel este metodo confere o perfil e o email da conta conectada
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