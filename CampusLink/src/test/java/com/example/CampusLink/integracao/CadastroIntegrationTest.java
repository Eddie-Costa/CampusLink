package com.example.CampusLink.integracao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static com.example.CampusLink.integracao.Navegador.campos;
import static org.assertj.core.api.Assertions.assertThat;

class CadastroIntegrationTest extends IntegracaoBase {

    private int usuariosComEmail(String email) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM \"USUARIOS\" WHERE email = ?", Integer.class, email);
    }

    @Test
    void paginaCadastroAluno_semSessao_deveRetornar200ComFormulario() {
        ResponseEntity<String> resposta = novoNavegador().get("/cadastrarAluno");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Cadastrar Aluno", "name=\"rgm\"", "name=\"senha\"");
    }

    @Test
    void cadastrarAluno_comDadosValidos_deveRedirecionarEPersistirComSenhaCriptografada() {
        Usuario aluno = dadosDeUsuario("aluno");
        Navegador navegador = novoNavegador();

        ResponseEntity<String> envio = navegador.enviarFormulario("/cadastrarAluno", "/cadastrarAluno",
                formularioAluno(aluno, SENHA_VALIDA));
        ResponseEntity<String> confirmacao = navegador.get("/cadastrarAlunoVerificado");

        assertThat(envio.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(envio.getHeaders().getLocation()).hasPath("/cadastrarAlunoVerificado");
        assertThat(confirmacao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmacao.getBody()).contains("LOGIN ALUNO");

        Map<String, Object> gravado = jdbcTemplate.queryForMap("""
                SELECT u.nome, u.senha, u.perfil, u.status, a.rgm
                FROM "USUARIOS" u JOIN "ALUNOS" a ON a.id_usuario = u.id WHERE u.email = ?
                """, aluno.email());
        assertThat(gravado.get("nome")).isEqualTo(aluno.nome().toLowerCase());
        assertThat(gravado.get("rgm")).isEqualTo(aluno.identificador());
        assertThat(gravado.get("perfil")).isEqualTo("ALUNOS");
        assertThat(gravado.get("status")).isEqualTo(true);
        assertThat((String) gravado.get("senha")).isNotEqualTo(SENHA_VALIDA).startsWith("$2");
    }

    @Test
    void cadastrarAluno_comSenhaFraca_deveRetornar200ComMensagemENaoPersistir() {
        Usuario aluno = dadosDeUsuario("aluno");

        ResponseEntity<String> resposta = novoNavegador().enviarFormulario("/cadastrarAluno", "/cadastrarAluno",
                formularioAluno(aluno, "senhafraca"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Dados inválidos. Verifique o formulário.");
        assertThat(usuariosComEmail(aluno.email())).isZero();
    }

    @Test
    void cadastrarAluno_comEmailDuplicado_deveRetornar200ComMensagemENaoDuplicar() {
        Usuario existente = cadastrarAluno("aluno");
        Usuario novo = dadosDeUsuario("aluno");
        Usuario comEmailRepetido = new Usuario(novo.nome(), existente.email(), novo.identificador(), novo.telefone());
        Navegador navegador = novoNavegador();

        navegador.enviarFormulario("/cadastrarAluno", "/cadastrarAluno", formularioAluno(comEmailRepetido, SENHA_VALIDA));
        ResponseEntity<String> resposta = navegador.get("/cadastrarAlunoVerificado");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Não foi possível cadastrar.", "Email já cadastrado.");
        assertThat(usuariosComEmail(existente.email())).isEqualTo(1);
    }

    @Test
    void cadastrarAluno_semTokenCsrf_deveRetornar403ENaoPersistir() {
        Usuario aluno = dadosDeUsuario("aluno");

        ResponseEntity<String> resposta = novoNavegador().enviarSemCsrf("/cadastrarAluno", formularioAluno(aluno, SENHA_VALIDA));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).contains("403");
        assertThat(usuariosComEmail(aluno.email())).isZero();
    }

    @Test
    void cadastroAlunoVerificado_semPassarPeloFormulario_deveRedirecionarParaCadastro() {
        ResponseEntity<String> resposta = novoNavegador().get("/cadastrarAlunoVerificado");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/cadastrarAluno");
    }

    @Test
    void cadastrarAluno_eDepoisLogar_deveAutenticarONovoAluno() {
        Usuario aluno = cadastrarAluno("aluno");

        ResponseEntity<String> resposta = novoNavegador().enviarFormulario("/loginAluno", "/loginAluno",
                campos("email", aluno.email(), "senha", SENHA_VALIDA));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/home");
    }
}
