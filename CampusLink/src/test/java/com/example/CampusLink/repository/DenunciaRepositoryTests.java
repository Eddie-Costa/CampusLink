// verificacao do banco de denuncias confere registro repetido contagem de alunos e atualizacao das denuncias pendentes
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.DenunciaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes do banco de denuncias esta classe usa o banco simulado com jdbctemplate real
class DenunciaRepositoryTests {

    private BancoSimulado banco;
    private DenunciaRepository denuncias;

    // preparar testes das denuncias este metodo liga o repositorio ao banco simulado
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        denuncias = new DenunciaRepository(banco.jdbcTemplate());
    }

    // testar registro da denuncia este teste confere os dados enviados e a situacao devolvida
    @Test
    void registraDenunciaDoAluno() throws SQLException {
        // preparar denuncia
        DenunciaDTO denuncia = new DenunciaDTO();
        denuncia.setIdConteudo(10L);
        denuncia.setIdAluno(7L);
        denuncia.setIdProfessor(4L);
        denuncia.setMotivo("Link quebrado");
        banco.quandoConsultar("INSERT INTO public.\"DENUNCIAS\"", linha("id", 3L, "status", "pendente", "created_at", LocalDateTime.now()));

        // executar registro
        denuncias.inserir(denuncia);

        // verificar parametros e retorno
        assertEquals(List.of(10L, 7L, 4L, "Link quebrado"), banco.ultimo("INSERT INTO").valores());
        assertEquals("pendente", denuncia.getStatus());
    }

    // testar denuncia repetida e contagens este teste confere a denuncia ja feita e os totais usados na suspensao
    @Test
    void confereDenunciaRepetidaEContagens() throws SQLException {
        // preparar totais do banco
        banco.quandoConsultar("AND \"id_aluno\" = ?", linha("quantidade", 1));
        banco.quandoConsultar("AND \"status\" = 'pendente'", linha("quantidade", 4));
        banco.quandoConsultar("FROM public.\"ALUNO_TURMA\"", linha("quantidade", 10));

        // executar consultas
        boolean repetida = denuncias.alunoJaDenunciou(10L, 7L);
        int pendentes = denuncias.contarDenunciasPendentes(10L);
        int alunos = denuncias.contarAlunosDaTurma(1L);

        // verificar valores
        assertTrue(repetida);
        assertEquals(4, pendentes);
        assertEquals(10, alunos);
    }

    // testar consultas sem resultado este teste confere os valores padrao quando o banco nao devolve linhas
    @Test
    void usaZeroQuandoNaoHaResultado() throws SQLException {
        // preparar banco vazio
        banco.linhasPadrao(0);

        // executar consultas
        boolean repetida = denuncias.alunoJaDenunciou(10L, 7L);
        int pendentes = denuncias.contarDenunciasPendentes(10L);
        int alunos = denuncias.contarAlunosDaTurma(1L);

        // verificar valores padrao
        assertFalse(repetida);
        assertEquals(0, pendentes);
        assertEquals(0, alunos);
    }

    // testar detalhes das denuncias este teste confere nomes dos alunos lista e atualizacao das pendentes
    @Test
    void listaEAtualizaDenunciasDoConteudo() throws SQLException {
        // preparar denuncia com aluno
        banco.quandoConsultar("SELECT DISTINCT u.\"nome\"", linha("nome", "Ana"));
        banco.quandoConsultar("AS nome_aluno", linha("id", 3L, "motivo", "Ofensivo", "nome_aluno", "Ana", "created_at", LocalDateTime.now()));

        // executar consultas e atualizacao
        List<String> nomes = denuncias.listarNomesAlunosPorConteudo(10L);
        List<DenunciaDTO> lista = denuncias.listarPorConteudo(10L);
        denuncias.atualizarStatusPorConteudo(10L, "aceita");

        // verificar dados e atualizacao apenas das pendentes
        assertEquals(List.of("Ana"), nomes);
        assertEquals("Ofensivo", lista.get(0).getMotivo());
        assertEquals(List.of("aceita", 10L), banco.ultimo("UPDATE public.\"DENUNCIAS\"").valores());
    }

    // testar falha do banco este teste confere que o erro original chega ao servico
    @Test
    void repassaErroDoBanco() {
        // preparar falha
        banco.falharAo("DENUNCIAS");

        // executar consulta
        SQLException erro = assertThrows(SQLException.class, () -> denuncias.listarPorConteudo(10L));

        // verificar mensagem original
        assertTrue(erro.getMessage().contains("falha simulada"));
    }
}
