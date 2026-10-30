package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import com.example.CampusLink.service.emailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminGerenciarConteudosIntegrationTest {

    private static final String SENHA = "SenhaTeste1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate banco;

    // substitui somente o envio externo de email e mantem a validacao real do codigo
    @MockitoBean
    private emailService emails;

    private RestClient cliente;
    private RestClient professor;
    private long turmaId;
    private String identificador;
    private String csrfProfessor;

    @BeforeEach
    void prepararCliente() {
        cliente = criarCliente();
        identificador = UUID.randomUUID().toString();
        turmaId = prepararConteudos();
        professor = criarCliente();
        csrfProfessor = entrar(professor, "/loginProfessor", "professor-" + identificador + "@example.com", "/verificarProfessor", "/home");
        entrar(cliente, "/admin/login", "admin-" + identificador + "@example.com", "/verificarAdmin", "/admin/painel");
    }

    @Test
    void listar_comAdministrador_deveExibirConteudoDaTurma() {
        // confere o conteudo cadastrado pelo professor na listagem administrativa
        String titulo = "Material " + identificador;
        long conteudoId = criarConteudo(professor, turmaId, titulo);

        ResponseEntity<String> resposta = cliente.get().uri(uri -> uri.path("/admin/conteudos")
                .queryParam("busca", identificador).build()).retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType()).isNotNull();
        assertThat(resposta.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(resposta.getBody()).contains(titulo, "ID: " + conteudoId, "Professor de teste", "Turma de teste",
                "Material criado pelo teste de integracao", "data-status=\"ativo\"", "data-total=\"1\"");
    }

    private RestClient criarCliente() {
        HttpClient http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return RestClient.builder().baseUrl("http://localhost:" + port).requestFactory(new JdkClientHttpRequestFactory(http))
                .defaultStatusHandler(status -> true, (request, response) -> {}).build();
    }

    private long prepararConteudos() {
        // cria apenas as contas e a turma de apoio, pois este fluxo so consulta conteudos
        String senhaHash = new BCryptPasswordEncoder().encode(SENHA);
        Long adminId = banco.queryForObject("""
                INSERT INTO public."USUARIOS" (nome, email, senha, perfil)
                VALUES ('Admin de teste', ?, ?, 'ADMINISTRADOR') RETURNING id
                """, Long.class, "admin-" + identificador + "@example.com", senhaHash);
        banco.update("INSERT INTO public.\"ADMINISTRADOR\" (id_usuario) VALUES (?)", adminId);
        Long usuarioId = banco.queryForObject("""
                INSERT INTO public."USUARIOS" (nome, email, senha, perfil)
                VALUES ('Professor de teste', ?, ?, 'PROFESSORES') RETURNING id
                """, Long.class, "professor-" + identificador + "@example.com", senhaHash);
        Long professorId = banco.queryForObject("INSERT INTO public.\"PROFESSORES\" (id_usuario) VALUES (?) RETURNING id",
                Long.class, usuarioId);
        return banco.queryForObject("""
                INSERT INTO public."TURMAS" (nome_turma, id_proprietario, id_professores)
                VALUES ('Turma de teste', ?, ARRAY[?::bigint]) RETURNING id
                """, Long.class, professorId, professorId);
    }

    private String entrar(RestClient usuario, String rota, String email, String verificacao, String destino) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("email", email);
        formulario.add("senha", SENHA);
        formulario.add("_csrf", buscarCsrf(usuario, rota));
        ResponseEntity<String> login = usuario.post().uri(rota).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario).retrieve().toEntity(String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(login.getHeaders().getLocation()).isNotNull();
        assertThat(login.getHeaders().getLocation().getPath()).isEqualTo(verificacao);

        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(emails).enviarCodigo(eq(email), codigo.capture(), eq(FinalidadeCodigoEmail.ACESSO));
        MultiValueMap<String, String> confirmacao = new LinkedMultiValueMap<>();
        confirmacao.add("codigo", codigo.getValue());
        String csrf = buscarCsrf(usuario, verificacao);
        confirmacao.add("_csrf", csrf);
        ResponseEntity<String> resposta = usuario.post().uri("/verificar").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(confirmacao).retrieve().toEntity(String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).isNotNull();
        assertThat(resposta.getHeaders().getLocation().getPath()).isEqualTo(destino);
        return csrf;
    }

    private long criarConteudo(RestClient professor, long turmaId, String titulo) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("titulo", titulo);
        formulario.add("descricao", "Material criado pelo teste de integracao");
        formulario.add("duracaoEstimada", "30");
        formulario.add("prioridade", "media");
        formulario.add("_csrf", csrfProfessor);
        ResponseEntity<String> resposta = professor.post().uri("/turmas/" + turmaId + "/conteudos")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario).retrieve().toEntity(String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).isNotNull();
        assertThat(resposta.getHeaders().getLocation().getPath()).isEqualTo("/turmas/" + turmaId);
        Long id = banco.queryForObject("SELECT id FROM public.\"CONTEUDOS\" WHERE titulo = ?", Long.class, titulo);
        assertThat(id).isNotNull();
        return id;
    }

    private String buscarCsrf(RestClient usuario, String rota) {
        ResponseEntity<String> pagina = usuario.get().uri(rota).retrieve().toEntity(String.class);
        assertThat(pagina.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pagina.getBody()).isNotNull();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(pagina.getBody());
        assertThat(token.find()).as("token CSRF presente no formulario").isTrue();
        return token.group(1);
    }

}
