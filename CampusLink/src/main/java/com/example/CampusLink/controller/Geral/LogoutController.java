package com.example.CampusLink.controller.Geral;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Controller
public class LogoutController {

    private static final Logger logger = LoggerFactory.getLogger(LogoutController.class);

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        if(session.getAttribute("usuarioLogado") == null){
            return "redirect:/login";
        }

        String usuario = "";
        String sessao = "";
        String remover = "";

        if(session.getAttribute("tipoUsuario").equals("aluno")){
            usuario = MDC.get("aluno");
            sessao = MDC.get("sessionId");
            remover = "aluno";

        } else if(session.getAttribute("tipoUsuario").equals("professor")){
            usuario = MDC.get("professor");
            sessao = MDC.get("sessionId");
            remover = "professor";
        }

        logger.info("Sessão encerrada para o usuário: {}", usuario);
        logger.info("Sessão de ID: {} invalidada", sessao);

        MDC.remove(remover);
        MDC.remove("sessionId");
        session.invalidate();

        return "redirect:/home";
    }
}
