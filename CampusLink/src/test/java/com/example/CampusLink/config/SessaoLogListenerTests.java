// verificacao de sessoes exercita a desconexao por titular
package com.example.CampusLink.config;

import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

// testes de encerramento esta classe confere que outras contas continuam conectadas
class SessaoLogListenerTests {
    // testar encerramento das sessoes este teste confere que apenas as sessoes do titular sao encerradas
    @Test
    void encerraTodasAsSessoesDoTitularSemAfetarOutrasContas() {
        SessaoLogListener listener = new SessaoLogListener(mock(UsuarioRepository.class));
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

    // preparar sessao de teste este metodo registra uma sessao simulada com o usuario informado
    private MockHttpSession sessao(SessaoLogListener listener, Object usuario) {
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", usuario);
        listener.sessionCreated(new HttpSessionEvent(sessao));
        return sessao;
    }
}
