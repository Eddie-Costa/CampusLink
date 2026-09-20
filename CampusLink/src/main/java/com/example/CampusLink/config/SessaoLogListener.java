package com.example.CampusLink.config;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class SessaoLogListener implements HttpSessionListener {

    private static final Logger logger = LoggerFactory.getLogger(SessaoLogListener.class);
    private final usuarioDAO usuarioDAO;

    public SessaoLogListener(usuarioDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        UUID sessaoLogId = (UUID) session.getAttribute("sessaoLogId");
        if (sessaoLogId == null) {
            return;
        }

        // O listener tambem executa por timeout, sem uma requisicao ou MDC ativo.
        Object usuario = session.getAttribute("usuarioLogado");
        String email = null;
        if (usuario instanceof loginAlunoDTO aluno) {
            email = aluno.getEmail();
        } else if (usuario instanceof loginProfessorDTO professor) {
            email = professor.getEmail();
        }

        try {
            usuarioDAO.finalizarSessaoLog(sessaoLogId,
                    Instant.ofEpochMilli(session.getCreationTime()), Instant.now(), email);
        } catch (Exception e) {
            // Uma falha no banco nao deve impedir que a sessao seja invalidada.
            logger.error("Erro ao consolidar historico da sessao. sessaoLogId={}", sessaoLogId, e);
        }
    }
}
