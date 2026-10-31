package com.example.CampusLink.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static com.example.CampusLink.integracao.Navegador.campos;
import static org.assertj.core.api.Assertions.assertThat;

class TurmasIntegrationTest extends IntegracaoBase {

    private Navegador navegadorProfessor;
    private Usuario aluno;
    private Navegador navegadorAluno;
    private String nomeTurma;
    private Long idTurma;

    @BeforeEach
    void prepararTurma() {
        navegadorProfessor = logarComoProfessor(cadastrarProfessor("professor"));
        aluno = cadastrarAluno("aluno");
        navegadorAluno = logarComoAluno(aluno);
        nomeTurma = "Turma " + unico();
        idTurma = criarTurma(navegadorProfessor, nomeTurma);
        adicionarPessoa(navegadorProfessor, idTurma, aluno.email());
    }

    private int vinculosDoAluno(Usuario aluno, Long idTurma) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM \"ALUNO_TURMA\" WHERE id_aluno = ? AND id_turma = ?",
                Integer.class, idAluno(aluno), idTurma);
    }

    private int turmasComId(Long idTurma) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM \"TURMAS\" WHERE id = ?", Integer.class, idTurma);
    }

    @Test
    void listarTurmas_semSessao_deveRedirecionarParaLogin() {
        ResponseEntity<String> resposta = novoNavegador().get("/turmas");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/login");
    }

    @Test
    void listarTurmas_comoAlunoParticipante_deveRetornar200ComATurma() {
        ResponseEntity<String> resposta = navegadorAluno.get("/turmas");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(nomeTurma);
    }

    @Test
    void criarTurma_comoProfessor_deveRedirecionarEPersistirComProprietario() {
        String nome = "Nova turma " + unico();

        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas", "/criarTurma",
                campos("nomeTurma", nome, "descricao", "Descrição"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/turmas");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM "TURMAS" t JOIN "PROFESSOR_TURMA" pt ON pt.id_turma = t.id
                WHERE t.nome_turma = ? AND t.id_proprietario = pt.id_professor
                """, Integer.class, nome)).isEqualTo(1);
        assertThat(navegadorProfessor.get("/turmas").getBody()).contains(nome);
    }

    @Test
    void criarTurma_comNomeEmBranco_deveRetornar200ComMensagemENaoPersistir() {
        int antes = jdbcTemplate.queryForObject("SELECT count(*) FROM \"TURMAS\"", Integer.class);

        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas", "/criarTurma",
                campos("nomeTurma", "", "descricao", "Sem nome"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("O nome da turma é obrigatório");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM \"TURMAS\"", Integer.class)).isEqualTo(antes);
    }

    @Test
    void ambienteTurma_comoAlunoParticipante_deveRetornar200ComConteudoDoProfessor() {
        String titulo = "Conteúdo " + unico();
        criarConteudo(navegadorProfessor, idTurma, titulo, "60");

        ResponseEntity<String> resposta = navegadorAluno.get("/turmas/" + idTurma);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(nomeTurma, titulo);
    }

    @Test
    void ambienteTurma_comIdInexistente_deveRedirecionarParaTurmas() {
        ResponseEntity<String> resposta = navegadorAluno.get("/turmas/999999999");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/turmas");
    }

    @Test
    void criarConteudo_comoProfessor_deveRedirecionarEPersistir() {
        String titulo = "Conteúdo " + unico();

        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas/" + idTurma, "/turmas/" + idTurma + "/conteudos",
                campos("titulo", titulo, "descricao", "Descrição", "tipo", "texto", "duracaoEstimada", "45", "prioridade", "alta"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/turmas/" + idTurma);
        assertThat(jdbcTemplate.queryForMap("SELECT status, prioridade, ? = ANY(id_turma) AS da_turma FROM \"CONTEUDOS\" WHERE titulo = ?",
                idTurma, titulo))
                .containsEntry("status", "ativo")
                .containsEntry("prioridade", "alta")
                .containsEntry("da_turma", true);
    }

    @Test
    void criarConteudo_comIdDeTurmaNaoNumerico_deveRetornar400() {
        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas/" + idTurma, "/turmas/abc/conteudos",
                campos("titulo", "Inválido", "descricao", "x", "tipo", "texto", "duracaoEstimada", "30", "prioridade", "media"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("400");
    }

    @Test
    void editarConteudo_comoProfessor_deveAtualizarTitulo() {
        Long idConteudo = criarConteudo(navegadorProfessor, idTurma, "Original " + unico(), "60");
        String novoTitulo = "Editado " + unico();

        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas/" + idTurma,
                "/turmas/" + idTurma + "/conteudos/" + idConteudo + "/editar",
                campos("titulo", novoTitulo, "descricao", "Descrição", "tipo", "texto", "duracaoEstimada", "45", "prioridade", "media"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/turmas/" + idTurma);
        assertThat(jdbcTemplate.queryForObject("SELECT titulo FROM \"CONTEUDOS\" WHERE id = ?", String.class, idConteudo))
                .isEqualTo(novoTitulo);
    }

    @Test
    void excluirConteudo_comoProfessor_deveMarcarComoRemovido() {
        Long idConteudo = criarConteudo(navegadorProfessor, idTurma, "Para excluir " + unico(), "30");

        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas/" + idTurma,
                "/turmas/" + idTurma + "/conteudos/" + idConteudo + "/excluir", campos());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/turmas/" + idTurma);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM \"CONTEUDOS\" WHERE id = ?", String.class, idConteudo))
                .isEqualTo("removido");
    }

    @Test
    void excluirConteudo_comIdDeConteudoNaoNumerico_deveRetornar400() {
        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas/" + idTurma,
                "/turmas/" + idTurma + "/conteudos/abc/excluir", campos());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("400");
    }

    @Test
    void adicionarPessoas_semSessao_deveRedirecionarParaLogin() {
        Navegador visitante = novoNavegador();

        ResponseEntity<String> resposta = visitante.enviarFormulario("/loginAluno", "/adicionarPessoas",
                campos("id", idTurma.toString(), "emailPessoa", "qualquer@teste.com"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/login");
    }

    @Test
    void adicionarPessoas_comoAluno_naoDeveVincularNinguem() {
        Usuario outroAluno = cadastrarAluno("aluno");

        ResponseEntity<String> resposta = navegadorAluno.enviarFormulario("/turmas/" + idTurma, "/adicionarPessoas",
                campos("id", idTurma.toString(), "emailPessoa", outroAluno.email()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).doesNotContain("Pessoa adicionada com sucesso.");
        assertThat(vinculosDoAluno(outroAluno, idTurma)).isZero();
    }

    @Test
    void adicionarPessoas_comoProfessor_deveRetornar200EVincularAluno() {
        Usuario novoAluno = cadastrarAluno("aluno");

        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas/" + idTurma, "/adicionarPessoas",
                campos("id", idTurma.toString(), "emailPessoa", novoAluno.email()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Pessoa adicionada com sucesso.");
        assertThat(vinculosDoAluno(novoAluno, idTurma)).isEqualTo(1);
        assertThat(logarComoAluno(novoAluno).get("/turmas").getBody()).contains(nomeTurma);
    }

    @Test
    void adicionarPessoas_comEmailInexistente_deveRetornar200ComMensagemDeErro() {
        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas/" + idTurma, "/adicionarPessoas",
                campos("id", idTurma.toString(), "emailPessoa", "ninguem" + unico() + "@teste.com"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Não foi possível adicionar a pessoa informada.");
    }

    @Test
    void removerPessoas_comAlunoDaTurma_deveRetornar200EDesvincular() {
        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas/" + idTurma, "/removerPessoas",
                campos("id", idTurma.toString(), "emailPessoa", aluno.email()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Pessoa removida com sucesso.");
        assertThat(vinculosDoAluno(aluno, idTurma)).isZero();
        assertThat(navegadorAluno.get("/turmas").getBody()).doesNotContain(nomeTurma);
    }

    @Test
    void excluirTurma_comoProprietario_deveRemoverTurmaEVinculos() {
        ResponseEntity<String> resposta = navegadorProfessor.enviarFormulario("/turmas", "/excluirTurma",
                campos("id", idTurma.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/turmas");
        assertThat(turmasComId(idTurma)).isZero();
        assertThat(navegadorProfessor.get("/turmas/" + idTurma).getHeaders().getLocation()).hasPath("/turmas");
    }
}
