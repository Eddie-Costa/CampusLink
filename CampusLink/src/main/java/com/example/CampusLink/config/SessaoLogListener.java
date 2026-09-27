package com.example.CampusLink.config;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessaoLogListener implements HttpSessionListener {

    private static final Logger logger = LoggerFactory.getLogger(SessaoLogListener.class);
    private final usuarioDAO usuarioDAO;
    private final Map<String, HttpSession> sessoes = new ConcurrentHashMap<>();

    public SessaoLogListener(usuarioDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        sessoes.put(event.getSession().getId(), event.getSession());
    }

    public void encerrarSessoesDoUsuario(String email) {
        for (HttpSession session : sessoes.values()) {
            try {
                Object usuario = session.getAttribute("usuarioLogado");
                String emailSessao = null;

                if (usuario instanceof loginAlunoDTO aluno) {
                    emailSessao = aluno.getEmail();
                } else if (usuario instanceof loginProfessorDTO professor) {
                    emailSessao = professor.getEmail();
                } else if (usuario instanceof loginAdminDTO admin) {
                    emailSessao = admin.getEmail();
                }

                if (email.equalsIgnoreCase(emailSessao)) {
                    session.invalidate();
                }
            } catch (IllegalStateException ignored) {
                // Uma sessão pode expirar enquanto as demais são encerradas.
            }
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        sessoes.values().remove(session);
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
        } else if (usuario instanceof loginAdminDTO admin) {
            email = admin.getEmail();
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
