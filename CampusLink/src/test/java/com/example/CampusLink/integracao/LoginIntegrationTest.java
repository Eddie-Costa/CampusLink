package com.example.CampusLink.integracao;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static com.example.CampusLink.integracao.Navegador.campos;
import static org.assertj.core.api.Assertions.assertThat;

class LoginIntegrationTest extends IntegracaoBase {

    @Test
    void paginaLoginAluno_semSessao_deveRetornar200ComFormulario() {
        ResponseEntity<String> resposta = novoNavegador().get("/loginAluno");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("LOGIN ALUNO", "name=\"email\"", "name=\"senha\"", "name=\"_csrf\"");
    }

    @Test
    void loginAluno_comCredenciaisValidas_deveRetornar302ParaHomeEAutenticarSessao() {
        Usuario aluno = cadastrarAluno("aluno");
        Navegador navegador = novoNavegador();

        ResponseEntity<String> resposta = navegador.enviarFormulario("/loginAluno", "/loginAluno",
                campos("email", aluno.email(), "senha", SENHA_VALIDA));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/home");
        assertThat(navegador.get("/turmas").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void loginAluno_comSenhaErrada_deveRetornar200ComMensagemESemSessao() {
        Usuario aluno = cadastrarAluno("aluno");
        Navegador navegador = novoNavegador();

        ResponseEntity<String> resposta = navegador.enviarFormulario("/loginAluno", "/loginAluno",
                campos("email", aluno.email(), "senha", "Errada@123"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Email ou senha inválidos.");
        assertThat(navegador.get("/turmas").getHeaders().getLocation()).hasPath("/login");
    }

    @Test
    void loginAluno_aposCincoSenhasErradas_deveBloquearMesmoComSenhaCorreta() {
        Usuario aluno = cadastrarAluno("aluno");
        Navegador navegador = novoNavegador();
        ResponseEntity<String> quintaTentativa = null;
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            quintaTentativa = navegador.enviarFormulario("/loginAluno", "/loginAluno",
                    campos("email", aluno.email(), "senha", "Errada@123"));
        }

        ResponseEntity<String> resposta = navegador.enviarFormulario("/loginAluno", "/loginAluno",
                campos("email", aluno.email(), "senha", SENHA_VALIDA));

        assertThat(quintaTentativa.getBody()).contains("Conta bloqueada por 10 minutos.");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Conta bloqueada por muitas tentativas. Tente mais tarde.");
    }

    @Test
    void loginAluno_semTokenCsrf_deveRetornar403() {
        Usuario aluno = cadastrarAluno("aluno");

        ResponseEntity<String> resposta = novoNavegador().enviarSemCsrf("/loginAluno",
                campos("email", aluno.email(), "senha", SENHA_VALIDA));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).contains("403");
    }

    @Test
    void paginaLoginAluno_comSessaoAtiva_deveRedirecionarParaHome() {
        Navegador navegador = logarComoAluno(cadastrarAluno("aluno"));

        ResponseEntity<String> resposta = navegador.get("/loginAluno");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/home");
    }
}
