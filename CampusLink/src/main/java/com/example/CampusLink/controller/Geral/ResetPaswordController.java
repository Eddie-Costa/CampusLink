// recuperacao de acesso organiza a troca de senha por email
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.redefinirSenhaDTO;
import com.example.CampusLink.service.LoginAttemptService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import java.sql.SQLException;

// controle de nova senha esta classe solicita verificacao e grava a senha confirmada
@Controller
public class ResetPaswordController {

    public static final String SESSAO_RESET_LIBERADO = "resetSenhaLiberadoPara";

    private static final Logger logger = LoggerFactory.getLogger(ResetPaswordController.class);

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    @Autowired
    private UsuarioRepository UsuarioRepository;

    // abrir recuperacao de senha este metodo mostra o formulario para informar o email
    @GetMapping("/ResetPassword_Verification")
    public String ResetPasword_Verification() {
        return "Geral/ResetPassword_Verification";
    }

    // enviar codigo de recuperacao este metodo gera o codigo e envia ao email informado
    @PostMapping("/ResetVerification")
    public String ResetVerification(@Valid @ModelAttribute("usuario") redefinirSenhaDTO usuarioDTO, BindingResult result, Model model, HttpSession session){

        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/profile";
        }
        if (result.hasFieldErrors("email")) {
            model.addAttribute("mensagemDeErro", result.getFieldError("email").getDefaultMessage());
            return "Geral/ResetPassword_Verification";
        }

        String email = usuarioDTO.getEmail().trim().toLowerCase();

        session.removeAttribute(SESSAO_RESET_LIBERADO);
        session.setAttribute("email2FA", email);
        session.setAttribute("redirect", "ResetPassword");




        String codigo = twoFactorService.gerarCodigo(email);


        emailService.enviarCodigo(email, codigo, FinalidadeCodigoEmail.REDEFINICAO_SENHA);

        logger.info("Solicitação de reset de senha para o email: {}", email);
        // registro de informacoes confere se esta habilitado ao enviar codigo de recuperacao
        if (logger.isInfoEnabled()) {
            // gravar historico ao enviar codigo de recuperacao tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", ResetPaswordController.class.getName(), "ResetVerification", null,
                        MessageFormatter.arrayFormat("Solicitação de reset de senha para o email: {}", new Object[]{email}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        return "redirect:/verificar";
    }

    // abrir nova senha este metodo mostra a troca de senha quando existe email na sessao
    @GetMapping("/ResetPassword")
    public String ResetPasword(HttpSession session){

        // exigir verificacao da recuperacao retorna a etapa de email quando o codigo nao foi validado para este email
        if (resetLiberado(session) == null) {
            return "redirect:/ResetPassword_Verification";
        }

        return "Geral/ResetPassword";
    }

    // redefinir senha este metodo compara a confirmacao e grava a senha protegida no banco
    @PostMapping("/reset")
    public String Reset(@Valid @ModelAttribute("usuario") redefinirSenhaDTO usuarioDTO, BindingResult result, Model model, HttpSession session) throws SQLException {


        String email = resetLiberado(session);
        if (email == null) {
            return "redirect:/ResetPassword_Verification";
        }
        String senha = usuarioDTO.getSenha();

        // exigir senha forte reapresenta o formulario quando a senha nao atende as regras
        if (result.hasFieldErrors("senha") || senha == null) {
            model.addAttribute("mensagemDeErro", result.hasFieldErrors("senha")
                    ? result.getFieldError("senha").getDefaultMessage() : "Informe a nova senha.");
            return "Geral/ResetPassword";
        }

        // conferir nova senha reapresenta o formulario quando senha e confirmacao sao diferentes
        if (!senha.equals(usuarioDTO.getConfirmarSenha())) {
            model.addAttribute("mensagemDeErro", "As senhas não coincidem");
            return "Geral/ResetPassword";
        }


        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        UsuarioRepository.UpdateSenhaUsuario(encoder.encode(senha), email);
        session.removeAttribute(SESSAO_RESET_LIBERADO);
        session.removeAttribute("email2FA");
        session.removeAttribute("tipoUsuario");

        logger.info("A senha foi resetada para o email: {}", email);
        // registro de informacoes confere se esta habilitado ao redefinir senha
        if (logger.isInfoEnabled()) {
            // gravar historico ao redefinir senha tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", ResetPaswordController.class.getName(), "Reset", null,
                        MessageFormatter.arrayFormat("A senha foi resetada para o email: {}", new Object[]{email}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        return "redirect:/login";
    }

    private String resetLiberado(HttpSession session) {
        Object liberadoPara = session.getAttribute(SESSAO_RESET_LIBERADO);
        Object email = session.getAttribute("email2FA");
        if (liberadoPara instanceof String emailLiberado && emailLiberado.equals(email)) {
            return emailLiberado;
        }
        return null;
    }
}
