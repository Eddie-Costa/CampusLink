package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dao.usuarioDAO;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;

@Controller
public class LogoutController {

    @Autowired
    private usuarioDAO usuarioDAO;

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

        logger.info("Sessão encerrada para o usuário {}", usuario != null ? usuario : "usuario autenticado");
        if (logger.isInfoEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "INFO", LogoutController.class.getName(), "logout", null,
                        MessageFormatter.arrayFormat("Sessão encerrada para o usuário {}", new Object[]{usuario != null ? usuario : "usuario autenticado"}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        // O evento de logout precisa existir antes da consolidacao no listener.
        session.invalidate();
        MDC.remove("aluno");
        MDC.remove("professor");
        MDC.remove("sessionId");
        return "redirect:/home";
    }
}
