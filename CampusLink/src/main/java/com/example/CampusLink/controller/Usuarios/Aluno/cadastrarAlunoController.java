package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.dto.Aluno.cadastrarAlunoDTO;
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
public class cadastrarAlunoController {

    private static final Logger logger =
            LoggerFactory.getLogger(cadastrarAlunoController.class);

    private final TwoFactorService twoFactorService;
    private final emailService emailService;

    public cadastrarAlunoController(TwoFactorService twoFactorService, emailService emailService) {
        this.twoFactorService = twoFactorService;
        this.emailService = emailService;
    }

    @GetMapping("/cadastrarAluno")
    public String CadastrarAluno(Model model) {
        model.addAttribute("aluno", new cadastrarAlunoDTO());
        return "Usuarios/Aluno/cadastrarAluno";
    }

    @PostMapping("/cadastrarAluno")
    public String registrar(
            @Valid @ModelAttribute("aluno")
            cadastrarAlunoDTO aluno,
            BindingResult result,
            Model model,
            HttpSession session
    ) {

        synchronized (session) {

            // limpa uma tentativa antiga
            String chaveAnterior = (String) session.getAttribute("chave2FACadastro");

            if (chaveAnterior != null) {twoFactorService.limparCodigo(chaveAnterior);
            }

            session.removeAttribute("cadastroPendente");
            session.removeAttribute("chave2FACadastro");
            session.removeAttribute("expiracaoCadastro2FA");
            session.removeAttribute("tentativasCadastro2FA");
            if (result.hasErrors() || aluno.getSenha() == null || aluno.getSenha().isBlank()) {
                aluno.setSenha(null);
                model.addAttribute("mensagemDeErro", "dados invalidos verifique o formulario " + "e informe a senha novamente");
                return "Usuarios/Aluno/cadastrarAluno";
            }

            String chaveCadastro = "cadastro:" + UUID.randomUUID();

            try {
                String emailNormalizado = aluno.getEmail().trim().toLowerCase(Locale.ROOT);
                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
                String senhaHash = encoder.encode(aluno.getSenha());
                aluno.setEmail(emailNormalizado);
                aluno.setSenha(senhaHash);
                long expiracao = System.currentTimeMillis() + (5 * 60 * 1000);
                String codigo = TwoFactorService.gerarCodigo(chaveCadastro);
                emailService.enviarCodigo(emailNormalizado, codigo);

                // salva os dados temporarios na sessao
                session.setAttribute("cadastroPendente", aluno);
                session.setAttribute("chave2FACadastro", chaveCadastro);
                session.setAttribute("expiracaoCadastro2FA", expiracao);
                session.setAttribute("tentativasCadastro2FA", 0);
                return "redirect:/verificarCadastro";

            } catch (RuntimeException e) {
                twoFactorService.limparCodigo(chaveCadastro);
                aluno.setSenha(null);
                logger.error("erro ao iniciar a verificacao do cadastro de aluno", e);
                model.addAttribute("mensagemDeErro", "nao foi possivel iniciar a verificacao " + "confira os dados e tente novamente");
                return "Usuarios/Aluno/cadastrarAluno";
            }
        }
    }
}