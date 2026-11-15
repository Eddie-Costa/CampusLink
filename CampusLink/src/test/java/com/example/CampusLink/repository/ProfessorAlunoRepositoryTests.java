// verificacao dos bancos de professores e alunos confere login vinculos com turmas e falhas de gravacao
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.List;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// testes de professores e alunos esta classe usa o mesmo banco simulado para os dois repositorios
class ProfessorAlunoRepositoryTests {

    private BancoSimulado banco;
    private UsuarioRepository logs;
    private ProfessorRepository professores;
    private AlunoRepository alunos;

    // preparar testes de professores e alunos este metodo liga os dois repositorios ao banco simulado
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        logs = mock(UsuarioRepository.class);
        professores = new ProfessorRepository();
        ReflectionTestUtils.setField(professores, "dataSource", banco.dataSource());
        alunos = new AlunoRepository(banco.jdbcTemplate(), banco.transacoes(), logs);
    }

    // testar login do professor este teste confere a conta encontrada e o codigo do professor
    @Test
    void buscaProfessorParaLogin() throws SQLException {
        // preparar professor cadastrado
        banco.quandoConsultar("SELECT * FROM public.\"USUARIOS\"", linha("email", "p@teste.com", "senha", "hash"));
        banco.quandoConsultar("SELECT p.id FROM", linha("id", "4"));

        // executar buscas
        loginProfessorDTO professor = professores.buscarPorEmailProfessor("p@teste.com");
        String id = professores.buscarPorIDProfessor("p@teste.com");
        banco.linhasPadrao(0).semResultado("SELECT * FROM public.\"USUARIOS\"").semResultado("SELECT p.id FROM");
        loginProfessorDTO inexistente = professores.buscarPorEmailProfessor("x@teste.com");
        String semId = professores.buscarPorIDProfessor("x@teste.com");

        // verificar dados e retornos vazios
        assertEquals("hash", professor.getSenha());
        assertEquals("4", id);
        assertNull(inexistente);
        assertEquals("", semId);
    }

    // testar vinculos do professor este teste confere criacao da turma e entrada e saida do professor
    @Test
    void gravaTurmaEVinculosDoProfessor() throws SQLException {
        // preparar banco que grava os registros

        // executar gravacoes
        professores.InsertTurmasIntoBD("Calculo", "Noite", "4");
        professores.InsertProfessor_TurmaIntoBD("4", "1");
        professores.removeProfessor_TurmaIntoBD("4", "1");

        // verificar parametros e confirmacoes
        assertEquals(List.of("Calculo", "Noite", 4L), banco.ultimo("INSERT INTO public.\"TURMAS\"").valores());
        assertEquals(List.of(4L, 1L), banco.ultimo("DELETE FROM public.\"PROFESSOR_TURMA\"").valores());
        assertEquals(3, banco.commits());
    }

    // testar gravacao sem efeito este teste confere o erro quando turma ou vinculo nao sao gravados
    @Test
    void avisaQuandoTurmaOuVinculoNaoSaoGravados() {
        // preparar banco que nao grava nada
        banco.alteradasPadrao(0);

        // executar gravacoes
        SQLException turma = assertThrows(SQLException.class, () -> professores.InsertTurmasIntoBD("Calculo", "Noite", "4"));
        SQLException vinculo = assertThrows(SQLException.class, () -> professores.InsertProfessor_TurmaIntoBD("4", "1"));

        // verificar mensagens e nenhuma confirmacao
        assertTrue(turma.getMessage().contains("Nenhuma turma"));
        assertTrue(vinculo.getMessage().contains("professor_turma"));
        assertEquals(0, banco.commits());
    }

    // testar login do aluno este teste confere a conta encontrada e a inexistente
    @Test
    void buscaAlunoParaLogin() throws Exception {
        // preparar aluno cadastrado
        banco.quandoConsultar("SELECT \"email\", \"senha\"", linha("email", "a@teste.com", "senha", "hash"));

        // executar buscas
        loginAlunoDTO aluno = alunos.buscarPorEmailAluno("a@teste.com");
        String id = alunos.buscarPorIDAluno("a@teste.com");
        banco.linhasPadrao(0).semResultado("SELECT \"email\", \"senha\"");
        loginAlunoDTO inexistente = alunos.buscarPorEmailAluno("x@teste.com");
        String semId = alunos.buscarPorIDAluno("x@teste.com");

        // verificar dados e retornos vazios
        assertEquals("hash", aluno.getSenha());
        assertEquals("1", id);
        assertNull(inexistente);
        assertEquals("", semId);
    }

    // testar falha no login do aluno este teste confere que o erro do banco chega ao servico e e registrado
    @Test
    void registraFalhaNaBuscaDoAluno() throws Exception {
        // preparar falha na consulta
        banco.falharAo("SELECT \"email\", \"senha\"");

        // executar busca
        SQLException erro = assertThrows(SQLException.class, () -> alunos.buscarPorEmailAluno("a@teste.com"));

        // verificar erro original e log de erro
        assertTrue(erro.getMessage().contains("falha simulada"));
        verify(logs).InserirLogsNoBD(any(), eq("ERROR"), any(), any(), any(), any(), any(), any());
    }

    // testar vinculos do aluno este teste confere entrada com situacao verde saida e falha sem gravacao
    @Test
    void gravaVinculosDoAluno() throws SQLException {
        // preparar banco que grava os vinculos

        // executar entrada e saida
        alunos.InsertAluno_TurmaIntoBD("7", "1");
        alunos.removeAluno_TurmaIntoBD("7", "1");
        banco.quandoAlterar("INSERT INTO public.\"ALUNO_TURMA\"", 0);
        SQLException semEntrada = assertThrows(SQLException.class, () -> alunos.InsertAluno_TurmaIntoBD("7", "2"));

        // verificar parametros e erro
        assertEquals(List.of(7L, 1L, "verde"), banco.comandos().get(0).valores());
        assertEquals(List.of(7L, 1L), banco.ultimo("DELETE FROM public.\"ALUNO_TURMA\"").valores());
        assertTrue(semEntrada.getMessage().contains("Nenhum"));
    }
}
