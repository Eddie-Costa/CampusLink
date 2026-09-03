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
        System.out.println("[LOGIN] GET /loginProfessor - exibindo pagina de login");
        model.addAttribute("professor", new loginProfessorDTO());
        return "Usuarios/Professor/loginProfessor";
    }

    @PostMapping("/loginProfessor")
    public String fazerLogin(@Valid @ModelAttribute("professor") loginProfessorDTO loginProfessorDTO, BindingResult result, Model model, HttpSession session) throws SQLException {

        String email = loginProfessorDTO.getEmail();
        System.out.println("[LOGIN] POST /loginProfessor - tentativa recebida para email: " + email);

        //Verifica bloqueio
        if (LoginAttemptService.estaBloqueado(email)) {
            System.out.println("[LOGIN] conta bloqueada - interrompendo fluxo");
            logger.warn("Tentativa de login em Conta bloqueada");
            model.addAttribute("mensagemDeErro", "Conta bloqueada por muitas tentativas. Tente mais tarde.");
            return "Usuarios/Professor/loginProfessor";
        }

        if (result.hasErrors()) {
            System.out.println("[LOGIN] dados de login invalidos - retornando para a pagina");
            logger.warn("Erro ao fazer login");
            model.addAttribute("mensagemDeErro", "Erro ao fazer login, tente novamente!!!");
            return "Usuarios/Professor/loginProfessor";
        }

        //Encriptador
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        //Requisição para o BD Buscar a senha criptografada e comparar com a senha digitada.
        System.out.println("[LOGIN] consultando professor no banco de dados");
        String senhaHash = usuarioDAO.QueryLoginUsuario( "professor", loginProfessorDTO.getEmail());

        if(senhaHash != null && encoder.matches(loginProfessorDTO.getSenha(), senhaHash)) {
            System.out.println("[LOGIN] credenciais validas - iniciando 2FA");
            //Sucesso de Login
            loginAttemptService.loginSucesso(email);

            //2FA
            logger.info("Envio de codigo 2FA para o email: {}", email);

            //Gerar token 2FA
            String codigo = twoFactorService.gerarCodigo(email);

            //Enviar token 2FA para o email
            emailService.enviarCodigo(email, codigo);
            System.out.println("[LOGIN] codigo 2FA enviado - redirecionando para /verificarProfessor");

            session.setAttribute("email2FA", email);
            session.setAttribute("redirect", "Login");
            session.setAttribute("tipoUsuario", "professor");

            return "redirect:/verificarProfessor";
        }

        //Erro de Login
    System.out.println("[LOGIN] credenciais invalidas - registrando tentativa");
        logger.warn("Erro ao fazer login");
        loginAttemptService.loginFalhou(email);
        logger.warn("Erro ao fazer login para o professor com email:" +email+ " ,numero de tentativas: {}", loginAttemptService.getTentativas(email));

        if (loginAttemptService.getTentativas(email) >= 5) {
            System.out.println("[LOGIN] limite de tentativas atingido - conta bloqueada");
            logger.warn("Conta com email:" +email+ " bloqueada por 10 minutos");
            model.addAttribute("mensagemDeErro", "Conta bloqueada por 10 minutos.");
        } else {
            model.addAttribute("mensagemDeErro", "Email ou senha inválidos.");
        }
        return "Usuarios/Professor/loginProfessor";
    }
}
