package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.ArquivoDAO;
import com.example.CampusLink.service.SupabaseStorageService;
import com.example.CampusLink.service.emailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
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
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegracaoHttpBase {

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ArquivoDAO arquivos;

    @MockitoBean
    protected SupabaseStorageService storage;

    @MockitoBean
    protected emailService email;

    protected RestClient professor;
    protected RestClient aluno;
    protected RestClient outroAluno;
    protected long turmaId;
    protected long conteudoId;
    protected long alunoId;
    protected String nomeArquivo;
    protected String storagePath;
    protected static final byte[] DADOS = "%PDF-1.4 material de teste".getBytes(StandardCharsets.UTF_8);
    private final List<RestClient> clientes = new ArrayList<>();

    @AfterEach
    void encerrarSessoes() {
        for (RestClient cliente : clientes) {
            var resposta = postar(cliente, "/logout", formulario());
            assertThat(resposta.getStatusCode().is3xxRedirection()).isTrue();
        }
    }

    @BeforeEach
    void prepararDadosEClientes() throws Exception {
        String identificador = UUID.randomUUID().toString();
        String emailProfessor = "professor-" + identificador + "@example.com";
        String emailAluno = "aluno-" + identificador + "@example.com";
        String emailOutroAluno = "outro-" + identificador + "@example.com";
        long professorId = criarUsuario(emailProfessor, "professor");
        alunoId = criarUsuario(emailAluno, "aluno");
        long outroAlunoId = criarUsuario(emailOutroAluno, "aluno");
        turmaId = jdbc.queryForObject("""
                INSERT INTO "TURMAS" (nome_turma, descricao, id_proprietario, id_professores, id_alunos)
                VALUES (?, 'Turma dos testes', ?, ARRAY[?::bigint], ARRAY[?::bigint, ?::bigint]) RETURNING id
                """, Long.class, "Turma " + identificador, professorId, professorId, alunoId, outroAlunoId);
        jdbc.update("INSERT INTO \"ALUNO_TURMA\" (id_aluno, id_turma) VALUES (?, ?), (?, ?)",
                alunoId, turmaId, outroAlunoId, turmaId);
        jdbc.update("INSERT INTO \"PROFESSOR_TURMA\" (id_professor, id_turma) VALUES (?, ?)", professorId, turmaId);

        professor = fazerLogin(emailProfessor, "Professor");
        aluno = fazerLogin(emailAluno, "Aluno");
        outroAluno = fazerLogin(emailOutroAluno, "Aluno");
        String titulo = "Material " + identificador;
        var resposta = postar(professor, "/turmas/" + turmaId + "/conteudos",
                formulario("titulo", titulo, "descricao", "Material da turma", "duracaoEstimada", "30", "prioridade", "media"));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/turmas/" + turmaId);
        conteudoId = jdbc.queryForObject("SELECT id FROM \"CONTEUDOS\" WHERE titulo = ?", Long.class, titulo);
        nomeArquivo = "material-" + identificador + ".pdf";
        storagePath = "testes/" + nomeArquivo;
        when(storage.upload(any())).thenReturn(storagePath);
        when(storage.download(storagePath)).thenReturn(DADOS);
    }

    protected RestClient novoCliente() {
        HttpClient http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        RestClient cliente = RestClient.builder().baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(http))
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
        clientes.add(cliente);
        return cliente;
    }

    private long criarUsuario(String endereco, String perfil) {
        String senha = new BCryptPasswordEncoder().encode("SenhaTeste1!");
        long usuarioId = jdbc.queryForObject("""
                INSERT INTO "USUARIOS" (nome, email, telefone, datanasc, senha, perfil)
                VALUES ('Usuario de teste', ?, '11999990000', '2000-01-01', ?, ?) RETURNING id
                """, Long.class, endereco, senha, perfil.equals("aluno") ? "ALUNOS" : "PROFESSORES");
        if (perfil.equals("aluno")) {
            return jdbc.queryForObject("INSERT INTO \"ALUNOS\" (id_usuario, rgm) VALUES (?, ?) RETURNING id",
                    Long.class, usuarioId, UUID.randomUUID().toString());
        }
        return jdbc.queryForObject("INSERT INTO \"PROFESSORES\" (id_usuario, matricula) VALUES (?, ?) RETURNING id",
                Long.class, usuarioId, UUID.randomUUID().toString());
    }

    private RestClient fazerLogin(String endereco, String perfil) throws Exception {
        RestClient cliente = novoCliente();
        var login = postar(cliente, "/login" + perfil, formulario("email", endereco, "senha", "SenhaTeste1!"));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(login.getHeaders().getLocation()).hasPath("/verificar" + perfil);
        var codigo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).enviarCodigo(eq(endereco), codigo.capture());
        var verificacao = postar(cliente, "/verificar", formulario("codigo", codigo.getValue()));
        assertThat(verificacao.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(verificacao.getHeaders().getLocation()).hasPath("/home");
        return cliente;
    }

    protected String csrf(RestClient cliente) {
        var pagina = cliente.get().uri("/ResetPassword_Verification").retrieve().toEntity(String.class);
        // O token vem do formulario real renderizado pelo Spring Security.
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(pagina.getBody());
        assertThat(token.find()).as("Token CSRF no HTML").isTrue();
        return token.group(1);
    }

    protected MultiValueMap<String, String> formulario(String... campos) {
        var dados = new LinkedMultiValueMap<String, String>();
        for (int i = 0; i < campos.length; i += 2) {
            dados.add(campos[i], campos[i + 1]);
        }
        return dados;
    }

    protected ResponseEntity<String> postar(RestClient cliente, String rota, MultiValueMap<String, String> dados) {
        return cliente.post().uri(rota).header("X-CSRF-TOKEN", csrf(cliente))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
                .body(dados).retrieve().toEntity(String.class);
    }

    protected ResponseEntity<String> enviarArquivo() {
        var dados = new LinkedMultiValueMap<String, Object>();
        var recurso = new ByteArrayResource(DADOS) {
            @Override
            public String getFilename() { return nomeArquivo; }
        };
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        dados.add("arquivo", new HttpEntity<>(recurso, headers));
        dados.add("idConteudo", Long.toString(conteudoId));
        return professor.post().uri("/arquivos/upload").header("X-CSRF-TOKEN", csrf(professor))
                .contentType(MediaType.MULTIPART_FORM_DATA).body(dados).retrieve().toEntity(String.class);
    }

    protected long criarArquivo() throws Exception {
        var resposta = enviarArquivo();
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/arquivos");
        conferirPagina(professor, "/arquivos", "Arquivo enviado com sucesso.");
        return arquivos.buscarMaisRecentePorConteudo(conteudoId).getId();
    }

    protected String rotaDenuncia() {
        return "/turmas/" + turmaId + "/conteudos/" + conteudoId + "/denunciar";
    }

    protected void conferirPagina(RestClient cliente, String rota, String texto) {
        var pagina = cliente.get().uri(rota).retrieve().toEntity(String.class);
        assertThat(pagina.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pagina.getBody()).contains(texto);
    }
}
