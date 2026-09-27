package com.example.CampusLink.config;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SessaoLogListenerTests {
    @Test
    void encerraTodasAsSessoesDoTitularSemAfetarOutrasContas() {
        SessaoLogListener listener = new SessaoLogListener(mock(usuarioDAO.class));
        loginAlunoDTO aluno = new loginAlunoDTO();
        aluno.setEmail("titular@example.com");
        loginProfessorDTO professor = new loginProfessorDTO();
        professor.setEmail("outra-conta@example.com");
        MockHttpSession primeira = sessao(listener, aluno);
        MockHttpSession segunda = sessao(listener, aluno);
        MockHttpSession outra = sessao(listener, professor);
        listener.encerrarSessoesDoUsuario("TITULAR@example.com");
        assertTrue(primeira.isInvalid());
        assertTrue(segunda.isInvalid());
        assertFalse(outra.isInvalid());
    }

    private MockHttpSession sessao(SessaoLogListener listener, Object usuario) {
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", usuario);
        listener.sessionCreated(new HttpSessionEvent(sessao));
        return sessao;
    }
}
