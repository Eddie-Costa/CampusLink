package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.ChamadoRespostaDTO;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import com.example.CampusLink.repository.ChamadoRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChamadosIntegrationTest {

    private static final String SENHA = "SenhaTeste1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate banco;

    @Autowired
    private ChamadoRepository chamados;

    // evita enviar emails reais, mantendo login, codigo e banco funcionando
    @MockitoBean
    private emailService emails;

    private RestClient usuario;
    private RestClient admin;
    private String csrfUsuario;
    private String csrfAdmin;
    private String identificador;
    private Long usuarioId;
    private Long adminId;

    @BeforeEach
    void prepararUsuarios() {
        identificador = UUID.randomUUID().toString();
        String senhaHash = new BCryptPasswordEncoder().encode(SENHA);
        usuarioId = criarConta("Professor de teste", "professor-", "PROFESSORES", senhaHash);
        adminId = criarConta("Admin de teste", "admin-", "ADMINISTRADOR", senhaHash);
        banco.update("INSERT INTO public.\"PROFESSORES\" (id_usuario) VALUES (?)", usuarioId);
        banco.update("INSERT INTO public.\"ADMINISTRADOR\" (id_usuario) VALUES (?)", adminId);
        when(emails.enviarEmailChamado(any(ChamadoDTO.class))).thenReturn(true);
        usuario = criarCliente();
        admin = criarCliente();
        csrfUsuario = entrar(usuario, "/loginProfessor", "professor-", "/verificarProfessor", "/home");
        csrfAdmin = entrar(admin, "/admin/login", "admin-", "/verificarAdmin", "/admin/painel");
    }

    @Test
    void abrir_comDadosValidos_devePersistirEExibirChamado() {
        // confere o mesmo cadastro pelas duas rotas disponiveis ao usuario
        for (String rota : new String[]{"/ajuda", "/ajuda/chamado"}) {
            verificarPagina(usuario, rota, "Canal de ajuda", "name=\"assunto\"", "name=\"descricao\"");
            String assunto = "Ajuda " + identificador + rota;

            long id = abrirChamado(rota, assunto);

            ChamadoDTO salvo = chamados.buscarDoUsuario(id, usuarioId);
            assertThat(salvo).isNotNull();
            assertThat(salvo.getAssunto()).isEqualTo(assunto);
            assertThat(salvo.getDescricao()).isEqualTo("Preciso de ajuda na turma");
            assertThat(salvo.getStatus()).isEqualTo("aberto");
            verificarPagina(usuario, "/meus-chamados", assunto, "/meus-chamados/" + id);
            verificarPagina(usuario, "/meus-chamados/" + id, assunto, "Preciso de ajuda na turma", "Aberto");
        }
    }

    @Test
    void responder_comUsuarioTitular_deveGravarMensagensNaConversa() {
        // acompanha as mensagens enviadas pelo formulario e pelo chat
        long id = abrirChamado();
        String caminho = "/meus-chamados/" + id;

        ResponseEntity<String> formulario = postar(usuario, caminho + "/responder", csrfUsuario,
                formulario("resposta", "Detalhe pelo formulario")).toEntity(String.class);
        verificarRedirect(formulario, caminho);
        verificarPagina(usuario, caminho, "Detalhe pelo formulario");
        ResponseEntity<ChamadoDTO> resposta = postar(usuario, caminho + "/mensagens", csrfUsuario,
                formulario("resposta", "Detalhe pelo chat")).toEntity(ChamadoDTO.class);

        verificarConversa(resposta, id, "aberto", "Detalhe pelo formulario", "Detalhe pelo chat");
        verificarConversa(conversa(usuario, caminho), id, "aberto", "Detalhe pelo formulario", "Detalhe pelo chat");
        assertThat(chamados.listarRespostas(id)).extracting(ChamadoRespostaDTO::getIdUsuario)
                .containsExactly(usuarioId, usuarioId);
    }

    @Test
    void responder_comAdministrador_deveExibirRespostasAoUsuario() {
        // a resposta administrativa deve aparecer tambem para quem abriu o chamado
        long id = abrirChamado();
        String caminho = "/admin/chamados/" + id;
        verificarPagina(admin, "/admin/chamados", "Ajuda " + identificador, caminho);
        verificarPagina(admin, caminho, "Ajuda " + identificador, "Preciso de ajuda na turma");

        ResponseEntity<String> formulario = postar(admin, caminho + "/responder", csrfAdmin,
                formulario("resposta", "Orientacao pelo formulario")).toEntity(String.class);
        verificarRedirect(formulario, caminho);
        verificarPagina(admin, caminho, "Orientacao pelo formulario", "Respondido");
        ResponseEntity<ChamadoDTO> resposta = postar(admin, caminho + "/mensagens", csrfAdmin,
                formulario("resposta", "Orientacao pelo chat")).toEntity(ChamadoDTO.class);

        verificarConversa(resposta, id, "respondido", "Orientacao pelo formulario", "Orientacao pelo chat");
        verificarConversa(conversa(usuario, "/meus-chamados/" + id), id, "respondido",
                "Orientacao pelo formulario", "Orientacao pelo chat");
        assertThat(chamados.listarRespostas(id)).extracting(ChamadoRespostaDTO::getIdAdminUsuario)
                .containsExactly(adminId, adminId);
    }

    @Test
    void concluir_comRespostaAdmin_devePersistirEncerramento() {
        // confirma a conclusao e preserva o historico para consulta
        long id = abrirChamado();
        responderComoAdmin(id);

        ResponseEntity<String> resposta = postar(admin, "/admin/chamados/" + id + "/concluir", csrfAdmin,
                new LinkedMultiValueMap<>()).toEntity(String.class);

        verificarRedirect(resposta, "/admin/chamados/" + id);
        verificarPagina(admin, "/admin/chamados/" + id, "Concluído", "Orientacao do admin");
        ChamadoDTO salvo = chamados.buscarPorId(id, false);
        assertThat(salvo.getStatus()).isEqualTo("concluido");
        assertThat(salvo.getConcluidoEm()).isNotNull();
        assertThat(salvo.getIdAdminConclusao()).isEqualTo(adminId);
        verificarConversa(conversa(usuario, "/meus-chamados/" + id), id, "concluido", "Orientacao do admin");
    }

    @Test
    void enviarMensagem_comTextoVazio_deveRetornar400SemGravar() {
        // uma mensagem vazia nao pode entrar no historico
        long id = abrirChamado();

        ResponseEntity<Map> resposta = postar(usuario, "/meus-chamados/" + id + "/mensagens", csrfUsuario,
                formulario("resposta", "   ")).toEntity(Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).containsEntry("mensagem", "Preencha o campo: Mensagem.");
        assertThat(chamados.listarRespostas(id)).isEmpty();
    }

    @Test
    void enviarMensagem_comTextoMuitoLongo_deveRetornar400SemGravar() {
        // respeita o limite de tamanho da resposta administrativa
        long id = abrirChamado();

        ResponseEntity<Map> resposta = postar(admin, "/admin/chamados/" + id + "/mensagens", csrfAdmin,
                formulario("resposta", "a".repeat(5001))).toEntity(Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).containsEntry("mensagem", "A resposta deve ter no máximo 5000 caracteres.");
        assertThat(chamados.listarRespostas(id)).isEmpty();
        assertThat(chamados.buscarPorId(id, false).getStatus()).isEqualTo("aberto");
    }

    @Test
    void conversa_comIdInexistente_deveRetornar404() {
        // consulta um id que nao pode ser gerado pela tabela de chamados
        String caminho = "/meus-chamados/-1/conversa";

        ResponseEntity<Map> resposta = usuario.get().uri(caminho).accept(MediaType.APPLICATION_JSON)
                .retrieve().toEntity(Map.class);

        verificarErro(resposta, 404, "Not Found", caminho);
    }

    @Test
    void enviarMensagem_comChamadoConcluido_deveRetornar409SemAlterarHistorico() {
        // um atendimento encerrado nao aceita novas mensagens
        long id = abrirChamado();
        responderComoAdmin(id);
        verificarRedirect(postar(admin, "/admin/chamados/" + id + "/concluir", csrfAdmin,
                new LinkedMultiValueMap<>()).toEntity(String.class), "/admin/chamados/" + id);

        ResponseEntity<Map> resposta = postar(usuario, "/meus-chamados/" + id + "/mensagens", csrfUsuario,
                formulario("resposta", "Nova duvida")).toEntity(Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).containsEntry("mensagem", "Este chamado já foi concluído. Abra outro chamado se precisar de ajuda.");
        assertThat(chamados.listarRespostas(id)).extracting(ChamadoRespostaDTO::getResposta).containsExactly("Orientacao do admin");
        assertThat(chamados.buscarPorId(id, false).getStatus()).isEqualTo("concluido");
    }

    @Test
    void conversaAdmin_semLogin_deveRetornar401() {
        // exige login para consultar a conversa administrativa
        long id = abrirChamado();
        String caminho = "/admin/chamados/" + id + "/conversa";

        ResponseEntity<Map> resposta = criarCliente().get().uri(caminho).accept(MediaType.APPLICATION_JSON)
                .retrieve().toEntity(Map.class);

        verificarErro(resposta, 401, "Unauthorized", caminho);
    }

    @Test
    void conversaAdmin_comProfessor_deveRetornar403() {
        // ser dono do chamado nao concede acesso a rota administrativa
        long id = abrirChamado();
        String caminho = "/admin/chamados/" + id + "/conversa";

        ResponseEntity<Map> resposta = usuario.get().uri(caminho).accept(MediaType.APPLICATION_JSON)
                .retrieve().toEntity(Map.class);

        verificarErro(resposta, 403, "Forbidden", caminho);
    }

    @Test
    void conversaAdmin_comAdministrador_deveRetornar200ComDados() {
        // permite consultar os dados do chamado com o perfil correto
        long id = abrirChamado();

        ResponseEntity<ChamadoDTO> resposta = conversa(admin, "/admin/chamados/" + id);

        verificarConversa(resposta, id, "aberto");
        assertThat(resposta.getBody().getAssunto()).isEqualTo("Ajuda " + identificador);
        assertThat(resposta.getBody().getDescricao()).isEqualTo("Preciso de ajuda na turma");
        assertThat(resposta.getBody().getIdUsuario()).isEqualTo(usuarioId);
        assertThat(resposta.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    private Long criarConta(String nome, String prefixo, String perfil, String senhaHash) {
        return banco.queryForObject("""
                INSERT INTO public."USUARIOS" (nome, email, senha, perfil)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class, nome, prefixo + identificador + "@example.com", senhaHash, perfil);
    }

    private RestClient criarCliente() {
        HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return RestClient.builder().baseUrl("http://localhost:" + port).requestFactory(new JdkClientHttpRequestFactory(http))
                .defaultStatusHandler(status -> true, (request, response) -> {}).build();
    }

    private String entrar(RestClient cliente, String rota, String prefixo, String verificacao, String destino) {
        String email = prefixo + identificador + "@example.com";
        MultiValueMap<String, String> dados = formulario("email", email);
        dados.add("senha", SENHA);
        verificarRedirect(postar(cliente, rota, buscarCsrf(cliente, rota), dados).toEntity(String.class), verificacao);
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(emails).enviarCodigo(eq(email), codigo.capture(), eq(FinalidadeCodigoEmail.ACESSO));
        String csrf = buscarCsrf(cliente, verificacao);
        verificarRedirect(postar(cliente, "/verificar", csrf, formulario("codigo", codigo.getValue()))
                .toEntity(String.class), destino);
        return csrf;
    }

    private String buscarCsrf(RestClient cliente, String rota) {
        ResponseEntity<String> pagina = cliente.get().uri(rota).retrieve().toEntity(String.class);
        assertThat(pagina.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pagina.getBody()).isNotNull();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(pagina.getBody());
        assertThat(token.find()).as("token CSRF presente no formulario").isTrue();
        return token.group(1);
    }

    private MultiValueMap<String, String> formulario(String campo, String valor) {
        MultiValueMap<String, String> dados = new LinkedMultiValueMap<>();
        dados.add(campo, valor);
        return dados;
    }

    private RestClient.ResponseSpec postar(RestClient cliente, String rota, String csrf, MultiValueMap<String, String> dados) {
        dados.add("_csrf", csrf);
        return cliente.post().uri(rota).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(dados).retrieve();
    }

    private long abrirChamado() {
        return abrirChamado("/ajuda", "Ajuda " + identificador);
    }

    private long abrirChamado(String rota, String assunto) {
        MultiValueMap<String, String> dados = formulario("assunto", assunto);
        dados.add("descricao", "Preciso de ajuda na turma");
        ResponseEntity<String> resposta = postar(usuario, rota, csrfUsuario, dados).toEntity(String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).isNotNull();
        String destino = resposta.getHeaders().getLocation().getPath();
        assertThat(destino).matches("/meus-chamados/[0-9]+");
        return Long.parseLong(destino.substring(destino.lastIndexOf('/') + 1));
    }

    private void responderComoAdmin(long id) {
        ResponseEntity<ChamadoDTO> resposta = postar(admin, "/admin/chamados/" + id + "/mensagens", csrfAdmin,
                formulario("resposta", "Orientacao do admin")).toEntity(ChamadoDTO.class);
        verificarConversa(resposta, id, "respondido", "Orientacao do admin");
    }

    private ResponseEntity<ChamadoDTO> conversa(RestClient cliente, String caminho) {
        return cliente.get().uri(caminho + "/conversa").accept(MediaType.APPLICATION_JSON).retrieve().toEntity(ChamadoDTO.class);
    }

    private void verificarConversa(ResponseEntity<ChamadoDTO> resposta, long id, String status, String... mensagens) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().getId()).isEqualTo(id);
        assertThat(resposta.getBody().getStatus()).isEqualTo(status);
        assertThat(resposta.getBody().getRespostas()).extracting(ChamadoRespostaDTO::getResposta).containsExactly(mensagens);
    }

    private void verificarPagina(RestClient cliente, String rota, String... textos) {
        ResponseEntity<String> resposta = cliente.get().uri(rota).retrieve().toEntity(String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(textos);
    }

    private void verificarRedirect(ResponseEntity<String> resposta, String destino) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).isNotNull();
        assertThat(resposta.getHeaders().getLocation().getPath()).isEqualTo(destino);
    }

    private void verificarErro(ResponseEntity<Map> resposta, int status, String erro, String caminho) {
        assertThat(resposta.getStatusCode().value()).isEqualTo(status);
        assertThat(resposta.getBody()).containsEntry("status", status).containsEntry("error", erro).containsEntry("path", caminho);
    }
}
