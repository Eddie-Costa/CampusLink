// verificacao do bloqueio por termos confere redirecionamento ao aceite e as paginas liberadas
package com.example.CampusLink.config;

import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.TermosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes do bloqueio por termos esta classe simula alunos com e sem aceite da versao vigente
class TermosInterceptorTests {

    private ChamadoAcessoService acesso;
    private TermosService termos;
    private TermosInterceptor interceptor;
    private MockHttpSession session;

    // preparar testes dos termos este metodo cria a sessao do aluno e os servicos simulados
    @BeforeEach
    void preparar() {
        acesso = mock(ChamadoAcessoService.class);
        termos = mock(TermosService.class);
        interceptor = new TermosInterceptor(acesso, termos);

        session = new MockHttpSession();
        session.setAttribute("usuarioLogado", new Object());
        session.setAttribute("tipoUsuario", "aluno");

        DadosUsuarioDTO usuario = new DadosUsuarioDTO();
        usuario.setId(7L);
        when(acesso.usuarioDaSessao(any())).thenReturn(usuario);
    }

    // testar aluno sem aceite este teste confere o redirecionamento para a tela de aceite
    @Test
    void redirecionaParaAceiteQuandoVersaoAtualNaoFoiAceita() throws Exception {
        when(termos.aceitouVersaoAtual(7L)).thenReturn(false);
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(requisicao("/turmas"), resposta, null));
        assertEquals("/termos/aceite", resposta.getRedirectedUrl());
    }

    // testar aluno com aceite este teste confere a liberacao e o registro na sessao
    @Test
    void liberaEGuardaNaSessaoQuandoJaAceitou() throws Exception {
        when(termos.aceitouVersaoAtual(7L)).thenReturn(true);

        assertTrue(interceptor.preHandle(requisicao("/turmas"), new MockHttpServletResponse(), null));
        assertEquals(true, session.getAttribute(TermosInterceptor.SESSAO_TERMOS_ACEITOS));
    }

    // testar paginas liberadas este teste confere que termos perfil e sair abrem sem consultar o banco
    @Test
    void liberaTelaDeAceitePerfilESairSemConsultarOBanco() throws Exception {
        for (String caminho : new String[]{"/termos/aceite", "/profile", "/profile/exportar", "/logout", "/css/campus-theme.css"}) {
            assertTrue(interceptor.preHandle(requisicao(caminho), new MockHttpServletResponse(), null), caminho);
        }
        verify(termos, never()).aceitouVersaoAtual(any());
    }

    // testar paginas publicas este teste confere que quem tem termos pendentes navega como visitante
    @Test
    void liberaPaginasPublicasEMarcaPendenciaQuandoNaoAceitou() throws Exception {
        when(termos.aceitouVersaoAtual(7L)).thenReturn(false);

        for (String caminho : new String[]{"/", "/home", "/funcionalidades", "/sobre-nos", "/login", "/cadastrar"}) {
            assertTrue(interceptor.preHandle(requisicao(caminho), new MockHttpServletResponse(), null), caminho);
        }
        assertEquals(true, session.getAttribute(TermosInterceptor.SESSAO_TERMOS_PENDENTES));

        MockHttpServletResponse resposta = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(requisicao("/homework"), resposta, null));
        assertEquals("/termos/aceite", resposta.getRedirectedUrl());
    }

    // testar administrador este teste confere que administradores nao precisam aceitar os termos
    @Test
    void naoExigeAceiteDoAdministrador() throws Exception {
        session.setAttribute("tipoUsuario", "admin");

        assertTrue(interceptor.preHandle(requisicao("/admin/painel"), new MockHttpServletResponse(), null));
        verify(termos, never()).aceitouVersaoAtual(any());
    }

    // montar requisicao do teste este metodo cria a requisicao com a sessao do aluno
    private MockHttpServletRequest requisicao(String caminho) {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", caminho);
        requisicao.setSession(session);
        return requisicao;
    }
}
