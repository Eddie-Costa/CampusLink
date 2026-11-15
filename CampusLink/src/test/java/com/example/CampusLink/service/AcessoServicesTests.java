// verificacao dos controles de acesso confere bloqueio por tentativas codigo de verificacao usuario da sessao e revisao de conteudo
package com.example.CampusLink.service;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.repository.ChamadoRepository;
import com.example.CampusLink.repository.RevisaoConteudoRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes dos controles de acesso esta classe limpa os mapas em memoria ao final de cada teste
class AcessoServicesTests {

    private static final String EMAIL = "acesso@teste.com";

    // limpar estado em memoria este metodo remove tentativas e codigos do email usado nos testes
    @AfterEach
    void limpar() {
        new LoginAttemptService().loginSucesso(EMAIL);
        new TwoFactorService().limparCodigo(EMAIL);
    }

    // testar bloqueio por tentativas este teste confere o bloqueio na quinta senha errada e a liberacao apos login certo
    @Test
    void bloqueiaNaQuintaTentativaELiberaAposSucesso() {
        // preparar quatro tentativas erradas
        LoginAttemptService tentativas = new LoginAttemptService();
        for (int i = 0; i < 4; i++) {
            tentativas.loginFalhou(EMAIL);
        }
        boolean bloqueadoAntes = LoginAttemptService.estaBloqueado(EMAIL);

        // executar quinta tentativa errada e depois login correto
        tentativas.loginFalhou(EMAIL);
        boolean bloqueadoDepois = LoginAttemptService.estaBloqueado(EMAIL);
        int contagem = tentativas.getTentativas(EMAIL);
        tentativas.loginSucesso(EMAIL);

        // verificar bloqueio e liberacao
        assertFalse(bloqueadoAntes);
        assertTrue(bloqueadoDepois);
        assertEquals(5, contagem);
        assertFalse(LoginAttemptService.estaBloqueado(EMAIL));
        assertEquals(0, tentativas.getTentativas(EMAIL));
    }

    // testar fim do bloqueio este teste confere que o bloqueio vencido e removido junto com as tentativas
    @Test
    @SuppressWarnings("unchecked")
    void removeBloqueioVencido() {
        // preparar bloqueio que ja terminou
        LoginAttemptService tentativas = new LoginAttemptService();
        tentativas.loginFalhou(EMAIL);
        ((Map<String, Long>) ReflectionTestUtils.getField(LoginAttemptService.class, "bloqueados")).put(EMAIL, System.currentTimeMillis() - 1);

        // executar conferencia
        boolean bloqueado = LoginAttemptService.estaBloqueado(EMAIL);

        // verificar liberacao e contagem zerada
        assertFalse(bloqueado);
        assertEquals(0, tentativas.getTentativas(EMAIL));
    }

    // testar tempos do codigo este teste confere espera para reenvio validade e invalidacao do codigo
    @Test
    @SuppressWarnings("unchecked")
    void controlaTemposDoCodigoDeVerificacao() {
        // preparar codigo recem gerado
        TwoFactorService doisFatores = new TwoFactorService();
        String codigo = TwoFactorService.gerarCodigo(EMAIL);

        // executar consultas de tempo e invalidacao
        long reenvio = doisFatores.segundosParaReenvio(EMAIL);
        long validade = doisFatores.segundosParaExpirarCodigo(EMAIL);
        long expiraEm = doisFatores.obterExpiracaoCodigo(EMAIL);
        ((Map<String, Long>) ReflectionTestUtils.getField(TwoFactorService.class, "proximoReenvio")).put(EMAIL, 0L);
        String novo = doisFatores.reenviarCodigo(EMAIL);
        doisFatores.invalidarCodigo(EMAIL);

        // verificar tempos codigo novo diferente e codigo invalidado
        assertTrue(reenvio > 0 && reenvio <= 120);
        assertTrue(validade > 0 && validade <= 300);
        assertTrue(expiraEm > System.currentTimeMillis());
        assertFalse(codigo.equals(novo));
        assertFalse(doisFatores.validarCodigo(EMAIL, novo));
    }

    // testar chaves invalidas este teste confere chave vazia codigo nulo e codigo errado
    @Test
    @SuppressWarnings("unchecked")
    void recusaChavesECodigosInvalidos() {
        // preparar codigo gerado e codigo vencido
        TwoFactorService doisFatores = new TwoFactorService();
        String codigo = TwoFactorService.gerarCodigo(EMAIL);

        // executar consultas invalidas
        boolean errado = doisFatores.validarCodigo(EMAIL, "000000".equals(codigo) ? "111111" : "000000");
        boolean nulo = doisFatores.validarCodigo(EMAIL, null);
        ((Map<String, Long>) ReflectionTestUtils.getField(TwoFactorService.class, "expiracao")).put(EMAIL, System.currentTimeMillis() - 1);
        long validadeVencida = doisFatores.segundosParaExpirarCodigo(EMAIL);

        // verificar recusas
        assertFalse(errado);
        assertFalse(nulo);
        assertEquals(0, validadeVencida);
        assertEquals(0, doisFatores.segundosParaReenvio(" "));
        assertEquals(0, doisFatores.segundosParaExpirarCodigo(null));
        assertEquals(0, doisFatores.segundosParaReenvio("sem-codigo@teste.com"));
        assertThrows(IllegalArgumentException.class, () -> TwoFactorService.gerarCodigo(" "));
        assertThrows(IllegalArgumentException.class, () -> doisFatores.reenviarCodigo(null));
    }

