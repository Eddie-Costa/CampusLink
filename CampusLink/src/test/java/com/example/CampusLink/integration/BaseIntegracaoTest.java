package com.example.CampusLink.integration;

import com.example.CampusLink.service.emailService;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Base dos testes de integracao: sobe a aplicacao inteira em porta aleatoria, com um
 * PostgreSQL real (Testcontainers). Apenas o envio de e-mail e simulado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BaseIntegracaoTest {

    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"[^>]*?value=\"([^\"]+)\"");
    private static final String SENHA_PADRAO = "Teste@1234";

    @Value("${local.server.port}")
    private int porta;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected emailService emailService;

    protected record Conta(long idUsuario, long idPerfil, String email, String senha) {}

    protected record Conteudo(long id, String titulo) {}

    // ---------- navegador: guarda cookies, nao segue redirects e nao lanca excecao em 4xx/5xx ----------

    protected class Navegador {
        private final RestClient http;
        private String tokenCsrf;

        Navegador() {
            HttpClient cliente = HttpClient.newBuilder()
                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            this.http = RestClient.builder()
                    .baseUrl("http://localhost:" + porta)
                    .requestFactory(new JdkClientHttpRequestFactory(cliente))
                    .defaultStatusHandler(status -> true, (requisicao, resposta) -> { })
                    .build();
        }

        ResponseEntity<String> get(String caminho) {
            ResponseEntity<String> resposta = http.get().uri(caminho).retrieve().toEntity(String.class);
            guardarTokenCsrf(resposta.getBody());
            return resposta;
        }

        ResponseEntity<String> post(String caminho, Map<String, String> campos) {
            return enviarFormulario(caminho, campos, true);
        }

        ResponseEntity<String> postSemCsrf(String caminho, Map<String, String> campos) {
            return enviarFormulario(caminho, campos, false);
        }

        private ResponseEntity<String> enviarFormulario(String caminho, Map<String, String> campos, boolean comCsrf) {
            MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
            campos.forEach(formulario::add);
            if (comCsrf && tokenCsrf != null) {
                formulario.add("_csrf", tokenCsrf);
            }
            return http.post().uri(caminho)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario)
                    .retrieve()
                    .toEntity(String.class);
        }

        private void guardarTokenCsrf(String html) {
            if (html == null) {
                return;
            }
            Matcher campo = CAMPO_CSRF.matcher(html);
            if (campo.find()) {
                tokenCsrf = campo.group(1);
            }
        }
    }

    // ---------- login completo (senha + codigo 2FA capturado do e-mail simulado) ----------

    protected void entrar(Navegador navegador, String rotaLogin, Conta conta, String destinoFinal) {
        navegador.get(rotaLogin);

        ResponseEntity<String> login = navegador.post(rotaLogin,
                Map.of("email", conta.email(), "senha", conta.senha()));
        assertEquals(302, login.getStatusCode().value(), "a senha correta deve levar a tela do 2FA");

        ResponseEntity<String> verificacao = navegador.post("/verificar",
                Map.of("codigo", ultimoCodigoEnviadoPara(conta.email())));
        assertRedireciona(verificacao, destinoFinal);
    }

    private String ultimoCodigoEnviadoPara(String email) {
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).enviarCodigo(eq(email), codigo.capture());
        List<String> codigos = codigo.getAllValues();
        return codigos.get(codigos.size() - 1);
    }

    protected static void assertRedireciona(ResponseEntity<String> resposta, String caminhoEsperado) {
        assertEquals(302, resposta.getStatusCode().value(), "deveria ser um redirecionamento");
        assertEquals(caminhoEsperado, resposta.getHeaders().getLocation().getPath());
    }

    // ---------- dados de teste (nomes unicos para um teste nao interferir no outro) ----------

    protected String sufixoUnico() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected Conta criarAdmin() {
        String sufixo = sufixoUnico();
        String email = "admin-" + sufixo + "@teste.com";
        long idUsuario = inserirUsuario("ADMINISTRADOR", email);
        Long idAdmin = jdbc.queryForObject(
                "INSERT INTO public.\"ADMINISTRADOR\" (id_usuario, matricula) VALUES (?, ?) RETURNING id",
                Long.class, idUsuario, "A" + sufixo);
        return new Conta(idUsuario, idAdmin, email, SENHA_PADRAO);
    }

    protected Conta criarProfessor() {
        String sufixo = sufixoUnico();
        String email = "prof-" + sufixo + "@teste.com";
        long idUsuario = inserirUsuario("PROFESSORES", email);
        Long idProfessor = jdbc.queryForObject(
                "INSERT INTO public.\"PROFESSORES\" (id_usuario, matricula) VALUES (?, ?) RETURNING id",
                Long.class, idUsuario, "P" + sufixo);
        return new Conta(idUsuario, idProfessor, email, SENHA_PADRAO);
    }

    protected Conta criarAluno() {
        String sufixo = sufixoUnico();
        String email = "aluno-" + sufixo + "@teste.com";
        long idUsuario = inserirUsuario("ALUNOS", email);
        Long idAluno = jdbc.queryForObject(
                "INSERT INTO public.\"ALUNOS\" (id_usuario, rgm) VALUES (?, ?) RETURNING id",
                Long.class, idUsuario, "R" + sufixo);
        return new Conta(idUsuario, idAluno, email, SENHA_PADRAO);
    }

    protected long criarTurma(Conta professor) {
        return jdbc.queryForObject(
                "INSERT INTO public.\"TURMAS\" (nome_turma, id_professores, id_proprietario) "
                        + "VALUES (?, ARRAY[?::bigint], ?) RETURNING id",
                Long.class, "Turma " + sufixoUnico(), professor.idPerfil(), professor.idPerfil());
    }

    protected Conteudo criarConteudo(Conta professor, String status, boolean revisaoSolicitada) {
        String titulo = "Conteudo " + sufixoUnico();
        long idTurma = criarTurma(professor);
        Long id = jdbc.queryForObject(
                "INSERT INTO public.\"CONTEUDOS\" (id_turma, id_professor, titulo, descricao, tipo, url, "
                        + "duracao_estimada, prioridade, status, revisao_solicitada, revisao_solicitada_em) "
                        + "VALUES (ARRAY[?::bigint], ?, ?, 'Descricao de teste', 'link', 'https://exemplo.com', "
                        + "'30', 'baixa', ?, ?, CASE WHEN ? THEN now() ELSE NULL END) RETURNING id",
                Long.class, idTurma, professor.idPerfil(), titulo, status, revisaoSolicitada, revisaoSolicitada);
        return new Conteudo(id, titulo);
    }

    protected void criarDenunciaPendente(Conteudo conteudo, Conta professor) {
        Conta aluno = criarAluno();
        jdbc.update("INSERT INTO public.\"DENUNCIAS\" (id_conteudo, id_aluno, id_professor, motivo, status) "
                        + "VALUES (?, ?, ?, 'Conteudo inadequado', 'pendente')",
                conteudo.id(), aluno.idPerfil(), professor.idPerfil());
    }

    private long inserirUsuario(String perfil, String email) {
        String senhaCriptografada = new BCryptPasswordEncoder(10).encode(SENHA_PADRAO);
        return jdbc.queryForObject(
                "INSERT INTO public.\"USUARIOS\" (nome, email, telefone, datanasc, senha, perfil, status) "
                        + "VALUES (?, ?, '11999990000', DATE '1990-01-01', ?, ?, TRUE) RETURNING id",
                Long.class, "Teste " + email, email, senhaCriptografada, perfil);
    }
}