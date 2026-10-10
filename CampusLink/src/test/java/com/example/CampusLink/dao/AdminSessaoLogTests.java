package com.example.CampusLink.dao;

import com.example.CampusLink.config.SessaoLogFilter;
import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.controller.Geral.LogoutController;
import com.example.CampusLink.controller.Geral.VerificarController;
import com.example.CampusLink.controller.Usuarios.Admin.AdminUsuariosController;
import com.example.CampusLink.controller.Usuarios.Admin.loginAdminController;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.service.LoginAttemptService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@org.springframework.test.context.ContextConfiguration(initializers = com.example.CampusLink.support.BancoTesteInitializer.class)
@SpringBootTest(classes = AdminSessaoLogTests.BancoTeste.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AdminSessaoLogTests {
    @Configuration(proxyBeanMethods = false)
    static class BancoTeste {
        @Bean
        DataSource dataSource(@Value("${spring.datasource.url}") String url) {
            return new DriverManagerDataSource(url);
        }
    }

    private static final String EMAIL = "admin-auditoria@example.com";
    private static final String SENHA = "SenhaTeste1!";
    @Autowired DataSource dataSource;
    private Connection conn;
    private usuarioDAO usuarios;
    private SessaoLogListener listener;
    private MockHttpSession session;
    private final LoginAttemptService tentativas = new LoginAttemptService();

    @BeforeEach
    void preparar() throws Exception {
        MDC.clear();
        tentativas.loginSucesso(EMAIL);
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);
        // Copia somente a estrutura real dos logs. Nenhum dado de produção é alterado.
        for (String tabela : new String[]{"LOGS_SESSOES", "LOGS_EVENTOS"}) {
            sql("CREATE TEMP TABLE \"" + tabela + "\" (LIKE public.\"" + tabela
                    + "\" INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING IDENTITY INCLUDING INDEXES)");
            // Defaults serial copiados não podem consumir sequências de produção.
            try (var stmt = conn.prepareStatement("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = ? AND column_default LIKE 'nextval(%'
                    """)) {
                stmt.setString(1, tabela);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String coluna = rs.getString(1).replace("\"", "\"\"");
                        String sequencia = "teste_" + tabela + "_" + coluna;
                        sql("CREATE TEMP SEQUENCE \"" + sequencia + "\"");
                        sql("ALTER TABLE pg_temp.\"" + tabela + "\" ALTER COLUMN \"" + coluna
                                + "\" SET DEFAULT nextval('pg_temp.\"" + sequencia + "\"'::regclass)");
                    }
                }
            }
        }
        sql("CREATE TEMP TABLE \"USUARIOS\" (id bigint PRIMARY KEY, email text NOT NULL)");
        sql("INSERT INTO pg_temp.\"USUARIOS\" VALUES (41, '" + EMAIL + "')");

        Connection isolada = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("close")) return null;
                    if (method.getName().equals("prepareStatement")) {
                        args[0] = ((String) args[0]).replace("public.", "pg_temp.");
                    }
                    try {
                        return method.invoke(conn, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        DataSource temporario = mock(DataSource.class);
        when(temporario.getConnection()).thenReturn(isolada);
        usuarios = spy(new usuarioDAO());
        ReflectionTestUtils.setField(usuarios, "dataSource", temporario);
        listener = new SessaoLogListener(usuarios);
        // MockHttpSession não notifica listeners; reproduzimos a notificação do servlet container.
        session = new MockHttpSession() {
            @Override
            public void invalidate() {
                listener.sessionDestroyed(new HttpSessionEvent(this));
                super.invalidate();
            }
        };
        listener.sessionCreated(new HttpSessionEvent(session));
    }

    @AfterEach
    void limpar() throws Exception {
        MDC.clear();
        tentativas.loginSucesso(EMAIL);
        new TwoFactorService().limparCodigo(EMAIL);
        if (conn != null) {
            try {
                if (!conn.getAutoCommit()) conn.rollback();
                conn.setAutoCommit(true);
                sql("DISCARD TEMP");
            } finally {
                conn.close();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loginAcaoErroELogoutDoAdminGeramHistoricoCompleto(boolean doisFatores) throws Exception {
        autenticar(doisFatores);
        UUID sessaoId = (UUID) session.getAttribute("sessaoLogId");
        AdminUsuarioDAO adminDAO = mock(AdminUsuarioDAO.class);
        AdminUsuariosController controller = new AdminUsuariosController(adminDAO, usuarios);
        when(adminDAO.atualizarStatusUsuario(77L, false)).thenReturn(true);
        requisicao("/admin/usuarios/77/desativar", () -> {
            assertEquals(EMAIL, MDC.get("admin"));
            controller.desativarUsuario(77L, session, new RedirectAttributesModelMap());
        });
        when(adminDAO.atualizarStatusUsuario(77L, true)).thenThrow(new SQLException("falha simulada de ativação"));
        requisicao("/admin/usuarios/77/ativar", () -> controller.ativarUsuario(77L, session, new RedirectAttributesModelMap()));

        LogoutController logout = new LogoutController();
        ReflectionTestUtils.setField(logout, "usuarioDAO", usuarios);
        requisicao("/logout", () -> logout.logout(session));
        assertTrue(session.isInvalid());
        String historico = historicoEncerrado(sessaoId);
        assertTrue(historico.contains("Login concluído. perfil=admin"));
        assertTrue(historico.contains("doisFatores=" + doisFatores));
        assertTrue(historico.contains("Usuário desativado pelo administrador. usuarioId=77"));
        assertTrue(historico.contains("[ERROR]"));
        assertTrue(historico.contains("java.sql.SQLException: falha simulada de ativação"));
        assertTrue(historico.contains("Sessão encerrada para o usuário " + EMAIL));
        assertTrue(historico.contains("Historico consolidado"));
        assertFalse(historico.contains(SENHA));
        verificarTodosEventosNoHistorico(sessaoId, historico);

        // E-mails podem terminar depois do logout: também devem entrar no histórico consolidado.
        usuarios.InserirLogsNoBD(sessaoId, "INFO", "emailService", "enviarCodigo", null,
                "Envio assíncrono concluído após logout", null, null);
        assertTrue(historicoEncerrado(sessaoId).contains("Envio assíncrono concluído após logout"));
    }

    @Test
    void timeoutDoAdminConsolidaSessaoSemMdcAtivo() throws Exception {
        autenticar(false);
        UUID sessaoId = (UUID) session.getAttribute("sessaoLogId");
        assertNull(MDC.get("sessaoLogId"));
        session.invalidate();
        String historico = historicoEncerrado(sessaoId);
        assertTrue(historico.contains("Login concluído. perfil=admin"));
        verificarTodosEventosNoHistorico(sessaoId, historico);
    }

    private void autenticar(boolean doisFatores) throws Exception {
        loginAdminDTO admin = new loginAdminDTO();
        admin.setEmail(EMAIL);
        admin.setSenha(SENHA);
        doReturn(new BCryptPasswordEncoder(10).encode(SENHA)).when(usuarios).QueryLoginUsuario("admin", EMAIL);
        doReturn(admin).when(usuarios).buscarPorEmailAdmin(EMAIL);
        loginAdminController login = new loginAdminController();
        emailService emails = mock(emailService.class);
        ReflectionTestUtils.setField(login, "usuarioDAO", usuarios);
        ReflectionTestUtils.setField(login, "loginAttemptService", tentativas);
        ReflectionTestUtils.setField(login, "twoFactorService", new TwoFactorService());
        ReflectionTestUtils.setField(login, "emailService", emails);
        ReflectionTestUtils.setField(login, "twoFactorEnabled", doisFatores);
        requisicao("/gestao/login-admin", () -> assertEquals(
                doisFatores ? "redirect:/verificarAdmin" : "redirect:/admin/painel",
                login.fazerLogin(admin, new BeanPropertyBindingResult(admin, "admin"), new ExtendedModelMap(), session)));
        if (doisFatores) {
            ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
            verify(emails).enviarCodigo(eq(EMAIL), codigo.capture());
            VerificarController verificar = new VerificarController();
            ReflectionTestUtils.setField(verificar, "usuarioDAO", usuarios);
            ReflectionTestUtils.setField(verificar, "twoFactorService", new TwoFactorService());
            requisicao("/verificar", () -> assertEquals("redirect:/admin/painel",
                    verificar.verificarCodigo(codigo.getValue(), session, new ExtendedModelMap())));
        }
        assertSame(admin, session.getAttribute("usuarioLogado"));
    }

    private void requisicao(String caminho, Acao acao) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(caminho);
        request.setSession(session);
        new SessaoLogFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            try {
                acao.executar();
            } catch (Exception e) {
                throw new ServletException(e);
            }
        });
        assertNull(MDC.get("admin"));
        assertNull(MDC.get("sessaoLogId"));
    }

    private String historicoEncerrado(UUID sessaoId) throws SQLException {
        try (var stmt = conn.prepareStatement("SELECT * FROM pg_temp.\"LOGS_SESSOES\" WHERE sessao_log_id = ?")) {
            stmt.setObject(1, sessaoId);
            try (var rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "A sessão deve existir em LOGS_SESSOES");
                assertEquals(41, rs.getLong("usuario_id"), "A sessão deve pertencer ao administrador");
                assertEquals("ENCERRADA", rs.getString("status"));
                assertNotNull(rs.getTimestamp("inicio"));
                assertNotNull(rs.getTimestamp("fim"));
                assertNotNull(rs.getTimestamp("historico_gerado_em"));
                String historico = rs.getString("log_completo");
                assertNotNull(historico);
                assertFalse(historico.isBlank());
                assertFalse(rs.next(), "A consolidação não deve duplicar a sessão");
                return historico;
            }
        }
    }

    private void verificarTodosEventosNoHistorico(UUID sessaoId, String historico) throws SQLException {
        try (var stmt = conn.prepareStatement("SELECT id, mensagem FROM pg_temp.\"LOGS_EVENTOS\" WHERE sessao_log_id = ? ORDER BY data_hora, id")) {
            stmt.setObject(1, sessaoId);
            try (var rs = stmt.executeQuery()) {
                int posicaoAnterior = -1;
                while (rs.next()) {
                    int posicao = historico.indexOf("eventoId=" + rs.getString("id") + " ");
                    assertTrue(posicao > posicaoAnterior, "Eventos devem aparecer em ordem no histórico");
                    assertTrue(historico.contains(rs.getString("mensagem")));
                    posicaoAnterior = posicao;
                }
            }
        }
    }

    private void sql(String texto) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute(texto);
        }
    }

    @FunctionalInterface
    private interface Acao {
        void executar() throws Exception;
    }
}
