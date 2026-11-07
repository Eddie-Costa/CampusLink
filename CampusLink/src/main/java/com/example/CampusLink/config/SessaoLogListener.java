// ciclo das sessoes acompanha a abertura e o encerramento dos acessos
package com.example.CampusLink.config;

import com.example.CampusLink.repository.UsuarioRepository;
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

// controle de sessoes esta classe guarda acessos ativos e consolida o historico ao desconectar
@Component
public class SessaoLogListener implements HttpSessionListener {

    private static final Logger logger = LoggerFactory.getLogger(SessaoLogListener.class);
    private final UsuarioRepository UsuarioRepository;
    private final Map<String, HttpSession> sessoes = new ConcurrentHashMap<>();

    // preparar registro de sessoes este construtor recebe o acesso ao historico de usuarios
    public SessaoLogListener(UsuarioRepository UsuarioRepository) {
        this.UsuarioRepository = UsuarioRepository;
    }

    // acompanhar nova sessao este metodo guarda a sessao criada para permitir seu encerramento
    @Override
    public void sessionCreated(HttpSessionEvent event) {
        sessoes.put(event.getSession().getId(), event.getSession());
    }

    // encerrar sessoes do usuario este metodo desconecta todas as sessoes do email informado
    public void encerrarSessoesDoUsuario(String email) {
        for (HttpSession session : sessoes.values()) {
            // encerrar sessoes correspondentes identifica o usuario e ignora sessoes que ja foram invalidadas
            try {
                Object usuario = session.getAttribute("usuarioLogado");
                String emailSessao = null;

                // identificar aluno da sessao usa o email do aluno para comparar as contas
                if (usuario instanceof loginAlunoDTO aluno) {
                    emailSessao = aluno.getEmail();
                }
                // identificar professor da sessao usa o email do professor para comparar as contas
                else if (usuario instanceof loginProfessorDTO professor) {
                    emailSessao = professor.getEmail();
                }
                // identificar administrador da sessao usa o email do administrador para comparar as contas
                else if (usuario instanceof loginAdminDTO admin) {
                    emailSessao = admin.getEmail();
                }

                // encerrar sessao do email invalida a sessao quando ela pertence a conta informada
                if (email.equalsIgnoreCase(emailSessao)) {
                    session.invalidate();
                }
            } catch (IllegalStateException ignored) {

            }
        }
    }

    // finalizar historico da sessao este metodo remove a sessao e grava seu periodo de uso
    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        sessoes.values().remove(session);
        UUID sessaoLogId = (UUID) session.getAttribute("sessaoLogId");
        // ignorar sessao sem codigo encerra o registro quando falta a identificacao do historico
        if (sessaoLogId == null) {
            return;
        }


        Object usuario = session.getAttribute("usuarioLogado");
        String email = null;
        // ler aluno ao encerrar recupera o email do aluno para finalizar o historico
        if (usuario instanceof loginAlunoDTO aluno) {
            email = aluno.getEmail();
        }
        // ler professor ao encerrar recupera o email do professor para finalizar o historico
        else if (usuario instanceof loginProfessorDTO professor) {
            email = professor.getEmail();
        }
        // ler administrador ao encerrar recupera o email do administrador para finalizar o historico
        else if (usuario instanceof loginAdminDTO admin) {
            email = admin.getEmail();
        }

        // finalizar historico da sessao grava inicio fim e usuario e registra possiveis falhas
        try {
            UsuarioRepository.finalizarSessaoLog(sessaoLogId,
                    Instant.ofEpochMilli(session.getCreationTime()), Instant.now(), email);
        } catch (Exception e) {

            logger.error("Erro ao consolidar historico da sessao. sessaoLogId={}", sessaoLogId, e);
        }
    }
}
