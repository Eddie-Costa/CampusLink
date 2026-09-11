package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LogoutController {

    private static final Logger logger = LoggerFactory.getLogger(LogoutController.class);

    @GetMapping("/logout")
    public String logout(HttpSession session) {

        Object usuarioLogado = session.getAttribute("usuarioLogado");

        if (usuarioLogado == null) {

            return "redirect:/login";
        }

        Object tipoSalvo = session.getAttribute("tipoUsuario");

        String tipoUsuario = tipoSalvo instanceof String ? (String) tipoSalvo : null;

        // tenta encontrar o tipo pelo usuario salvo
        if (tipoUsuario == null) {

            if (usuarioLogado instanceof loginAlunoDTO) {

                tipoUsuario = "aluno";

            } else if (usuarioLogado instanceof loginProfessorDTO) {

                tipoUsuario = "professor";
            }
        }

        String usuario = null;

        if ("aluno".equals(tipoUsuario)) {

            usuario = MDC.get("aluno");

        } else if ("professor".equals(tipoUsuario)) {

            usuario = MDC.get("professor");
        }

        String sessao = MDC.get("sessionId");

        logger.info("sessao encerrada para o usuario {}", usuario != null ? usuario : "usuario autenticado");
        logger.info("sessao de id {} invalidada", sessao != null ? sessao : session.getId());

        // remove os dados usados pelos logs
        MDC.remove("aluno");
        MDC.remove("professor");
        MDC.remove("sessionId");
        session.invalidate();
        return "redirect:/home";
    }
}