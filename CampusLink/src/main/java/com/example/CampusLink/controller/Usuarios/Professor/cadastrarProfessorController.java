package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.dto.Professor.cadastrarProfessorDTO;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Locale;
import java.util.UUID;

@Controller
public class cadastrarProfessorController {

    private static final Logger logger = LoggerFactory.getLogger(cadastrarProfessorController.class);
    private final TwoFactorService twoFactorService;
    private final emailService emailService;

    public cadastrarProfessorController(TwoFactorService twoFactorService, emailService emailService) {
        this.twoFactorService = twoFactorService;
        this.emailService = emailService;
    }

    @GetMapping("/cadastrarProfessor")
    public String CadastrarProfessor(Model model) {
        model.addAttribute("professor", new cadastrarProfessorDTO());
        return "Usuarios/Professor/cadastrarProfessor";
    }

    @PostMapping("/cadastrarProfessor")
    public String registrar(
            @Valid @ModelAttribute("professor")
            cadastrarProfessorDTO professor,
            BindingResult result,
            Model model,
            HttpSession session
    ) {

        synchronized (session) {

            // limpa uma tentativa antiga
            String chaveAnterior = (String) session.getAttribute("chave2FACadastro");

            if (chaveAnterior != null) {twoFactorService.limparCodigo(chaveAnterior);}
            session.removeAttribute("cadastroPendente");
            session.removeAttribute("chave2FACadastro");
            session.removeAttribute("expiracaoCadastro2FA");
            session.removeAttribute("tentativasCadastro2FA");

            if (result.hasErrors() || professor.getSenha() == null || professor.getSenha().isBlank()) {
                professor.setSenha(null);
                model.addAttribute("mensagemDeErro", "dados invalidos verifique o formulario " + "e informe a senha novamente");
                return "Usuarios/Professor/cadastrarProfessor";
            }

            String chaveCadastro = "cadastro:" + UUID.randomUUID();

            try {
                String emailNormalizado = professor.getEmail().trim().toLowerCase(Locale.ROOT);
                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
                String senhaHash = encoder.encode(professor.getSenha());
                professor.setEmail(emailNormalizado);
                professor.setSenha(senhaHash);

                long expiracao = System.currentTimeMillis() + (5 * 60 * 1000);
                String codigo = TwoFactorService.gerarCodigo(chaveCadastro);
                emailService.enviarCodigo(emailNormalizado, codigo);

                // salva os dados temporarios na sessao
                session.setAttribute("cadastroPendente", professor);
                session.setAttribute("chave2FACadastro", chaveCadastro);
                session.setAttribute("expiracaoCadastro2FA", expiracao);
                session.setAttribute("tentativasCadastro2FA", 0);
                return "redirect:/verificarCadastro";
            } catch (RuntimeException e) {

                twoFactorService.limparCodigo(chaveCadastro);
                professor.setSenha(null);
                logger.error("erro ao iniciar a verificacao do cadastro de professor", e);
                model.addAttribute("mensagemDeErro", "nao foi possivel iniciar a verificacao " + "confira os dados e tente novamente");
                return "Usuarios/Professor/cadastrarProfessor";
            }
        }
    }
}