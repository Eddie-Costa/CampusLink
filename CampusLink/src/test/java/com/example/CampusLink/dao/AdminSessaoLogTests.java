// verificacao do historico administrativo exercita o ciclo de acesso em tabelas temporarias
package com.example.CampusLink.dao;

import com.example.CampusLink.config.SessaoLogFilter;
import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.controller.Geral.LogoutController;
import com.example.CampusLink.controller.Geral.VerificarController;
import com.example.CampusLink.controller.Usuarios.Admin.AdminUsuariosController;
import com.example.CampusLink.controller.Usuarios.Admin.loginAdminController;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import com.example.CampusLink.repository.AdminUsuarioRepository;
import com.example.CampusLink.repository.UsuarioRepository;
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

// testes das sessoes administrativas esta classe confere login acoes erros saida e consolidacao
@SpringBootTest(classes = AdminSessaoLogTests.BancoTeste.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AdminSessaoLogTests {
    // conexao de teste do historico esta classe fornece o acesso ao banco usado nos cenarios isolados
    @Configuration(proxyBeanMethods = false)
    static class BancoTeste {
        // preparar conexao do teste de historico este metodo usa o endereco configurado para acessar o banco
        @Bean
        DataSource dataSource(@Value("${spring.datasource.url}") String url) {
            return new DriverManagerDataSource(url);
        }
    }

    private static final String EMAIL = "admin-auditoria@example.com";
    private static final String SENHA = "SenhaTeste1!";
    @Autowired DataSource dataSource;
    private Connection conn;
    private UsuarioRepository usuarios;
    private SessaoLogListener listener;
    private MockHttpSession session;
    private final LoginAttemptService tentativas = new LoginAttemptService();

    // preparar historico isolado este metodo cria tabelas temporarias e direciona as gravacoes do teste para elas
    @BeforeEach
    void preparar() throws Exception {
        MDC.clear();
        tentativas.loginSucesso(EMAIL);
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);

        for (String tabela : new String[]{"LOGS_SESSOES", "LOGS_EVENTOS"}) {
            sql("CREATE TEMP TABLE \"" + tabela + "\" (LIKE public.\"" + tabela
                    + "\" INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING IDENTITY INCLUDING INDEXES)");

            // preparar sequencias de teste consulta as colunas que usam numeracao automatica
            try (var stmt = conn.prepareStatement("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = ? AND column_default LIKE 'nextval(%'
                    """)) {
                stmt.setString(1, tabela);
                // criar sequencias temporarias monta contadores isolados para as tabelas do teste
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
                    // preservar conexao de teste impede que o repositorio feche a conexao compartilhada
                    if (method.getName().equals("close")) return null;
                    // isolar consultas de teste troca a tabela publica pela tabela temporaria na consulta
                    if (method.getName().equals("prepareStatement")) {
                        args[0] = ((String) args[0]).replace("public.", "pg_temp.");
                    }
                    // executar consulta do teste repassa a chamada e preserva a causa de uma falha
                    try {
                        return method.invoke(conn, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        DataSource temporario = mock(DataSource.class);
        when(temporario.getConnection()).thenReturn(isolada);
        usuarios = spy(new UsuarioRepository());
        ReflectionTestUtils.setField(usuarios, "dataSource", temporario);
        listener = new SessaoLogListener(usuarios);

        session = new MockHttpSession() // sessao simulada com aviso esta classe notifica o encerramento antes de invalidar o acesso de teste
        {
            // simular encerramento de sessao este metodo notifica o registro de historico antes de invalidar a sessao de teste
            @Override
            public void invalidate() {
                listener.sessionDestroyed(new HttpSessionEvent(this));
                super.invalidate();
            }
        };
        listener.sessionCreated(new HttpSessionEvent(session));
    }

    // limpar historico temporario este metodo desfaz gravacoes pendentes remove tabelas temporarias e fecha a conexao
    @AfterEach
    void limpar() throws Exception {
        MDC.clear();
        tentativas.loginSucesso(EMAIL);
        new TwoFactorService().limparCodigo(EMAIL);
        // limpar dados do teste inicia a limpeza quando existe uma conexao
        if (conn != null) {
            // encerrar teste de sessao descarta os dados temporarios e garante o fechamento da conexao
            try {
                // desfazer transacao do teste cancela uma transacao ainda aberta
                if (!conn.getAutoCommit()) conn.rollback();
                conn.setAutoCommit(true);
                sql("DISCARD TEMP");
            } finally {
                conn.close();
            }
        }
    }

    // testar historico completo do administrador este teste confere login acao erro logout e mensagem posterior
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loginAcaoErroELogoutDoAdminGeramHistoricoCompleto(boolean doisFatores) throws Exception {
        autenticar(doisFatores);
        UUID sessaoId = (UUID) session.getAttribute("sessaoLogId");
        AdminUsuarioRepository adminRepository = mock(AdminUsuarioRepository.class);
        usuarioAdminDTO administrador = new usuarioAdminDTO();
        administrador.setIdUsuario(41L);
        administrador.setPerfil("Administrador");
        administrador.setStatus(true);
        when(adminRepository.buscarAdministradorPorEmail(EMAIL)).thenReturn(administrador);
        AdminUsuariosController controller = new AdminUsuariosController(adminRepository, usuarios);
        when(adminRepository.atualizarStatusUsuario(77L, false, EMAIL)).thenReturn(true);
        requisicao("/admin/usuarios/77/desativar", () -> {
            assertEquals(EMAIL, MDC.get("admin"));
            controller.desativarUsuario(77L, session, new RedirectAttributesModelMap());
        });
        when(adminRepository.atualizarStatusUsuario(77L, true, EMAIL)).thenThrow(new SQLException("falha simulada de ativação"));
        requisicao("/admin/usuarios/77/ativar", () -> controller.ativarUsuario(77L, session, new RedirectAttributesModelMap()));

        LogoutController logout = new LogoutController();
        ReflectionTestUtils.setField(logout, "UsuarioRepository", usuarios);
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


        usuarios.InserirLogsNoBD(sessaoId, "INFO", "emailService", "enviarCodigo", null,
                "Envio assíncrono concluído após logout", null, null);
        assertTrue(historicoEncerrado(sessaoId).contains("Envio assíncrono concluído após logout"));
    }

    // testar expiracao administrativa este teste confere a consolidacao da sessao sem contexto de pedido ativo
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

    // autenticar administrador no teste este metodo simula login com ou sem codigo por email
    private void autenticar(boolean doisFatores) throws Exception {
        loginAdminDTO admin = new loginAdminDTO();
        admin.setEmail(EMAIL);
        admin.setSenha(SENHA);
        doReturn(new BCryptPasswordEncoder(10).encode(SENHA)).when(usuarios).QueryLoginUsuario("admin", EMAIL);
        doReturn(admin).when(usuarios).buscarPorEmailAdmin(EMAIL);
        loginAdminController login = new loginAdminController();
        emailService emails = mock(emailService.class);
        ReflectionTestUtils.setField(login, "UsuarioRepository", usuarios);
        ReflectionTestUtils.setField(login, "loginAttemptService", tentativas);
        ReflectionTestUtils.setField(login, "twoFactorService", new TwoFactorService());
        ReflectionTestUtils.setField(login, "emailService", emails);
        ReflectionTestUtils.setField(login, "twoFactorEnabled", doisFatores);
        requisicao("/gestao/login-admin", () -> assertEquals(
                doisFatores ? "redirect:/verificarAdmin" : "redirect:/admin/painel",
                login.fazerLogin(admin, new BeanPropertyBindingResult(admin, "admin"), new ExtendedModelMap(), session)));
        // testar segundo fator confirma o codigo enviado antes de validar o acesso administrativo
        if (doisFatores) {
            ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
            verify(emails).enviarCodigo(eq(EMAIL), codigo.capture(), eq(FinalidadeCodigoEmail.ACESSO));
            VerificarController verificar = new VerificarController();
            ReflectionTestUtils.setField(verificar, "UsuarioRepository", usuarios);
            ReflectionTestUtils.setField(verificar, "twoFactorService", new TwoFactorService());
            requisicao("/verificar", () -> assertEquals("redirect:/admin/painel",
                    verificar.verificarCodigo(codigo.getValue(), session, new ExtendedModelMap())));
        }
        assertSame(admin, session.getAttribute("usuarioLogado"));
    }

    // simular pedido com historico este metodo executa a acao pelo filtro e confere a limpeza do contexto
    private void requisicao(String caminho, Acao acao) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(caminho);
        request.setSession(session);
        new SessaoLogFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            // executar requisicao do teste transforma falhas da acao em erro da requisicao
            try {
                acao.executar();
            } catch (Exception e) {
                throw new ServletException(e);
            }
        });
        assertNull(MDC.get("admin"));
        assertNull(MDC.get("sessaoLogId"));
    }

    // conferir historico encerrado este metodo valida titular datas situacao e ausencia de sessao duplicada
    private String historicoEncerrado(UUID sessaoId) throws SQLException {
        // consultar sessao encerrada busca o historico temporario pelo codigo da sessao
        try (var stmt = conn.prepareStatement("SELECT * FROM pg_temp.\"LOGS_SESSOES\" WHERE sessao_log_id = ?")) {
            stmt.setObject(1, sessaoId);
            // conferir historico encerrado valida os dados da sessao e a ausencia de duplicacao
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

    // conferir ordem das ocorrencias este metodo verifica se todas as mensagens aparecem em ordem no historico
    private void verificarTodosEventosNoHistorico(UUID sessaoId, String historico) throws SQLException {
        // consultar eventos do historico busca os eventos temporarios na ordem de registro
        try (var stmt = conn.prepareStatement("SELECT id, mensagem FROM pg_temp.\"LOGS_EVENTOS\" WHERE sessao_log_id = ? ORDER BY data_hora, id")) {
            stmt.setObject(1, sessaoId);
            // conferir ordem do historico valida a presenca e a ordem de cada evento
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

    // executar comando temporario este metodo roda o texto recebido na conexao isolada do teste
    private void sql(String texto) throws SQLException {
        // executar comando de teste abre e fecha a consulta usada na preparacao dos dados
        try (var stmt = conn.createStatement()) {
            stmt.execute(texto);
        }
    }

    // acao simulada esta interface representa uma tarefa de teste que pode apresentar falha
    @FunctionalInterface
    private interface Acao {
        // executar acao do cenario este metodo representa a tarefa que sera realizada no pedido simulado
        void executar() throws Exception;
    }
}
