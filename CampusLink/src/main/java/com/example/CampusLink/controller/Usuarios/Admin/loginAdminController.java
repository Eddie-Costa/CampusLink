package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.service.LoginAttemptService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.sql.SQLException;

@Controller
public class loginAdminController {

    private static final Logger logger = LoggerFactory.getLogger(loginAdminController.class);

    private static final String URL_LOGIN_ADMIN = "/gestao/8f3c1d7a-2b94-4e61-a5c8-7d2f9b4a6e31";

    @Value("${campuslink.twofa.enabled:true}")
    private boolean twoFactorEnabled;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    @Autowired
    private usuarioDAO usuarioDAO;

    @GetMapping(URL_LOGIN_ADMIN)
    public String loginPage(Model model) {

        model.addAttribute("admin", new loginAdminDTO());

        return "Usuarios/Admin/loginAdmin";
    }

    @PostMapping(URL_LOGIN_ADMIN)
    public String fazerLogin(
            @Valid @ModelAttribute("admin") loginAdminDTO loginAdminDTO,
            BindingResult result,
            Model model,
            HttpSession session
    ) throws SQLException {

        String email = loginAdminDTO.getEmail();

        // verifica se o usuario esta bloqueado
        if (LoginAttemptService.estaBloqueado(email)) {

            logger.warn("Conta bloqueada para administrador {}", email);

            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(
                            null,
                            "WARN",
                            loginAdminController.class.getName(),
                            "fazerLogin",
                            null,
                            MessageFormatter.arrayFormat(
                                    "Conta bloqueada para administrador {}",
                                    new Object[]{email}
                            ).getMessage(),
                            null,
                            null
                    );

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute(
                    "mensagemDeErro",
                    "Conta bloqueada por muitas tentativas. Tente mais tarde."
            );

            return "Usuarios/Admin/loginAdmin";
        }

        if (result.hasErrors()) {

            logger.warn("Dados de login inválidos para administrador {}", email);

            model.addAttribute(
                    "mensagemDeErro",
                    "Erro ao fazer login, tente novamente."
            );

            return "Usuarios/Admin/loginAdmin";
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        String senhaHash = usuarioDAO.QueryLoginUsuario(
                "admin",
                loginAdminDTO.getEmail()
        );

        if (senhaHash != null &&
                !senhaHash.isBlank() &&
                encoder.matches(loginAdminDTO.getSenha(), senhaHash)) {

            loginAttemptService.loginSucesso(email);

            if (twoFactorEnabled) {

                String codigo = twoFactorService.gerarCodigo(email);

                emailService.enviarCodigo(email, codigo);

                session.setAttribute("email2FA", email);
                session.setAttribute("redirect", "Login");
                session.setAttribute("tipoUsuario", "admin");

                return "redirect:/verificarAdmin";
            }

            session.setAttribute("email2FA", email);
            session.setAttribute("redirect", "Login");
            session.setAttribute("tipoUsuario", "admin");
            session.setAttribute(
                    "usuarioLogado",
                    usuarioDAO.buscarPorEmailAdmin(email)
            );

            session.setMaxInactiveInterval(900);

            logger.info(
                    "Login concluído. perfil=admin email={} doisFatores=false",
                    email
            );

            return "redirect:/admin/painel";
        }

        loginAttemptService.loginFalhou(email);

        logger.warn(
                "Credenciais inválidas para administrador {}. Tentativas: {}",
                email,
                loginAttemptService.getTentativas(email)
        );

        if (loginAttemptService.getTentativas(email) >= 5) {

            model.addAttribute(
                    "mensagemDeErro",
                    "Conta bloqueada por 10 minutos."
            );

        } else {

            model.addAttribute(
                    "mensagemDeErro",
                    "Email ou senha inválidos."
            );
        }

        return "Usuarios/Admin/loginAdmin";
    }
}