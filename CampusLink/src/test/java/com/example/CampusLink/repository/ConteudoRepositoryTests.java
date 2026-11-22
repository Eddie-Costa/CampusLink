// verificacao do banco de conteudos confere filtros acesso prazos conclusoes e alteracoes dos materiais
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.ConteudoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes do banco de conteudos esta classe usa o banco simulado com jdbctemplate real
class ConteudoRepositoryTests {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 12, 0);

    private BancoSimulado banco;
    private ConteudoRepository conteudos;

    // preparar testes dos conteudos este metodo liga o repositorio ao banco simulado
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        conteudos = new ConteudoRepository(banco.jdbcTemplate());
    }

    // testar cadastro do material este teste confere os dados enviados e o codigo devolvido pelo banco
    @Test
    void cadastraConteudoERecuperaCodigoESituacao() throws SQLException {
        // preparar material sem descricao e retorno do banco
        ConteudoDTO conteudo = conteudo("Aula 1");
        conteudo.setDescricao(null);
        banco.quandoConsultar("INSERT INTO public.\"CONTEUDOS\"", linha("id", 40L, "status", "ativo", "created_at", AGORA));

        // executar cadastro
        conteudos.inserir(conteudo);

        // verificar descricao vazia e dados devolvidos
        // primeiro valor e a lista de turmas, que aqui tem so a turma 1
        List<Object> valores = banco.ultimo("INSERT INTO public.\"CONTEUDOS\"").valores();
        assertEquals(List.of(1L), List.of((Object[]) ((java.sql.Array) valores.get(0)).getArray()));
        assertEquals(List.of(2L, "Aula 1", "", "link", "https://www.youtube.com/watch?v=1", "30 min", "alta", 30),
                valores.subList(1, valores.size()));
        assertEquals(40L, conteudo.getId());
        assertEquals("ativo", conteudo.getStatus());
        assertNotNull(conteudo.getCreatedAt());
    }

    // testar falha no banco este teste confere que o erro original do banco chega ao servico
    @Test
    void repassaErroOriginalDoBanco() {
        // preparar falha na consulta de conteudos
        banco.falharAo("FROM public.\"CONTEUDOS\"");

        // executar consulta
        SQLException erro = assertThrows(SQLException.class, () -> conteudos.listarPorTurma(1L));

        // verificar mensagem original
        assertTrue(erro.getMessage().contains("falha simulada"));
    }

    // testar materiais da turma e do evento este teste confere a montagem dos materiais encontrados
    @Test
    void listaConteudosDaTurmaEDoEvento() throws SQLException {
        // preparar material ativo
        banco.quandoConsultar("CONTEUDOS", linha("id", 10L, "titulo", "Apostila", "status", "ativo", "duracao_estimada_min", 45));

        // executar consultas
        List<ConteudoDTO> daTurma = conteudos.listarPorTurma(1L);
        List<ConteudoDTO> doEvento = conteudos.listarPorEvento(5L);

        // verificar material com turma informada e sem turma no evento
        assertEquals("Apostila", daTurma.get(0).getTitulo());
        assertEquals(1L, daTurma.get(0).getIdTurma());
        assertEquals(45, doEvento.get(0).getDuracaoEstimadaMin());
        assertNull(doEvento.get(0).getIdTurma());
    }

    // testar materiais de varios eventos este teste confere o agrupamento por evento e a lista vazia sem consulta
    @Test
    void agrupaConteudosPorEvento() throws SQLException {
        // preparar dois materiais do evento 5 e um do evento 6
        banco.quandoConsultar("AS evento_id",
                linha("id", 10L, "evento_id", 5L), linha("id", 11L, "evento_id", 5L), linha("id", 12L, "evento_id", 6L));

        // executar agrupamento e consulta sem eventos
        Map<Long, List<ConteudoDTO>> porEvento = conteudos.listarPorEventos(List.of(5L, 6L));
        Map<Long, List<ConteudoDTO>> vazio = conteudos.listarPorEventos(List.of());

        // verificar grupos e que a lista vazia nao consultou o banco
        assertEquals(2, porEvento.get(5L).size());
        assertEquals(1, porEvento.get(6L).size());
        assertTrue(vazio.isEmpty());
        assertEquals(1, banco.comandos().size());
    }

    // testar busca de videos este teste confere a busca textual e o filtro de links do youtube
    @Test
    void buscaVideosPorTextoNaTurma() throws SQLException {
        // preparar turma com material

        // executar busca de videos com texto
        conteudos.buscarPorTurma(1L, "derivada", "video");

        // verificar filtro do youtube e texto usado na busca e na relevancia
        BancoSimulado.Comando comando = banco.ultimo("websearch_to_tsquery");
        assertTrue(comando.sql().contains("youtube"));
        assertTrue(comando.sql().contains("ts_rank"));
        assertEquals(List.of(1L, "derivada", "derivada", "derivada", "derivada"), comando.valores());
    }

    // testar busca de links este teste confere que links comuns excluem videos e livros
    @Test
    void buscaLinksSemVideosNemLivros() throws SQLException {
        // preparar turma com material

        // executar busca de links sem texto
        conteudos.buscarPorTurma(1L, " ", "link");

        // verificar exclusao de videos e livros e ordenacao pela data
        BancoSimulado.Comando comando = banco.ultimo("AND NOT (");
        assertTrue(comando.sql().contains("books"));
        assertFalse(comando.sql().contains("ts_rank"));
        assertEquals(List.of(1L, "link"), comando.valores());
    }

    // testar busca de livros e outros tipos este teste confere os filtros de livros arquivos e sem filtro
    @Test
    void buscaLivrosArquivosOuTudo() throws SQLException {
        // preparar turma com material

        // executar as tres buscas
        conteudos.buscarPorTurma(1L, null, "livro");
        BancoSimulado.Comando livro = banco.comandos().get(0);
        conteudos.buscarPorTurma(1L, null, "arquivo");
        BancoSimulado.Comando arquivo = banco.comandos().get(1);
        conteudos.buscarPorTurma(1L, null, null);
        BancoSimulado.Comando todos = banco.comandos().get(2);

        // verificar filtros de cada busca
        assertTrue(livro.sql().contains("google"));
        assertEquals(List.of(1L), livro.valores());
        assertEquals(List.of(1L, "arquivo"), arquivo.valores());
        assertFalse(todos.sql().contains("\"tipo\""));
    }

    // testar materiais do professor este teste confere material sem turma e pedido de revisao
    @Test
    void listaConteudosDoProfessorComESemTurma() throws SQLException {
        // preparar material sem turma e com revisao pedida
        banco.quandoConsultar("LEFT JOIN public.\"TURMAS\" t",
                linha("id", 10L, "turma_id", null, "titulo", "Lista", "revisao_solicitada", true, "revisao_solicitada_em", AGORA),
                linha("id", 11L, "turma_id", 3L, "titulo", "Prova", "nome_turma", "Calculo", "revisao_solicitada_em", null));

        // executar consulta
        List<ConteudoDTO> resultado = conteudos.listarPorProfessor(4L);

        // verificar turma ausente e dados da revisao
        assertNull(resultado.get(0).getIdTurma());
        assertTrue(resultado.get(0).isRevisaoSolicitada());
        assertNotNull(resultado.get(0).getRevisaoSolicitadaEm());
        assertEquals(3L, resultado.get(1).getIdTurma());
        assertEquals("Calculo", resultado.get(1).getNomeTurma());
    }

    // testar paginacao do professor este teste confere que situacao invalida vira todos e pagina negativa vira a primeira
    @Test
    void paginaConteudosDoProfessorComFiltrosSeguros() throws SQLException {
        // preparar pagina com material
        banco.quandoConsultar("LIMIT 10 OFFSET ?", linha("id", 10L, "turma_id", 3L, "nome_turma", "Calculo, Fisica"));

        // executar pagina invalida com situacao desconhecida e pagina 2 com situacao valida
        List<ConteudoDTO> pagina = conteudos.listarPorProfessorPaginado(4L, -1, null, "qualquer");
        conteudos.listarPorProfessorPaginado(4L, 2, "  lista ", " ATIVO ");

        // verificar filtros enviados e nomes das turmas
        assertEquals("Calculo, Fisica", pagina.get(0).getNomeTurma());
        assertEquals(List.of(4L, "todos", "todos", "", "", "", 0L), banco.comandos().get(0).valores());
        assertEquals(List.of(4L, "ativo", "ativo", "lista", "lista", "lista", 20L), banco.comandos().get(1).valores());
    }

    // testar contadores do professor este teste confere o total filtrado e os totais por situacao
    @Test
    void contaConteudosDoProfessor() throws SQLException {
        // preparar totais do banco
        banco.quandoConsultar("AS quantidade", linha("quantidade", 7));
        banco.quandoConsultar("AS removidos", linha("total", 9, "ativos", 6, "suspensos", 2, "removidos", 1));

        // executar contagens
        int filtrados = conteudos.contarFiltradosPorProfessor(4L, "a", "removido");
        Map<String, Integer> totais = conteudos.contarPorProfessor(4L);

        // verificar totais
        assertEquals(7, filtrados);
        assertEquals(Map.of("totalConteudos", 9, "totalAtivos", 6, "totalSuspensos", 2, "totalRemovidos", 1), totais);
    }

    // testar listagens do administrador este teste confere a paginacao a contagem e a fila de revisao
    @Test
    void listaConteudosParaAdministrador() throws SQLException {
        // preparar material com autor e total filtrado
        banco.quandoConsultar("AS nome_professor", linha("id", 10L, "nome_professor", "Prof. Ana", "turma_id", null));
        banco.quandoConsultar("SELECT COUNT(*) AS quantidade", linha("quantidade", 3));
        banco.quandoConsultar("SELECT COUNT(*) FROM public.\"CONTEUDOS\" c WHERE", linha("count", 1L));

        // executar consultas do administrador
        List<ConteudoDTO> pagina = conteudos.listarTodosParaAdminPaginado(1, "ana", "suspenso_denuncia");
        int total = conteudos.contarFiltradosParaAdmin(null, null);
        List<ConteudoDTO> revisao = conteudos.listarParaAnaliseAdmin(0).getContent();

        // verificar autor filtros e fila de revisao
        assertEquals("Prof. Ana", pagina.get(0).getNomeProfessor());
        assertEquals(List.of("suspenso_denuncia", "suspenso_denuncia", "ana", "ana", "ana", "ana", 10L), banco.comandos().get(0).valores());
        assertEquals(3, total);
        assertEquals("Prof. Ana", revisao.get(0).getNomeProfessor());
        assertEquals(List.of(10, 0), banco.ultimo("LIMIT ? OFFSET ?").valores());
    }

    // testar dados soltos do material este teste confere autor e situacao e o retorno nulo do material inexistente
    @Test
    void buscaAutorESituacaoDoConteudo() throws SQLException {
        // preparar material encontrado
        banco.quandoConsultar("SELECT \"id_professor\"", linha("id_professor", 4L));
        banco.quandoConsultar("SELECT \"status\"", linha("status", "suspenso_denuncia"));

        // executar buscas com material existente e inexistente
        Long autor = conteudos.buscarIdProfessor(10L);
        String situacao = conteudos.buscarStatus(10L);
        banco.semResultado("SELECT \"id_professor\"").semResultado("SELECT \"status\"");
        Long semAutor = conteudos.buscarIdProfessor(99L);
        String semSituacao = conteudos.buscarStatus(99L);

        // verificar valores e nulos
        assertEquals(4L, autor);
        assertEquals("suspenso_denuncia", situacao);
        assertNull(semAutor);
        assertNull(semSituacao);
    }

    // testar material da turma este teste confere que material removido ou de outra turma nao pertence a turma
    @Test
    void confereSeConteudoPertenceATurma() throws SQLException {
        // preparar material na turma
        banco.quandoConsultar("AND ? = ANY(\"id_turma\")", linha("quantidade", 1));

        // executar conferencias
        boolean pertence = conteudos.conteudoPertenceTurma(10L, 1L);
        banco.quandoConsultar("AND ? = ANY(\"id_turma\")", linha("quantidade", 0));
        boolean naoPertence = conteudos.conteudoPertenceTurma(10L, 2L);

        // verificar respostas
        assertTrue(pertence);
        assertFalse(naoPertence);
    }

    // testar acesso ao material este teste confere a regra de aluno professor e outros perfis
    @Test
    void confereAcessoAoConteudoPorPerfil() throws SQLException {
        // preparar banco que libera o acesso
        banco.quandoConsultar("SELECT EXISTS", linha("exists", true));

        // executar conferencias por perfil
        boolean aluno = conteudos.usuarioTemAcessoAoConteudo(10L, "aluno", 7L);
        boolean professor = conteudos.usuarioTemAcessoAoConteudo(10L, "professor", 4L);
        boolean outro = conteudos.usuarioTemAcessoAoConteudo(10L, "admin", 1L);

        // verificar parametros de cada regra e recusa sem consulta para outros perfis
        assertTrue(aluno);
        assertTrue(professor);
        assertFalse(outro);
        assertEquals(List.of(10L, 7L), banco.comandos().get(0).valores());
        assertEquals(List.of(10L, 4L, 4L, 4L), banco.comandos().get(1).valores());
        assertEquals(2, banco.comandos().size());
    }

    // testar acesso negado este teste confere que o aluno fora da turma nao acessa o material
    @Test
    void negaAcessoQuandoBancoNaoEncontraVinculo() throws SQLException {
        // preparar banco sem vinculo
        banco.quandoConsultar("SELECT EXISTS", linha("exists", false));

        // executar conferencia
        boolean acesso = conteudos.usuarioTemAcessoAoConteudo(10L, "aluno", 8L);

        // verificar recusa
        assertFalse(acesso);
    }

    // testar acoes do professor este teste confere remocao reabertura e revisao feitas apenas pelo autor
    @Test
    void acoesDoProfessorSoAlteramConteudoProprio() throws SQLException {
        // preparar banco onde o professor nao e o autor
        banco.alteradasPadrao(0);

        // executar acoes
        boolean removeu = conteudos.removerConteudoProfessor(10L, 9L);
        boolean reabriu = conteudos.reabrir(10L, 9L, null);
        boolean pediuRevisao = conteudos.solicitarRevisao(10L, 9L);

        // verificar recusas e filtro pelo autor
        assertFalse(removeu);
        assertFalse(reabriu);
        assertFalse(pediuRevisao);
        assertEquals(List.of(10L, 9L), banco.ultimo("\"status\" = 'suspenso_denuncia'").valores());
        assertTrue(banco.comandos().get(1).valores().contains(null));
    }

    // testar acoes confirmadas este teste confere remocao reabertura e pedido de revisao aceitos
    @Test
    void acoesDoAutorSaoConfirmadas() throws SQLException {
        // preparar banco que altera uma linha

        // executar acoes
        boolean removeu = conteudos.removerConteudoProfessor(10L, 4L);
        boolean reabriu = conteudos.reabrir(10L, 4L, 1L);
        boolean pediuRevisao = conteudos.solicitarRevisao(10L, 4L);

        // verificar confirmacoes
        assertTrue(removeu);
        assertTrue(reabriu);
        assertTrue(pediuRevisao);
    }

    // testar alteracoes administrativas este teste confere troca de situacao exclusao logica e edicao do material
    @Test
    void alteraSituacaoExcluiEEditaConteudo() throws SQLException {
        // preparar material editado sem descricao
        ConteudoDTO editado = conteudo("Aula revisada");
        editado.setId(10L);
        editado.setDescricao(null);

        // executar alteracoes
        conteudos.atualizarStatus(10L, "ativo");
        conteudos.excluir(11L);
        conteudos.atualizar(editado);

        // verificar comandos enviados
        assertEquals(List.of("ativo", 10L), banco.comandos().get(0).valores());
        assertTrue(banco.comandos().get(1).sql().contains("'removido'"));
        assertEquals(List.of("Aula revisada", "", "https://www.youtube.com/watch?v=1", "30 min", 30, "link", 10L),
                banco.comandos().get(2).valores());
    }

    // testar progresso do aluno este teste confere marcar desmarcar e listar materiais concluidos
    @Test
    void marcaDesmarcaEListaConcluidos() throws SQLException {
        // preparar dois materiais concluidos
        banco.quandoConsultar("SELECT p.\"id_conteudo\"", linha("id_conteudo", 10L), linha("id_conteudo", 11L));

        // executar alteracoes e consulta
        conteudos.marcarConcluido(7L, 10L);
        conteudos.desmarcarConcluido(7L, 12L);
        Set<Long> concluidos = conteudos.listarConcluidosDoAluno(7L, 1L);

        // verificar comandos e materiais concluidos
        assertTrue(banco.comandos().get(0).sql().contains("ON CONFLICT"));
        assertTrue(banco.comandos().get(1).sql().contains("DELETE FROM public.\"PROGRESSO_CONTEUDO\""));
        assertEquals(Set.of(10L, 11L), concluidos);
    }

    // testar progresso de varios alunos este teste confere o agrupamento dos concluidos e dos minutos estudados
    @Test
    void agrupaProgressoDeVariosAlunos() throws SQLException {
        // preparar conclusoes e minutos de dois alunos
        banco.quandoConsultar("SELECT \"id_aluno\", \"id_conteudo\"",
                linha("id_aluno", 7L, "id_conteudo", 10L), linha("id_aluno", 7L, "id_conteudo", 11L), linha("id_aluno", 8L, "id_conteudo", 10L));
        banco.quandoConsultar("AS \"minutos\"", linha("id_aluno", 7L, "minutos", 90));

        // executar agrupamentos com alunos e sem alunos
        Map<Long, Set<Long>> concluidos = conteudos.listarConcluidosDosAlunos(List.of(7L, 8L));
        Map<Long, Integer> minutos = conteudos.minutosConcluidosRecentes(List.of(7L, 8L), 7, 30);
        boolean semAlunos = conteudos.listarConcluidosDosAlunos(List.of()).isEmpty()
                && conteudos.minutosConcluidosRecentes(List.of(), 7, 30).isEmpty();

        // verificar grupos e parametros dos minutos
        assertEquals(Set.of(10L, 11L), concluidos.get(7L));
        assertEquals(Set.of(10L), concluidos.get(8L));
        assertEquals(90, minutos.get(7L));
        assertEquals(30, banco.ultimo("AS \"minutos\"").valores().get(0));
        assertTrue(semAlunos);
    }

    // testar prazos encerrados este teste confere que so entram materiais com prazo vencido sem evento futuro e sem reabertura
    @Test
    void listaSoPrazosRealmenteEncerrados() throws SQLException {
        // preparar quatro materiais em situacoes diferentes
        LocalDateTime prazo = LocalDateTime.of(2026, 11, 5, 19, 0);
        banco.quandoConsultar("\"ultimo_prazo\"",
                linha("id", 10L, "ultimo_prazo", prazo, "tem_evento_futuro", false, "reaberto_em", null),
                linha("id", 11L, "ultimo_prazo", prazo, "tem_evento_futuro", true, "reaberto_em", null),
                linha("id", 12L, "ultimo_prazo", prazo, "tem_evento_futuro", false, "reaberto_em", prazo.plusDays(1)),
                linha("id", 13L, "ultimo_prazo", null, "tem_evento_futuro", false, "reaberto_em", null));

        // executar consulta com materiais e sem materiais
        Map<Long, LocalDateTime> encerrados = conteudos.listarPrazosEncerrados(List.of(10L, 11L, 12L, 13L), AGORA);
        Map<Long, LocalDateTime> vazio = conteudos.listarPrazosEncerrados(List.of(), AGORA);

        // verificar apenas o material vencido e sem reabertura
        assertEquals(Map.of(10L, prazo), encerrados);
        assertTrue(vazio.isEmpty());
    }

    // montar material do teste este metodo cria um link com turma autor e duracao
    private ConteudoDTO conteudo(String titulo) {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setIdTurma(1L);
        conteudo.setIdProfessor(2L);
        conteudo.setTitulo(titulo);
        conteudo.setTipo("link");
        conteudo.setUrl("https://www.youtube.com/watch?v=1");
        conteudo.setDuracaoEstimada("30 min");
        conteudo.setDuracaoEstimadaMin(30);
        conteudo.setPrioridade("alta");
        return conteudo;
    }
}
