package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.redefinirSenhaDTO;
import com.example.CampusLink.service.LoginAttemptService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
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
import java.sql.SQLException;

@Controller
public class ResetPaswordController {

    private static final Logger logger = LoggerFactory.getLogger(ResetPaswordController.class);

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    @Autowired
    private usuarioDAO usuarioDAO;

    @GetMapping("/ResetPassword_Verification")
    public String ResetPasword_Verification() {
        return "Geral/ResetPassword_Verification";
    }

    @PostMapping("/ResetVerification")
    public String ResetVerification(@Valid @ModelAttribute("usuario") redefinirSenhaDTO usuarioDTO, BindingResult result, Model model, HttpSession session){

        String email = usuarioDTO.getEmail();

        session.setAttribute("email2FA", email);
        session.setAttribute("redirect", "ResetPassword");

        //2FA

        //Gerar codigo 2FA
        String codigo = twoFactorService.gerarCodigo(email);

        //Enviar codigo 2FA
        emailService.enviarCodigo(email, codigo);

        logger.info("Solicitação de reset de senha para o email: {}", email);

        return "redirect:/verificar";
    }

    @GetMapping("/ResetPassword")
    public String ResetPasword(HttpSession session){

        if(session.getAttribute("email2FA") == null){
            return "redirect:/ResetPassword_Verification";
        }

        return "Geral/ResetPassword";
    }

    @PostMapping("/reset")
    public String Reset(@Valid @ModelAttribute("usuario") redefinirSenhaDTO usuarioDTO, BindingResult result, Model model, HttpSession session) throws SQLException {


        String email = (String) session.getAttribute("email2FA");
        String senha = usuarioDTO.getSenha();

        if (!usuarioDTO.getSenha().equals(usuarioDTO.getConfirmarSenha())) {
            model.addAttribute("mensagemDeErro", "As senhas não coincidem");
            return "Geral/ResetPassword";
        }

        //Encriptador
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        usuarioDAO.UpdateSenhaUsuario(encoder.encode(senha), email);

        logger.info("A senha foi resetada para o email: {}", email);

        return "redirect:/login";
    }
}
