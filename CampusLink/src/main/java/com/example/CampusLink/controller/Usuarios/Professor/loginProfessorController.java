package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.service.LoginAttemptService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class loginProfessorController {
    private static final Logger logger = LoggerFactory.getLogger(loginProfessorController.class);

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

    @GetMapping("/loginProfessor")
    public String loginPage(Model model) {
        model.addAttribute("professor", new loginProfessorDTO());
        return "Usuarios/Professor/loginProfessor";
    }

    @PostMapping("/loginProfessor")
    public String fazerLogin(@Valid @ModelAttribute("professor") loginProfessorDTO loginProfessorDTO, BindingResult result, Model model, HttpSession session) throws SQLException {

        String email = loginProfessorDTO.getEmail();

        //Verifica bloqueio
        if (LoginAttemptService.estaBloqueado(email)) {
            logger.warn("Conta bloqueada para professor {}", email);
            model.addAttribute("mensagemDeErro", "Conta bloqueada por muitas tentativas. Tente mais tarde.");
            return "Usuarios/Professor/loginProfessor";
        }

        if (result.hasErrors()) {
            logger.warn("Dados de login inválidos para professor {}", email);
            model.addAttribute("mensagemDeErro", "Erro ao fazer login, tente novamente!!!");
            return "Usuarios/Professor/loginProfessor";
        }

        //Encriptador
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        //Requisição para o BD Buscar a senha criptografada e comparar com a senha digitada.
        String senhaHash = usuarioDAO.QueryLoginUsuario( "professor", loginProfessorDTO.getEmail());

        if(senhaHash != null && encoder.matches(loginProfessorDTO.getSenha(), senhaHash)) {
            //Sucesso de Login
            loginAttemptService.loginSucesso(email);

            if (twoFactorEnabled) {
                String codigo = twoFactorService.gerarCodigo(email);
                emailService.enviarCodigo(email, codigo);

                session.setAttribute("email2FA", email);
                session.setAttribute("redirect", "Login");
                session.setAttribute("tipoUsuario", "professor");

                return "redirect:/verificarProfessor";
            }

            session.setAttribute("email2FA", email);
            session.setAttribute("redirect", "Login");
            session.setAttribute("tipoUsuario", "professor");
            session.setAttribute("usuarioLogado", usuarioDAO.buscarPorEmailProfessor(email));
            session.setMaxInactiveInterval(900);

            return "redirect:/home";
        }

        //Erro de Login
        loginAttemptService.loginFalhou(email);
        logger.warn("Credenciais inválidas para professor {}. Tentativas: {}", email, loginAttemptService.getTentativas(email));

        if (loginAttemptService.getTentativas(email) >= 5) {
            logger.warn("Conta bloqueada por excesso de tentativas para professor {}", email);
            model.addAttribute("mensagemDeErro", "Conta bloqueada por 10 minutos.");
        } else {
            model.addAttribute("mensagemDeErro", "Email ou senha inválidos.");
        }
        return "Usuarios/Professor/loginProfessor";
    }
}
