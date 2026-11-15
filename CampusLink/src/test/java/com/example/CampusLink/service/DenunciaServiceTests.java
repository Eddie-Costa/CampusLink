// verificacao das denuncias confere as recusas do registro e a suspensao automatica pelo limite da turma
package com.example.CampusLink.service;

import com.example.CampusLink.dto.DenunciaDTO;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.DenunciaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes das denuncias esta classe simula um conteudo ativo de uma turma com alunos
class DenunciaServiceTests {

    private static final Long CONTEUDO = 10L;
    private static final Long ALUNO = 7L;
    private static final Long TURMA = 1L;

    private DenunciaRepository denuncias;
    private ConteudoRepository conteudos;
    private DenunciaService servico;

    // preparar testes das denuncias este metodo cria um conteudo ativo do professor 4 em turma de 10 alunos
    @BeforeEach
    void preparar() throws SQLException {
        denuncias = mock(DenunciaRepository.class);
        conteudos = mock(ConteudoRepository.class);
        servico = new DenunciaService(denuncias, conteudos);
        ReflectionTestUtils.setField(servico, "UsuarioRepository", mock(UsuarioRepository.class));
        when(conteudos.buscarStatus(CONTEUDO)).thenReturn("ativo");
        when(conteudos.buscarIdProfessor(CONTEUDO)).thenReturn(4L);
        when(denuncias.contarAlunosDaTurma(TURMA)).thenReturn(10);
    }

    // testar limite de suspensao este teste confere que o conteudo e suspenso ao atingir 15 por cento dos alunos com minimo de dois
    @ParameterizedTest
    @CsvSource({"10,1,sucesso", "10,2,suspenso", "40,5,sucesso", "40,6,suspenso", "3,1,sucesso", "3,2,suspenso"})
    void suspendeConteudoAoAtingirLimiteDaTurma(int alunos, int denunciasPendentes, String esperado) throws SQLException {
        // preparar turma e denuncias pendentes
        when(denuncias.contarAlunosDaTurma(TURMA)).thenReturn(alunos);
        when(denuncias.contarDenunciasPendentes(CONTEUDO)).thenReturn(denunciasPendentes);

        // executar denuncia
        String resultado = servico.registrarDenuncia(CONTEUDO, ALUNO, TURMA, "Conteudo ofensivo");

        // verificar resultado e suspensao somente ao atingir o limite
        assertEquals(esperado, resultado);
        verify(conteudos, "suspenso".equals(esperado) ? org.mockito.Mockito.times(1) : never())
                .atualizarStatus(CONTEUDO, "suspenso_denuncia");
    }

    // testar registro da denuncia este teste confere autor do conteudo e motivo sem espacos
    @Test
    void registraDenunciaComProfessorEMotivoLimpo() throws SQLException {
        // preparar uma denuncia pendente
        when(denuncias.contarDenunciasPendentes(CONTEUDO)).thenReturn(1);

        // executar denuncia
        servico.registrarDenuncia(CONTEUDO, ALUNO, TURMA, "  Link quebrado  ");

        // verificar dados gravados
        ArgumentCaptor<DenunciaDTO> denuncia = ArgumentCaptor.forClass(DenunciaDTO.class);
        verify(denuncias).inserir(denuncia.capture());
        assertEquals(4L, denuncia.getValue().getIdProfessor());
        assertEquals("Link quebrado", denuncia.getValue().getMotivo());
    }

    // testar denuncias recusadas este teste confere cada motivo de recusa sem gravar denuncia
    @Test
    void recusaDenunciasInvalidas() throws SQLException {
        // preparar respostas do banco para cada situacao invalida
        when(conteudos.buscarStatus(20L)).thenReturn(null);
        when(conteudos.buscarStatus(30L)).thenReturn("suspenso_denuncia");
        when(conteudos.buscarStatus(40L)).thenReturn("ativo");
        when(denuncias.alunoJaDenunciou(40L, ALUNO)).thenReturn(true);
        when(conteudos.buscarStatus(50L)).thenReturn("ATIVO");
        when(conteudos.buscarIdProfessor(50L)).thenReturn(null);
        when(denuncias.contarAlunosDaTurma(2L)).thenReturn(0);

        // executar denuncias invalidas e verificar cada resposta
        assertEquals("erro", servico.registrarDenuncia(null, ALUNO, TURMA, "motivo"));
        assertEquals("erro", servico.registrarDenuncia(CONTEUDO, null, TURMA, "motivo"));
        assertEquals("erro", servico.registrarDenuncia(CONTEUDO, ALUNO, null, "motivo"));
        assertEquals("motivo_vazio", servico.registrarDenuncia(CONTEUDO, ALUNO, TURMA, "  "));
        assertEquals("conteudo_nao_encontrado", servico.registrarDenuncia(20L, ALUNO, TURMA, "motivo"));
        assertEquals("conteudo_indisponivel", servico.registrarDenuncia(30L, ALUNO, TURMA, "motivo"));
        assertEquals("duplicada", servico.registrarDenuncia(40L, ALUNO, TURMA, "motivo"));
        assertEquals("professor_nao_encontrado", servico.registrarDenuncia(50L, ALUNO, TURMA, "motivo"));
        assertEquals("turma_sem_alunos", servico.registrarDenuncia(CONTEUDO, ALUNO, 2L, "motivo"));
        verify(denuncias, never()).inserir(any());
    }

    // testar consultas e falhas este teste confere os repasses ao banco e as mensagens quando o banco falha
    @Test
    void consultaDenunciasETrocaFalhasPorMensagem() throws SQLException {
        // preparar dados do conteudo 10 e falhas no conteudo 99
        when(denuncias.contarDenunciasPendentes(CONTEUDO)).thenReturn(3);
        when(denuncias.listarNomesAlunosPorConteudo(CONTEUDO)).thenReturn(List.of("Ana"));
        when(denuncias.contarDenunciasPendentes(99L)).thenThrow(new SQLException("banco"));
        when(denuncias.listarNomesAlunosPorConteudo(99L)).thenThrow(new SQLException("banco"));
        when(denuncias.listarPorConteudo(99L)).thenThrow(new SQLException("banco"));
        when(conteudos.buscarStatus(99L)).thenThrow(new SQLException("banco"));

        // executar consultas e verificar resultados e mensagens
        assertEquals(3, servico.contarDenuncias(CONTEUDO));
        assertEquals(List.of("Ana"), servico.listarNomesAlunos(CONTEUDO));
        assertTrue(servico.listarPorConteudo(CONTEUDO).isEmpty());
        assertTrue(assertThrows(RuntimeException.class, () -> servico.contarDenuncias(99L)).getMessage().contains("contar"));
        assertTrue(assertThrows(RuntimeException.class, () -> servico.listarNomesAlunos(99L)).getMessage().contains("alunos"));
        assertTrue(assertThrows(RuntimeException.class, () -> servico.listarPorConteudo(99L)).getMessage().contains("denúncias"));
        assertTrue(assertThrows(RuntimeException.class, () -> servico.registrarDenuncia(99L, ALUNO, TURMA, "m")).getMessage().contains("registrar"));
        verify(conteudos, never()).atualizarStatus(anyLong(), anyString());
    }
}
