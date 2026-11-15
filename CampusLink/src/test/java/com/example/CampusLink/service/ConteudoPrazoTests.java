// verificacao do prazo dos conteudos confere o bloqueio da conclusao depois que o evento comeca
package com.example.CampusLink.service;

import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes do prazo dos conteudos esta classe simula conteudos com prazo encerrado e de outras turmas
class ConteudoPrazoTests {

    private static final Long ALUNO = 7L;
    private static final Long TURMA = 1L;
    private static final Long CONTEUDO = 10L;

    private ConteudoRepository repositorio;
    private ConteudoService conteudos;

    // preparar testes do prazo este metodo cria o servico com o banco simulado e o conteudo pertencendo a turma
    @BeforeEach
    void preparar() throws Exception {
        repositorio = mock(ConteudoRepository.class);
        conteudos = new ConteudoService(repositorio, mock(ArquivoService.class));
        ReflectionTestUtils.setField(conteudos, "UsuarioRepository", mock(UsuarioRepository.class));
        when(repositorio.conteudoPertenceTurma(CONTEUDO, TURMA)).thenReturn(true);
    }

    // testar conclusao apos o prazo este teste confere que o aluno nao conclui conteudo cujo evento ja comecou
    @Test
    void bloqueiaConclusaoDepoisDoPrazoDoEvento() throws Exception {
        // preparar prazo encerrado no dia 05/11
        when(repositorio.listarPrazosEncerrados(anyList(), any(LocalDateTime.class)))
                .thenReturn(Map.of(CONTEUDO, LocalDateTime.of(2026, 11, 5, 19, 0)));

        // executar tentativa de concluir o conteudo
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> conteudos.alterarConclusao(ALUNO, TURMA, CONTEUDO, true));

        // verificar mensagem com a data do prazo e nenhuma conclusao gravada
        assertTrue(erro.getMessage().contains("terminou em 05/11"), erro.getMessage());
        verify(repositorio, never()).marcarConcluido(anyLong(), anyLong());
    }

    // testar desfazer conclusao apos o prazo este teste confere que o aluno ainda pode desmarcar um conteudo vencido
    @Test
    void permiteDesfazerConclusaoMesmoDepoisDoPrazo() throws Exception {
        // preparar prazo encerrado para o conteudo
        when(repositorio.listarPrazosEncerrados(anyList(), any(LocalDateTime.class)))
                .thenReturn(Map.of(CONTEUDO, LocalDateTime.of(2026, 11, 5, 19, 0)));

        // executar pedido para desmarcar o conteudo
        conteudos.alterarConclusao(ALUNO, TURMA, CONTEUDO, false);

        // verificar que a conclusao foi desfeita sem consultar o prazo
        verify(repositorio).desmarcarConcluido(ALUNO, CONTEUDO);
        verify(repositorio, never()).listarPrazosEncerrados(anyList(), any(LocalDateTime.class));
    }

    // testar conteudo de outra turma este teste confere que nao se conclui conteudo usando o id de uma turma diferente
    @Test
    void recusaConteudoQueNaoPertenceATurmaInformada() throws Exception {
        // preparar conteudo que nao pertence a turma 2
        when(repositorio.conteudoPertenceTurma(CONTEUDO, 2L)).thenReturn(false);

        // executar tentativa de concluir pela turma errada
        assertThrows(IllegalArgumentException.class, () -> conteudos.alterarConclusao(ALUNO, 2L, CONTEUDO, true));

        // verificar que nada foi gravado
        verify(repositorio, never()).marcarConcluido(anyLong(), anyLong());
        verify(repositorio, never()).desmarcarConcluido(anyLong(), anyLong());
    }
}
