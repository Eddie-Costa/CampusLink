// verificacao da gestao de turmas do administrador confere cadastro edicao do responsavel e entrada e saida de participantes
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.Admin.cadastrarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.editarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.turmaAdminDTO;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes da gestao de turmas esta classe usa o banco simulado com transacoes reais do spring
class AdminTurmaRepositoryTests {

    private static final String PROFESSOR_ATIVO = "WHERE p.id = ?";
    private static final String ALUNO_ATIVO = "WHERE a.id = ?";
    private static final String TURMA_EXISTE = "SELECT 1\nFROM public.\"TURMAS\"";
    private static final String RESPONSAVEL = "SELECT id_proprietario";
    private static final String VINCULO_ALUNO = "SELECT 1\nFROM public.\"ALUNO_TURMA\"";
    private static final String VINCULO_PROFESSOR = "SELECT 1\nFROM public.\"PROFESSOR_TURMA\"";

    private BancoSimulado banco;
    private AdminTurmaRepository turmas;

    // preparar testes da gestao este metodo liga o repositorio ao banco e as transacoes simuladas
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        turmas = new AdminTurmaRepository(banco.jdbcTemplate(), banco.transacoes());
    }

    // testar listagens da gestao este teste confere turmas com quantidades e professores ativos
    @Test
    void listaTurmasEProfessoresAtivos() throws SQLException {
        // preparar turma sem responsavel e professor ativo
        banco.quandoConsultar("AS quantidade_professores", linha("id", 1L, "nome_turma", "Calculo", "id_proprietario", null,
                "quantidade_alunos", 30, "quantidade_professores", 2));
        banco.quandoConsultar("p.matricula,\n    u.status", linha("id_professor", 3L, "id_usuario", 6L, "nome", "Ana", "matricula", "P1", "status", true));

        // executar listagens
        List<turmaAdminDTO> lista = turmas.listarTurmas();
        List<usuarioAdminDTO> professores = turmas.listarProfessoresAtivos();

        // verificar dados montados
        assertNull(lista.get(0).getIdProprietario());
        assertEquals(30, lista.get(0).getQuantidadeAlunos());
        assertEquals(3L, professores.get(0).getIdPerfil());
        assertEquals("Professor", professores.get(0).getPerfil());
    }

    // testar cadastro da turma este teste confere a turma criada com o responsavel ja vinculado
    @Test
    void cadastraTurmaComResponsavelVinculado() throws SQLException {
        // preparar professor ativo e codigo da nova turma
        banco.quandoConsultar("RETURNING id", linha("id", 50L));

        // executar cadastro com nome cercado de espacos
        Long id = turmas.cadastrarTurma(novaTurma("  Calculo I  ", 3L));

        // verificar codigo nome limpo e vinculo do responsavel
        assertEquals(50L, id);
        assertEquals(List.of("Calculo I", "Noite", 3L, 3L), banco.ultimo("RETURNING id").valores());
        assertEquals(List.of(3L, 50L), banco.ultimo("INSERT INTO public.\"PROFESSOR_TURMA\"").valores());
    }

    // testar responsavel inativo este teste confere que turma nao e criada para professor inexistente ou inativo
    @Test
    void recusaCadastroComResponsavelInativo() {
        // preparar professor nao encontrado entre os ativos
        banco.semResultado(PROFESSOR_ATIVO);

        // executar cadastro
        SQLException erro = assertThrows(SQLException.class, () -> turmas.cadastrarTurma(novaTurma("Calculo", 9L)));

        // verificar mensagem e nenhuma turma criada
        assertTrue(erro.getMessage().contains("Professor responsável"));
        assertFalse(banco.executou("RETURNING id"));
    }

    // testar falha ao criar turma este teste confere o erro quando o banco nao devolve a turma ou o vinculo
    @Test
    void avisaFalhaAoCriarTurmaOuVinculo() {
        // preparar banco sem retorno da turma
        banco.semResultado("RETURNING id");

        // executar cadastro sem retorno e depois sem vinculo
        SQLException semTurma = assertThrows(SQLException.class, () -> turmas.cadastrarTurma(novaTurma("Calculo", 3L)));
        banco.quandoConsultar("RETURNING id", linha("id", 50L)).quandoAlterar("INSERT INTO public.\"PROFESSOR_TURMA\"", 0);
        SQLException semVinculo = assertThrows(SQLException.class, () -> turmas.cadastrarTurma(novaTurma("Calculo", 3L)));

        // verificar mensagens de cada falha
        assertTrue(semTurma.getMessage().contains("cadastrar a turma"));
        assertTrue(semVinculo.getMessage().contains("relacionar o professor"));
    }

    // testar busca da turma este teste confere a turma encontrada e a inexistente
    @Test
    void buscaTurmaParaEdicao() throws SQLException {
        // preparar turma 1
        banco.quandoConsultar("descricao,\n    id_proprietario\nFROM", linha("id", 1L, "nome_turma", "Calculo", "id_proprietario", 3L));

        // executar busca da turma existente e de uma inexistente
        editarTurmaAdminDTO turma = turmas.buscarTurmaPorId(1L);
        banco.semResultado("descricao,\n    id_proprietario\nFROM");
        editarTurmaAdminDTO inexistente = turmas.buscarTurmaPorId(99L);

        // verificar dados e nulo
        assertEquals(3L, turma.getIdProprietario());
        assertNull(inexistente);
    }

    // testar troca de responsavel este teste confere que o antigo e o novo responsavel continuam como professores da turma
    @Test
    void trocaResponsavelMantendoOsDoisNaTurma() throws SQLException {
        // preparar turma do professor 3 sendo passada ao professor 4 que ainda nao participa
        banco.quandoConsultar(RESPONSAVEL, linha("id_proprietario", 3L));
        banco.semResultado(VINCULO_PROFESSOR);

        // executar edicao
        boolean alterou = turmas.atualizarTurma(edicao(1L, " Calculo II ", 4L));

        // verificar edicao inclusao dos dois professores e novo vinculo
        assertTrue(alterou);
        assertEquals(List.of("Calculo II", "Noite", 4L, 1L), banco.ultimo("SET nome_turma = ?").valores());
        List<List<Object>> inclusoes = banco.comandos().stream()
                .filter(c -> c.sql().contains("SET id_professores =")).map(BancoSimulado.Comando::valores).toList();
        assertEquals(List.of(List.of(3L, 3L, 1L), List.of(4L, 4L, 1L)), inclusoes);
        assertEquals(List.of(4L, 1L), banco.ultimo("INSERT INTO public.\"PROFESSOR_TURMA\"").valores());
    }

    // testar edicao invalida este teste confere turma inexistente edicao sem efeito e responsavel inativo
    @Test
    void recusaEdicaoInvalidaDaTurma() {
        // preparar turma inexistente
        banco.semResultado(RESPONSAVEL);

        // executar tentativas invalidas
        boolean inexistente = executar(() -> turmas.atualizarTurma(edicao(99L, "Calculo", 4L)));
        banco.quandoConsultar(RESPONSAVEL, linha("id_proprietario", null)).quandoAlterar("SET nome_turma = ?", 0);
        boolean semEfeito = executar(() -> turmas.atualizarTurma(edicao(1L, "Calculo", 4L)));
        banco.semResultado(PROFESSOR_ATIVO);
        SQLException inativo = assertThrows(SQLException.class, () -> turmas.atualizarTurma(edicao(1L, "Calculo", 9L)));

        // verificar recusas
        assertFalse(inexistente);
        assertFalse(semEfeito);
        assertTrue(inativo.getMessage().contains("Professor responsável"));
    }

    // testar participantes da turma este teste confere alunos e professores listados com e sem data de nascimento
    @Test
    void listaParticipantesEPessoasDisponiveis() throws SQLException {
        // preparar aluno com data e professor sem data
        banco.quandoConsultar("a.rgm", linha("id_aluno", 7L, "id_usuario", 2L, "nome", "Bia", "datanasc", LocalDate.of(2004, 3, 1), "rgm", "123", "status", true));
        banco.quandoConsultar("p.matricula\nFROM", linha("id_professor", 3L, "id_usuario", 6L, "nome", "Ana", "datanasc", null, "matricula", "P1", "status", true));

        // executar listagens
        List<usuarioAdminDTO> alunos = turmas.listarAlunosDaTurma(1L);
        List<usuarioAdminDTO> alunosLivres = turmas.listarAlunosDisponiveis(1L, " bi ");
        List<usuarioAdminDTO> professores = turmas.listarProfessoresDaTurma(1L);
        List<usuarioAdminDTO> professoresLivres = turmas.listarProfessoresDisponiveis(1L);

        // verificar dados e a turma enviada duas vezes para excluir o responsavel
        assertEquals("2004-03-01", alunos.get(0).getDataNasc());
        assertEquals("Aluno", alunosLivres.get(0).getPerfil());
        assertNull(professores.get(0).getDataNasc());
        assertEquals("P1", professoresLivres.get(0).getIdentificador());
        assertEquals(List.of(1L, 1L), banco.ultimo("t.id_proprietario = p.id").valores());
        assertEquals(List.of(1L, "%bi%", "%bi%"), banco.ultimo("AND (u.nome ILIKE ? OR a.rgm ILIKE ?)").valores());
        assertTrue(banco.executou("LIMIT 20"));
    }

    // testar turmas paginadas este teste confere a busca pelo nome e a pagina limitada ao total
    @Test
    void paginaTurmasNoBanco() throws SQLException {
        // preparar 25 turmas encontradas, o que da 2 paginas de 20
        banco.quandoConsultar("SELECT COUNT(*) FROM public.\"TURMAS\" t", linha("count", 25L));

        // executar busca pedindo uma pagina alem da ultima
        var pagina = turmas.listarTurmas(" calc ", 5);

        // verificar filtro, pagina corrigida e deslocamento enviado ao banco
        assertEquals(1, pagina.getNumber());
        assertEquals(2, pagina.getTotalPages());
        assertEquals(List.of("%calc%", "%calc%", 20), banco.ultimo("ORDER BY t.nome_turma, t.id LIMIT").valores());
    }

    // testar entrada do aluno este teste confere a inclusao na lista e o vinculo com situacao verde
    @Test
    void adicionaAlunoAtivoNaTurma() throws SQLException {
        // preparar turma e aluno ativo sem vinculo
        banco.semResultado(VINCULO_ALUNO);

        // executar inclusao
        boolean adicionou = turmas.adicionarAlunoNaTurma(1L, 7L);

        // verificar lista e vinculo
        assertTrue(adicionou);
        assertEquals(List.of(7L, 7L, 1L), banco.ultimo("SET id_alunos =").valores());
        assertEquals(List.of(7L, 1L, "verde"), banco.ultimo("INSERT INTO public.\"ALUNO_TURMA\"").valores());
    }

    // testar entrada invalida do aluno este teste confere codigos ausentes turma inexistente aluno inativo e vinculo repetido
    @Test
    void recusaEntradaInvalidaDeAluno() throws SQLException {
        // preparar turma inexistente

        // executar tentativas invalidas
        boolean semCodigos = turmas.adicionarAlunoNaTurma(null, 7L) || turmas.adicionarAlunoNaTurma(1L, null);
        boolean repetido = turmas.adicionarAlunoNaTurma(1L, 7L);
        banco.semResultado(ALUNO_ATIVO);
        boolean inativo = turmas.adicionarAlunoNaTurma(1L, 8L);
        banco.semResultado(TURMA_EXISTE);
        boolean semTurma = turmas.adicionarAlunoNaTurma(99L, 7L);

        // verificar recusas sem alterar a turma
        assertFalse(semCodigos);
        assertFalse(repetido);
        assertFalse(inativo);
        assertFalse(semTurma);
        assertFalse(banco.executou("SET id_alunos ="));
    }

    // testar falha na entrada do aluno este teste confere o erro quando a lista ou o vinculo nao sao gravados
    @Test
    void avisaFalhaAoGravarEntradaDoAluno() {
        // preparar lista da turma que nao muda
        banco.semResultado(VINCULO_ALUNO).quandoAlterar("SET id_alunos =", 0);

        // executar inclusao com lista sem efeito e depois com vinculo sem efeito
        SQLException lista = assertThrows(SQLException.class, () -> turmas.adicionarAlunoNaTurma(1L, 7L));
        banco.quandoAlterar("SET id_alunos =", 1).quandoAlterar("INSERT INTO public.\"ALUNO_TURMA\"", 0);
        SQLException vinculo = assertThrows(SQLException.class, () -> turmas.adicionarAlunoNaTurma(1L, 7L));

        // verificar mensagens
        assertTrue(lista.getMessage().contains("atualizar a turma"));
        assertTrue(vinculo.getMessage().contains("adicionar o aluno"));
    }

    // testar saida do aluno este teste confere a remocao do vinculo e da lista da turma
    @Test
    void removeAlunoDaTurma() throws SQLException {
        // preparar aluno vinculado

        // executar remocao valida e pedidos invalidos
        boolean removeu = turmas.removerAlunoDaTurma(1L, 7L);
        boolean semCodigos = turmas.removerAlunoDaTurma(null, 7L) || turmas.removerAlunoDaTurma(1L, null);
        banco.semResultado(VINCULO_ALUNO);
        boolean semVinculo = turmas.removerAlunoDaTurma(1L, 8L);

        // verificar remocao e recusas
        assertTrue(removeu);
        assertEquals(List.of(7L, 1L), banco.ultimo("SET id_alunos = array_remove(").valores());
        assertFalse(semCodigos);
        assertFalse(semVinculo);
    }

    // testar falha na saida do aluno este teste confere o erro quando vinculo ou lista nao sao alterados
    @Test
    void avisaFalhaAoRemoverAluno() {
        // preparar vinculo que nao e apagado
        banco.quandoAlterar("DELETE FROM public.\"ALUNO_TURMA\"", 0);

        // executar remocao sem efeito no vinculo e depois na lista
        SQLException vinculo = assertThrows(SQLException.class, () -> turmas.removerAlunoDaTurma(1L, 7L));
        banco.quandoAlterar("DELETE FROM public.\"ALUNO_TURMA\"", 1).quandoAlterar("SET id_alunos = array_remove(", 0);
        SQLException lista = assertThrows(SQLException.class, () -> turmas.removerAlunoDaTurma(1L, 7L));

        // verificar mensagens
        assertTrue(vinculo.getMessage().contains("remover o aluno"));
        assertTrue(lista.getMessage().contains("atualizar os alunos"));
    }

    // testar entrada do professor este teste confere a inclusao de professor que nao e o responsavel
    @Test
    void adicionaProfessorNaTurma() throws SQLException {
        // preparar turma do professor 3 sem o professor 4
        banco.quandoConsultar(RESPONSAVEL, linha("id_proprietario", 3L)).semResultado(VINCULO_PROFESSOR);

        // executar inclusao
        boolean adicionou = turmas.adicionarProfessorNaTurma(1L, 4L);

        // verificar lista e vinculo
        assertTrue(adicionou);
        assertEquals(List.of(4L, 4L, 1L), banco.ultimo("SET id_professores =").valores());
        assertEquals(List.of(4L, 1L), banco.ultimo("INSERT INTO public.\"PROFESSOR_TURMA\"").valores());
    }

    // testar entrada invalida do professor este teste confere responsavel repetido turma inexistente e professor ja vinculado
    @Test
    void recusaEntradaInvalidaDeProfessor() throws SQLException {
        // preparar turma do professor 3
        banco.quandoConsultar(RESPONSAVEL, linha("id_proprietario", 3L));

        // executar tentativas invalidas
        boolean responsavel = turmas.adicionarProfessorNaTurma(1L, 3L);
        boolean jaVinculado = turmas.adicionarProfessorNaTurma(1L, 4L);
        boolean semCodigos = turmas.adicionarProfessorNaTurma(null, 4L) || turmas.adicionarProfessorNaTurma(1L, null);
        banco.semResultado(RESPONSAVEL);
        boolean semTurma = turmas.adicionarProfessorNaTurma(99L, 4L);

        // verificar recusas
        assertFalse(responsavel);
        assertFalse(jaVinculado);
        assertFalse(semCodigos);
        assertFalse(semTurma);
        assertFalse(banco.executou("SET id_professores ="));
    }

    // testar falha na entrada do professor este teste confere o erro quando a lista ou o vinculo nao sao gravados
    @Test
    void avisaFalhaAoGravarEntradaDoProfessor() {
        // preparar turma sem o professor e lista que nao muda
        banco.quandoConsultar(RESPONSAVEL, linha("id_proprietario", 3L)).semResultado(VINCULO_PROFESSOR)
                .quandoAlterar("SET id_professores =", 0);

        // executar inclusao com lista sem efeito e depois com vinculo sem efeito
        SQLException lista = assertThrows(SQLException.class, () -> turmas.adicionarProfessorNaTurma(1L, 4L));
        banco.quandoAlterar("SET id_professores =", 1).quandoAlterar("INSERT INTO public.\"PROFESSOR_TURMA\"", 0);
        SQLException vinculo = assertThrows(SQLException.class, () -> turmas.adicionarProfessorNaTurma(1L, 4L));

        // verificar mensagens
        assertTrue(lista.getMessage().contains("atualizar os professores"));
        assertTrue(vinculo.getMessage().contains("adicionar o professor"));
    }

    // testar saida do professor este teste confere que o responsavel nunca e removido e os outros professores sim
    @Test
    void removeProfessorMasNuncaOResponsavel() throws SQLException {
        // preparar turma do professor 3
        banco.quandoConsultar(RESPONSAVEL, linha("id_proprietario", 3L));

        // executar remocoes
        boolean responsavel = turmas.removerProfessorDaTurma(1L, 3L);
        boolean outro = turmas.removerProfessorDaTurma(1L, 4L);

        // verificar recusa do responsavel e remocao do outro professor
        assertFalse(responsavel);
        assertTrue(outro);
        assertEquals(List.of(4L, 1L), banco.ultimo("SET id_professores = array_remove(").valores());
    }

    // testar saida invalida do professor este teste confere codigos ausentes turma inexistente professor sem vinculo e falhas
    @Test
    void recusaSaidaInvalidaDeProfessor() throws SQLException {
        // preparar turma inexistente
        banco.semResultado(RESPONSAVEL);

        // executar tentativas invalidas
        boolean semCodigos = turmas.removerProfessorDaTurma(null, 4L) || turmas.removerProfessorDaTurma(1L, null);
        boolean semTurma = turmas.removerProfessorDaTurma(99L, 4L);
        banco.quandoConsultar(RESPONSAVEL, linha("id_proprietario", 3L)).semResultado(VINCULO_PROFESSOR);
        boolean semVinculo = turmas.removerProfessorDaTurma(1L, 4L);
        banco.quandoConsultar(VINCULO_PROFESSOR, linha("um", 1)).quandoAlterar("DELETE FROM public.\"PROFESSOR_TURMA\"", 0);
        SQLException vinculo = assertThrows(SQLException.class, () -> turmas.removerProfessorDaTurma(1L, 4L));
        banco.quandoAlterar("DELETE FROM public.\"PROFESSOR_TURMA\"", 1).quandoAlterar("SET id_professores = array_remove(", 0);
        SQLException lista = assertThrows(SQLException.class, () -> turmas.removerProfessorDaTurma(1L, 4L));

        // verificar recusas e mensagens
        assertFalse(semCodigos);
        assertFalse(semTurma);
        assertFalse(semVinculo);
        assertTrue(vinculo.getMessage().contains("remover o professor"));
        assertTrue(lista.getMessage().contains("atualizar os professores"));
    }

    // acao do banco que pode falhar esta interface permite usar metodos que lancam sqlexception nos testes
    private interface AcaoBanco {
        boolean executar() throws SQLException;
    }

    // executar acao do banco este metodo converte erro inesperado em falha do teste
    private boolean executar(AcaoBanco acao) {
        try {
            return acao.executar();
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    // montar nova turma este metodo cria os dados do cadastro com nome e responsavel
    private cadastrarTurmaAdminDTO novaTurma(String nome, Long idProfessor) {
        cadastrarTurmaAdminDTO turma = new cadastrarTurmaAdminDTO();
        turma.setNomeTurma(nome);
        turma.setDescricao("Noite");
        turma.setIdProprietario(idProfessor);
        return turma;
    }

    // montar edicao da turma este metodo cria os dados da edicao com codigo nome e responsavel
    private editarTurmaAdminDTO edicao(Long id, String nome, Long idProfessor) {
        editarTurmaAdminDTO turma = new editarTurmaAdminDTO();
        turma.setId(id);
        turma.setNomeTurma(nome);
        turma.setDescricao("Noite");
        turma.setIdProprietario(idProfessor);
        return turma;
    }
}
