package com.example.CampusLink.integracao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.MultiValueMap;
import org.testcontainers.postgresql.PostgreSQLContainer;


import static com.example.CampusLink.integracao.Navegador.campos;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegracaoBase {

    protected static final String SENHA_VALIDA = "Senha@123";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("db/schema-test.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int porta;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected record Usuario(String nome, String email, String identificador, String telefone) {
    }

    protected Navegador novoNavegador() {
        return new Navegador(porta);
    }

    protected static String unico() {
        return String.valueOf(System.nanoTime());
    }

    protected Usuario dadosDeUsuario(String prefixo) {
        String sufixo = unico();
        String digitos = sufixo.substring(sufixo.length() - 9);
        return new Usuario(prefixo + " " + sufixo, prefixo + sufixo + "@teste.campuslink.com", digitos, "11" + digitos);
    }

    protected MultiValueMap<String, String> formularioAluno(Usuario usuario, String senha) {
        return campos("rgm", usuario.identificador(), "nome", usuario.nome(), "email", usuario.email(),
                "telefone", usuario.telefone(), "dataNasc", "2000-01-15", "senha", senha);
    }

    protected MultiValueMap<String, String> formularioProfessor(Usuario usuario, String senha) {
        return campos("matricula", usuario.identificador(), "nome", usuario.nome(), "email", usuario.email(),
                "telefone", usuario.telefone(), "dataNasc", "1985-06-20", "senha", senha);
    }

    protected Usuario cadastrarAluno(String prefixo) {
        Usuario aluno = dadosDeUsuario(prefixo);
        Navegador navegador = novoNavegador();
        navegador.enviarFormulario("/cadastrarAluno", "/cadastrarAluno", formularioAluno(aluno, SENHA_VALIDA));
        assertThat(navegador.get("/cadastrarAlunoVerificado").getStatusCode()).isEqualTo(HttpStatus.OK);
        return aluno;
    }

    protected Usuario cadastrarProfessor(String prefixo) {
        Usuario professor = dadosDeUsuario(prefixo);
        Navegador navegador = novoNavegador();
        navegador.enviarFormulario("/cadastrarProfessor", "/cadastrarProfessor", formularioProfessor(professor, SENHA_VALIDA));
        assertThat(navegador.get("/cadastrarProfessorVerificado").getStatusCode()).isEqualTo(HttpStatus.OK);
        return professor;
    }

    protected Navegador logarComoAluno(Usuario aluno) {
        Navegador navegador = novoNavegador();
        ResponseEntity<String> resposta = navegador.enviarFormulario("/loginAluno", "/loginAluno",
                campos("email", aluno.email(), "senha", SENHA_VALIDA));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        return navegador;
    }

    protected Navegador logarComoProfessor(Usuario professor) {
        Navegador navegador = novoNavegador();
        ResponseEntity<String> resposta = navegador.enviarFormulario("/loginProfessor", "/loginProfessor",
                campos("email", professor.email(), "senha", SENHA_VALIDA));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        return navegador;
    }

    protected Long criarTurma(Navegador professor, String nomeTurma) {
        professor.enviarFormulario("/turmas", "/criarTurma", campos("nomeTurma", nomeTurma, "descricao", "Turma de teste"));
        return jdbcTemplate.queryForObject("SELECT id FROM \"TURMAS\" WHERE nome_turma = ?", Long.class, nomeTurma);
    }

    protected void adicionarPessoa(Navegador professor, Long idTurma, String email) {
        professor.enviarFormulario("/turmas/" + idTurma, "/adicionarPessoas",
                campos("id", idTurma.toString(), "emailPessoa", email));
    }

    protected Long criarConteudo(Navegador professor, Long idTurma, String titulo, String duracao) {
        professor.enviarFormulario("/turmas/" + idTurma, "/turmas/" + idTurma + "/conteudos",
                campos("titulo", titulo, "descricao", "Conteúdo de teste", "tipo", "texto",
                        "duracaoEstimada", duracao, "prioridade", "media"));
        return jdbcTemplate.queryForObject("SELECT id FROM \"CONTEUDOS\" WHERE titulo = ?", Long.class, titulo);
    }

    protected Long idAluno(Usuario aluno) {
        return jdbcTemplate.queryForObject("""
                SELECT a.id FROM "ALUNOS" a JOIN "USUARIOS" u ON u.id = a.id_usuario WHERE u.email = ?
                """, Long.class, aluno.email());
    }
}
