// verificacao do banco de turmas confere consultas por perfil exclusao pelo proprietario e entrada e saida de participantes
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.TurmaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// testes do banco de turmas esta classe usa o banco simulado para conferir os comandos enviados
class TurmaRepositoryTests {

    private static final String USUARIO_POR_EMAIL = "u.perfil FROM public.\"USUARIOS\"";
    private static final String ALUNO_POR_USUARIO = "FROM public.\"ALUNOS\" a WHERE a.id_usuario";
    private static final String PROFESSOR_POR_USUARIO = "FROM public.\"PROFESSORES\" p WHERE p.id_usuario";
    private static final String JA_PARTICIPA = "SELECT 1 FROM public.\"TURMAS\" t WHERE t.id = ? AND (";
    private static final String PROPRIETARIO = "SELECT t.id_proprietario FROM public.\"TURMAS\" t WHERE t.id = ?";

    private BancoSimulado banco;
    private UsuarioRepository logs;
    private TurmaRepository turmas;

    // preparar testes das turmas este metodo liga o repositorio ao banco simulado e ao registro de logs falso
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        logs = mock(UsuarioRepository.class);
        turmas = new TurmaRepository();
        ReflectionTestUtils.setField(turmas, "dataSource", banco.dataSource());
        ReflectionTestUtils.setField(turmas, "UsuarioRepository", logs);
    }

    // testar turmas do aluno este teste confere que o aluno ve apenas as turmas em que esta matriculado
    @Test
    void listaTurmasDoAlunoPelaMatricula() throws SQLException {
        // preparar duas turmas do aluno
        banco.quandoConsultar("WHERE ? = ANY(t.id_alunos)",
                linha("id", 1L, "nome_turma", "Calculo", "descricao", "Turma A"),
                linha("id", 2L, "nome_turma", "Fisica", "descricao", "Turma B"));

        // executar consulta das turmas do aluno
        List<TurmaDTO> resultado = turmas.buscarTurmasDoUsuario("ALUNO", "7");

        // verificar turmas montadas e codigo do aluno enviado
        assertEquals(List.of("Calculo", "Fisica"), resultado.stream().map(TurmaDTO::getNomeTurma).toList());
        assertEquals(List.of(7L), banco.ultimo("ANY(t.id_alunos)").valores());
    }

    // testar turmas do professor este teste confere que entram turmas em que participa e turmas de sua propriedade
    @Test
    void listaTurmasDoProfessorParticipanteOuProprietario() throws SQLException {
        // preparar turma do professor
        banco.quandoConsultar("OR t.id_proprietario = ?)", linha("id", 3L, "nome_turma", "Banco de Dados"));

        // executar consulta das turmas do professor
        List<TurmaDTO> resultado = turmas.buscarTurmasDoUsuario("professor", "4");

        // verificar turma encontrada e o mesmo codigo para os dois vinculos
        assertEquals(3L, resultado.get(0).getId());
        assertEquals(List.of(4L, 4L), banco.ultimo("t.id_proprietario = ?").valores());
    }

    // testar perfil sem turmas este teste confere que outros perfis nao consultam turmas
    @Test
    void perfilSemTurmasNaoConsultaBanco() throws SQLException {
        // preparar perfil administrativo

        // executar consulta com perfil sem turmas
        List<TurmaDTO> resultado = turmas.buscarTurmasDoUsuario("admin", "1");

        // verificar lista vazia sem comandos
        assertTrue(resultado.isEmpty());
        assertTrue(banco.comandos().isEmpty());
    }

    // testar turma do professor este teste confere a primeira e a ultima turma ligadas ao professor
    @Test
    void buscaTurmaEUltimaTurmaDoProfessor() throws SQLException {
        // preparar turma encontrada para o professor
        banco.quandoConsultar("LIMIT 1", linha("id", "12"));

        // executar as duas buscas
        String primeira = turmas.buscarPorIDTurma("4");
        String ultima = turmas.buscarUltimaTurmaPorProfessor("4");

        // verificar codigo devolvido e ordenacao pela turma mais recente
        assertEquals("12", primeira);
        assertEquals("12", ultima);
        assertTrue(banco.executou("ORDER BY t.id DESC LIMIT 1"));
    }

    // testar professor sem turmas este teste confere o retorno quando nao existe turma ligada ao professor
    @Test
    void professorSemTurmaDevolveVazioOuNulo() throws SQLException {
        // preparar banco sem turmas
        banco.linhasPadrao(0);

        // executar as duas buscas
        String primeira = turmas.buscarPorIDTurma("4");
        String ultima = turmas.buscarUltimaTurmaPorProfessor("4");

        // verificar texto vazio e nulo
        assertEquals("", primeira);
        assertNull(ultima);
    }

    // testar registro de atividade este teste confere a gravacao da atividade pela turma e pelo conteudo
    @Test
    void registraAtividadePelaTurmaEPeloConteudo() {
        // preparar email do professor

        // executar os dois registros
        turmas.registrarAlteracao("prof@teste.com", 1L);
        turmas.registrarAlteracaoPorConteudo("prof@teste.com", 10L);

        // verificar email e codigos enviados
        assertEquals(List.of("prof@teste.com", 1L), banco.ultimo("AND t.id = ?").valores());
        assertEquals(List.of("prof@teste.com", 10L), banco.ultimo("AND c.id = ?").valores());
    }

    // testar registro sem dados este teste confere que o registro e ignorado sem email ou sem turma
    @Test
    void ignoraRegistroDeAtividadeSemEmailOuTurma() {
        // preparar chamadas incompletas

        // executar registros sem dados
        turmas.registrarAlteracao(null, 1L);
        turmas.registrarAlteracao("prof@teste.com", null);

        // verificar que nada foi enviado ao banco
        assertTrue(banco.comandos().isEmpty());
    }

    // testar falha no registro este teste confere que a falha ao gravar atividade nao interrompe a acao do usuario
    @Test
    void falhaNoRegistroDeAtividadeNaoInterrompeAcao() throws Exception {
        // preparar banco com erro na tabela de atividade
        banco.falharAo("TURMA_ATIVIDADE");

        // executar registro
        turmas.registrarAlteracao("prof@teste.com", 1L);

        // verificar que o aviso foi gravado nos logs
        verify(logs).InserirLogsNoBD(any(), eq("WARN"), any(), eq("registrarAlteracao"), any(), any(), any(), any());
    }

    // testar turmas recentes este teste confere que so aparecem turmas recentes das quais o usuario ainda participa
    @Test
    void recentesConsideraSoTurmasAtuaisDoUsuario() throws SQLException {
        // preparar atividade em tres turmas sendo uma delas antiga
        banco.quandoConsultar("TURMA_ATIVIDADE", linha("id_turma", 3L), linha("id_turma", 99L), linha("id_turma", 1L));
        List<TurmaDTO> atuais = List.of(turma(1L, "Calculo"), turma(3L, "Fisica"));

        // executar escolha das duas mais recentes
        List<TurmaDTO> recentes = turmas.recentesDentre("aluno@teste.com", atuais, 2);

        // verificar ordem da atividade sem a turma antiga e busca com folga no limite
        assertEquals(List.of(3L, 1L), recentes.stream().map(TurmaDTO::getId).toList());
        assertEquals(List.of("aluno@teste.com", 8), banco.ultimo("TURMA_ATIVIDADE").valores());
    }

    // testar ordem alfabetica este teste confere que acentos e maiusculas nao alteram a ordem dos nomes
    @Test
    void ordenaTurmasIgnorandoAcentosEMaiusculas() {
        // preparar nomes misturados e uma turma sem nome
        List<TurmaDTO> lista = List.of(turma(1L, "fisica"), turma(2L, null), turma(3L, "Álgebra"), turma(4L, "Calculo"));

        // executar ordenacao
        List<TurmaDTO> ordenadas = TurmaRepository.ordenarPorNome(lista);

        // verificar ordem alfabetica com a turma sem nome no final
        assertEquals(List.of(3L, 4L, 1L, 2L), ordenadas.stream().map(TurmaDTO::getId).toList());
    }

    // testar turmas por aluno este teste confere o agrupamento das turmas de varios alunos
    @Test
    void agrupaTurmasPorAluno() throws SQLException {
        // preparar dois vinculos do aluno 7 e um do aluno 8
        banco.quandoConsultar("unnest(t.id_alunos)",
                linha("id_turma", 1L, "id_aluno", 7L),
                linha("id_turma", 2L, "id_aluno", 7L),
                linha("id_turma", 1L, "id_aluno", 8L));

        // executar agrupamento
        Map<Long, List<Long>> resultado = turmas.buscarTurmasDosAlunos(List.of(7L, 8L));

        // verificar turmas de cada aluno
        assertEquals(List.of(1L, 2L), resultado.get(7L));
        assertEquals(List.of(1L), resultado.get(8L));
    }

    // testar dados da turma este teste confere a turma encontrada e a turma inexistente
    @Test
    void buscaTurmaPorCodigo() throws SQLException {
        // preparar turma 5 cadastrada e turma 6 inexistente
        banco.quandoConsultar("t.id_proprietario FROM", linha("id", 5L, "nome_turma", "Redes", "descricao", "Noite", "id_proprietario", 4L));

        // executar busca da turma
        TurmaDTO turma = turmas.buscarTurmaPorId("5");
        banco.semResultado("t.id_proprietario FROM");
        TurmaDTO inexistente = turmas.buscarTurmaPorId("6");

        // verificar dados montados e retorno nulo
        assertEquals("Redes", turma.getNomeTurma());
        assertEquals(4L, turma.getIdProprietario());
        assertNull(inexistente);
    }

    // testar edicao por outro professor este teste confere que so o proprietario altera nome e descricao
    @Test
    void edicaoDaTurmaSoValeParaProprietario() throws SQLException {
        // preparar banco sem turma do professor 9
        banco.quandoAlterar("UPDATE public.\"TURMAS\" SET nome_turma", 0);

        // executar tentativa de edicao
        boolean alterou = turmas.atualizarTurmaDoProprietario("5", "9", "Novo nome", "Nova descricao");

        // verificar recusa e filtro pelo proprietario
        assertFalse(alterou);
        assertEquals(List.of("Novo nome", "Nova descricao", 5L, 9L), banco.ultimo("SET nome_turma").valores());
    }

    // testar participantes e sugestoes este teste confere a lista da turma e a busca por nome ou email
    @Test
    void listaParticipantesESugestoesDaTurma() throws SQLException {
        // preparar um participante e uma sugestao
        banco.quandoConsultar("participantes.tipo_usuario FROM", linha("nome", "Ana", "email", "ana@teste.com", "tipo_usuario", "Aluno"));
        banco.quandoConsultar("LIMIT 10", linha("nome", "Bia", "email", "bia@teste.com", "tipo_usuario", "Professor"));

        // executar as duas consultas
        List<TurmaDTO> participantes = turmas.buscarParticipantesDaTurma("5");
        List<TurmaDTO> sugestoes = turmas.buscarSugestoesPessoasDaTurma("  bi ", "5");

        // verificar pessoas montadas e texto da busca sem espacos
        assertEquals("ana@teste.com", participantes.get(0).getEmailPessoa());
        assertEquals("Professor", sugestoes.get(0).getTipoUsuario());
        assertEquals(List.of("%bi%", "%bi%", 5L, 5L), banco.ultimo("LIMIT 10").valores());
    }

    // testar exclusao por outro professor este teste confere que a turma so e apagada pelo proprietario
    @Test
    void bloqueiaExclusaoDeQuemNaoEhProprietario() {
        // preparar turma que nao pertence ao professor
        banco.semResultado("WHERE id = ? AND id_proprietario = ?");

        // executar tentativa de exclusao
        SQLException erro = assertThrows(SQLException.class, () -> turmas.excluirTurma("5", "9"));

        // verificar mensagem e nenhum vinculo apagado
        assertTrue(erro.getMessage().contains("proprietário"));
        assertFalse(banco.executou("DELETE"));
    }

    // testar exclusao pelo proprietario este teste confere que os vinculos sao apagados antes da turma
    @Test
    void apagaVinculosAntesDaTurma() throws SQLException {
        // preparar turma do professor

        // executar exclusao
        turmas.excluirTurma("5", "4");

        // verificar ordem dos comandos de exclusao
        List<String> sqls = banco.comandos().stream().map(BancoSimulado.Comando::sql).toList();
        assertTrue(sqls.get(1).contains("ALUNO_TURMA"));
        assertTrue(sqls.get(2).contains("PROFESSOR_TURMA"));
        assertTrue(sqls.get(3).startsWith("DELETE FROM public.\"TURMAS\""));
    }

    // testar exclusao sem efeito este teste confere o erro quando a turma nao foi apagada
    @Test
    void avisaQuandoNenhumaTurmaFoiApagada() {
        // preparar exclusao que nao encontra a turma
        banco.quandoAlterar("DELETE FROM public.\"TURMAS\" WHERE id = ?", 0);

        // executar exclusao
        SQLException erro = assertThrows(SQLException.class, () -> turmas.excluirTurma("5", "4"));

        // verificar mensagem de nenhuma turma removida
        assertTrue(erro.getMessage().contains("Nenhuma turma"));
    }

    // testar entrada do aluno este teste confere a inclusao do aluno na lista e no vinculo com situacao verde
    @Test
    void adicionaAlunoNaTurmaComSituacaoVerde() throws Exception {
        // preparar aluno 9 ainda fora da turma
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 5L, "perfil", "ALUNOS"));
        banco.quandoConsultar(ALUNO_POR_USUARIO, linha("id", 9L));
        banco.semResultado(JA_PARTICIPA);

        // executar inclusao
        boolean adicionou = turmas.inserirPessoaTurma("aluno@teste.com", "5");

        // verificar inclusao na lista e vinculo verde
        assertTrue(adicionou);
        assertEquals(List.of(9L, 5L), banco.ultimo("array_append(COALESCE(\"id_alunos\"").valores());
        assertEquals(List.of(9L, 5L, "verde"), banco.ultimo("INSERT INTO public.\"ALUNO_TURMA\"").valores());
    }

    // testar entrada do professor este teste confere a inclusao do professor na lista de professores
    @Test
    void adicionaProfessorNaTurma() throws Exception {
        // preparar professor 3 ainda fora da turma
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 6L, "perfil", "professores"));
        banco.quandoConsultar(PROFESSOR_POR_USUARIO, linha("id", 3L));
        banco.semResultado(JA_PARTICIPA);

        // executar inclusao
        boolean adicionou = turmas.inserirPessoaTurma("prof@teste.com", "5");

        // verificar vinculo do professor sem situacao de aluno
        assertTrue(adicionou);
        assertEquals(List.of(3L, 5L), banco.ultimo("INSERT INTO public.\"PROFESSOR_TURMA\"").valores());
    }

    // testar entrada repetida este teste confere que a mesma pessoa nao entra duas vezes na turma
    @Test
    void naoAdicionaQuemJaParticipa() throws Exception {
        // preparar aluno que ja esta na turma
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 5L, "perfil", "alunos"));
        banco.quandoConsultar(ALUNO_POR_USUARIO, linha("id", 9L));

        // executar inclusao
        boolean adicionou = turmas.inserirPessoaTurma("aluno@teste.com", "5");

        // verificar recusa sem alterar a turma
        assertFalse(adicionou);
        assertFalse(banco.executou("array_append"));
    }

    // testar entrada invalida este teste confere a recusa para email inexistente perfil invalido e cadastro incompleto
    @Test
    void recusaEntradaSemContaValidaNaTurma() throws Exception {
        // preparar email sem conta
        banco.semResultado(USUARIO_POR_EMAIL);

        // executar tentativas com email inexistente perfil administrativo e aluno sem cadastro
        boolean semConta = turmas.inserirPessoaTurma("nada@teste.com", "5");
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 1L, "perfil", "administrador"));
        boolean administrador = turmas.inserirPessoaTurma("admin@teste.com", "5");
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 5L, "perfil", "alunos"));
        banco.semResultado(ALUNO_POR_USUARIO);
        boolean semCadastro = turmas.inserirPessoaTurma("aluno@teste.com", "5");

        // verificar as tres recusas sem gravar nada
        assertFalse(semConta);
        assertFalse(administrador);
        assertFalse(semCadastro);
        assertFalse(banco.executou("UPDATE"));
        verify(logs, atLeastOnce()).InserirLogsNoBD(any(), eq("WARN"), any(), eq("inserirPessoaTurma"), any(), any(), any(), any());
    }

    // testar turma que nao recebeu a pessoa este teste confere a recusa quando a lista da turma nao muda
    @Test
    void recusaEntradaQuandoTurmaNaoFoiAtualizada() throws Exception {
        // preparar aluno valido e turma inexistente
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 5L, "perfil", "alunos"));
        banco.quandoConsultar(ALUNO_POR_USUARIO, linha("id", 9L));
        banco.semResultado(JA_PARTICIPA);
        banco.quandoAlterar("array_append", 0);

        // executar inclusao
        boolean adicionou = turmas.inserirPessoaTurma("aluno@teste.com", "99");

        // verificar recusa sem gravar o vinculo
        assertFalse(adicionou);
        assertFalse(banco.executou("INSERT INTO public.\"ALUNO_TURMA\""));
    }

    // testar saida do aluno este teste confere a retirada do aluno da lista e do vinculo da turma
    @Test
    void removeAlunoDaTurma() throws Exception {
        // preparar aluno 9 participante da turma
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 5L, "perfil", "alunos"));
        banco.quandoConsultar(ALUNO_POR_USUARIO, linha("id", 9L));

        // executar remocao
        boolean removeu = turmas.revomerPessoaTurma("aluno@teste.com", "5");

        // verificar retirada da lista e do vinculo
        assertTrue(removeu);
        assertEquals(List.of(9L, 5L), banco.ultimo("array_remove(COALESCE(\"id_alunos\"").valores());
        assertEquals(List.of(9L, 5L), banco.ultimo("DELETE FROM public.\"ALUNO_TURMA\"").valores());
    }

    // testar saida do proprietario este teste confere que o professor responsavel nao pode ser removido
    @Test
    void bloqueiaRemocaoDoProfessorProprietario() throws Exception {
        // preparar professor 3 que e dono da turma
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 6L, "perfil", "professores"));
        banco.quandoConsultar(PROFESSOR_POR_USUARIO, linha("id", 3L));
        banco.quandoConsultar(PROPRIETARIO, linha("id_proprietario", 3L));

        // executar tentativa de remocao
        boolean removeu = turmas.revomerPessoaTurma("dono@teste.com", "5");

        // verificar recusa sem alterar a turma
        assertFalse(removeu);
        assertFalse(banco.executou("array_remove"));
    }

    // testar saida de outro professor este teste confere que professores que nao sao donos podem sair
    @Test
    void removeProfessorQueNaoEhProprietario() throws Exception {
        // preparar professor 3 em turma de outro dono
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 6L, "perfil", "professores"));
        banco.quandoConsultar(PROFESSOR_POR_USUARIO, linha("id", 3L));
        banco.quandoConsultar(PROPRIETARIO, linha("id_proprietario", 4L));

        // executar remocao
        boolean removeu = turmas.revomerPessoaTurma("prof@teste.com", "5");

        // verificar remocao do vinculo de professor
        assertTrue(removeu);
        assertTrue(banco.executou("DELETE FROM public.\"PROFESSOR_TURMA\""));
    }

    // testar saida invalida este teste confere a recusa para email inexistente perfil invalido cadastro incompleto e quem nao participa
    @Test
    void recusaSaidaDeQuemNaoPodeSerRemovido() throws Exception {
        // preparar email sem conta
        banco.semResultado(USUARIO_POR_EMAIL);

        // executar tentativas para cada situacao invalida
        boolean semConta = turmas.revomerPessoaTurma("nada@teste.com", "5");
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 1L, "perfil", "administrador"));
        boolean administrador = turmas.revomerPessoaTurma("admin@teste.com", "5");
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 5L, "perfil", "alunos"));
        banco.semResultado(ALUNO_POR_USUARIO);
        boolean semCadastro = turmas.revomerPessoaTurma("aluno@teste.com", "5");
        banco.quandoConsultar(ALUNO_POR_USUARIO, linha("id", 9L));
        banco.semResultado(JA_PARTICIPA);
        boolean naoParticipa = turmas.revomerPessoaTurma("aluno@teste.com", "5");

        // verificar as quatro recusas sem alterar a turma
        assertFalse(semConta);
        assertFalse(administrador);
        assertFalse(semCadastro);
        assertFalse(naoParticipa);
        assertFalse(banco.executou("array_remove"));
    }

    // testar saida sem efeito este teste confere a recusa quando a lista ou o vinculo nao foram alterados
    @Test
    void recusaSaidaQuandoNadaFoiAlterado() throws Exception {
        // preparar aluno participante e lista que nao muda
        banco.quandoConsultar(USUARIO_POR_EMAIL, linha("id", 5L, "perfil", "alunos"));
        banco.quandoConsultar(ALUNO_POR_USUARIO, linha("id", 9L));
        banco.quandoAlterar("array_remove", 0);

        // executar remocao com lista sem efeito e depois com vinculo sem efeito
        boolean listaSemEfeito = turmas.revomerPessoaTurma("aluno@teste.com", "5");
        banco.quandoAlterar("array_remove", 1);
        banco.quandoAlterar("DELETE FROM public.\"ALUNO_TURMA\"", 0);
        boolean vinculoSemEfeito = turmas.revomerPessoaTurma("aluno@teste.com", "5");

        // verificar as duas recusas
        assertFalse(listaSemEfeito);
        assertFalse(vinculoSemEfeito);
    }

    // montar turma do teste este metodo cria uma turma com codigo e nome
    private TurmaDTO turma(Long id, String nome) {
        TurmaDTO turma = new TurmaDTO();
        turma.setId(id);
        turma.setNomeTurma(nome);
        return turma;
    }
}