    // testar usuario da sessao este teste confere o email usado para cada perfil e sessao sem login
    @Test
    void identificaUsuarioDaSessaoPorPerfil() {
        // preparar banco com usuario ativo para cada perfil
        ChamadoRepository repositorio = mock(ChamadoRepository.class);
        ChamadoAcessoService acesso = new ChamadoAcessoService(repositorio);
        DadosUsuarioDTO encontrado = new DadosUsuarioDTO();
        when(repositorio.buscarUsuarioAtivo(anyString(), anyString())).thenReturn(encontrado);
        loginAlunoDTO aluno = new loginAlunoDTO();
        aluno.setEmail("aluno@teste.com");
        loginProfessorDTO professor = new loginProfessorDTO();
        professor.setEmail("prof@teste.com");
        loginAdminDTO admin = new loginAdminDTO();
        admin.setEmail("admin@teste.com");

        // executar identificacoes
        DadosUsuarioDTO doAluno = acesso.usuarioDaSessao(sessao(aluno, "aluno"));
        acesso.usuarioDaSessao(sessao(professor, "professor"));
        acesso.usuarioDaSessao(sessao(admin, "admin"));
        DadosUsuarioDTO tipoTrocado = acesso.usuarioDaSessao(sessao(aluno, "admin"));
        DadosUsuarioDTO semLogin = acesso.usuarioDaSessao(new MockHttpSession());

        // verificar consultas e recusas
        assertEquals(encontrado, doAluno);
        verify(repositorio).buscarUsuarioAtivo("prof@teste.com", "professor");
        verify(repositorio).buscarUsuarioAtivo("admin@teste.com", "admin");
        assertNull(tipoTrocado);
        assertNull(semLogin);
    }

    // testar revisao do conteudo este teste confere liberacao reprovacao com motivo e as denuncias marcadas
    @Test
    void liberaOuReprovaConteudoEmAnalise() {
        // preparar conteudo 10 em analise e conteudo 11 fora da analise
        RevisaoConteudoRepository repositorio = mock(RevisaoConteudoRepository.class);
        RevisaoConteudoService revisao = new RevisaoConteudoService(repositorio);
        ReflectionTestUtils.setField(revisao, "UsuarioRepository", mock(UsuarioRepository.class));
        when(repositorio.liberarConteudo(10L)).thenReturn(1);
        when(repositorio.reprovarConteudo(10L, "Ofensivo")).thenReturn(1);

        // executar decisoes
        boolean liberou = revisao.liberarConteudo(10L);
        boolean reprovou = revisao.reprovarConteudo(10L, "  Ofensivo  ");
        boolean foraDaAnalise = revisao.liberarConteudo(11L) || revisao.reprovarConteudo(11L, "motivo");
        boolean semMotivo = revisao.reprovarConteudo(10L, " ");
        boolean semCodigo = revisao.liberarConteudo(null) || revisao.reprovarConteudo(null, "motivo");

        // verificar decisoes e denuncias marcadas apenas quando houve alteracao
        assertTrue(liberou);
        assertTrue(reprovou);
        assertFalse(foraDaAnalise);
        assertFalse(semMotivo);
        assertFalse(semCodigo);
        verify(repositorio).marcarDenunciasLiberadas(10L);
        verify(repositorio).marcarDenunciasRemovidas(10L);
        verify(repositorio, never()).marcarDenunciasLiberadas(11L);
    }

    // testar falha na revisao este teste confere as mensagens quando o banco falha na liberacao ou reprovacao
    @Test
    void trocaFalhaDaRevisaoPorMensagem() {
        // preparar banco indisponivel
        RevisaoConteudoRepository repositorio = mock(RevisaoConteudoRepository.class);
        RevisaoConteudoService revisao = new RevisaoConteudoService(repositorio);
        ReflectionTestUtils.setField(revisao, "UsuarioRepository", mock(UsuarioRepository.class));
        when(repositorio.liberarConteudo(anyLong())).thenThrow(new DataRetrievalFailureException("banco"));
        when(repositorio.reprovarConteudo(anyLong(), anyString())).thenThrow(new DataRetrievalFailureException("banco"));

        // executar decisoes
        RuntimeException liberar = assertThrows(RuntimeException.class, () -> revisao.liberarConteudo(10L));
        RuntimeException reprovar = assertThrows(RuntimeException.class, () -> revisao.reprovarConteudo(10L, "motivo"));

        // verificar mensagens
        assertTrue(liberar.getMessage().contains("liberar"));
        assertTrue(reprovar.getMessage().contains("reprovar"));
    }

    // montar sessao este metodo cria a sessao com o usuario e o perfil informados
    private MockHttpSession sessao(Object usuario, String tipo) {
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", usuario);
        sessao.setAttribute("tipoUsuario", tipo);
        return sessao;
    }
}
