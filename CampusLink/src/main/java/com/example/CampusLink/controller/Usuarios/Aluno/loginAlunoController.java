package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.service.LoginAttemptService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

@Controller
public class loginAlunoController {
    private static final Logger logger = LoggerFactory.getLogger(loginAlunoController.class);

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

    @GetMapping("/loginAluno")
    public String loginPage(Model model) {
        model.addAttribute("aluno", new loginAlunoDTO());
        return "Usuarios/Aluno/loginAluno";
    }

    @PostMapping("/loginAluno")
    public String fazerLogin(@Valid @ModelAttribute("aluno") loginAlunoDTO loginAlunoDTO, BindingResult result, Model model, HttpSession session) throws SQLException {

        String email = loginAlunoDTO.getEmail();

        //Verifica bloqueio
        if (LoginAttemptService.estaBloqueado(email)) {
            logger.warn("Conta bloqueada para aluno {}", email);
            model.addAttribute("mensagemDeErro", "Conta bloqueada por muitas tentativas. Tente mais tarde.");
            return "Usuarios/Aluno/loginAluno";
        }

        if (result.hasErrors()) {
            logger.warn("Dados de login inválidos para aluno {}", email);
            model.addAttribute("mensagemDeErro", "Erro ao fazer login, tente novamente!!!");
            return "Usuarios/Aluno/loginAluno";
        }

        //Encriptador
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        //Requisição para o BD Buscar a senha criptografada e comparar com a senha digitada.
        String senhaHash = usuarioDAO.QueryLoginUsuario( "aluno", loginAlunoDTO.getEmail());

        if(senhaHash != null && encoder.matches(loginAlunoDTO.getSenha(), senhaHash)) {
            //Sucesso de Login
            loginAttemptService.loginSucesso(email);

            if (twoFactorEnabled) {
                String codigo = twoFactorService.gerarCodigo(email);
                emailService.enviarCodigo(email, codigo);

                session.setAttribute("email2FA", email);
                session.setAttribute("redirect", "Login");
                session.setAttribute("tipoUsuario", "aluno");

                return "redirect:/verificarAluno";
            }

            session.setAttribute("email2FA", email);
            session.setAttribute("redirect", "Login");
            session.setAttribute("tipoUsuario", "aluno");
            session.setAttribute("usuarioLogado", usuarioDAO.buscarPorEmailAluno(email));
            session.setMaxInactiveInterval(900);

            return "redirect:/home";
        }

        //Erro de Login
        loginAttemptService.loginFalhou(email);
        logger.warn("Credenciais inválidas para aluno {}. Tentativas: {}", email, loginAttemptService.getTentativas(email));

        if (loginAttemptService.getTentativas(email) >= 5) {
            logger.warn("Conta bloqueada por excesso de tentativas para aluno {}", email);
            model.addAttribute("mensagemDeErro", "Conta bloqueada por 10 minutos.");
        } else {
            model.addAttribute("mensagemDeErro", "Email ou senha inválidos.");
        }
        return "Usuarios/Aluno/loginAluno";
    }
}
