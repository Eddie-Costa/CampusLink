package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.controller.Geral.VerificarController;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.service.*;
import com.example.CampusLink.support.AdminFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAutenticacaoTest {
    static final String EMAIL = "admin-fluxos@example.com";
    @Mock usuarioDAO usuarios;
    @Mock TwoFactorService twoFactor;
    @Mock emailService emails;
    @Mock LoginAttemptService tentativas;
    loginAdminController controller;
    loginAdminDTO admin;
    MockHttpSession session;
    ExtendedModelMap model;
    @BeforeEach void preparar() {
        new LoginAttemptService().loginSucesso(EMAIL);
        controller = new loginAdminController(); admin = new loginAdminDTO(); admin.setEmail(EMAIL); admin.setSenha("SenhaTeste1!");
        session = new MockHttpSession(); model = new ExtendedModelMap();
        ReflectionTestUtils.setField(controller, "usuarioDAO", usuarios);
        ReflectionTestUtils.setField(controller, "twoFactorService", twoFactor);
        ReflectionTestUtils.setField(controller, "emailService", emails);
        ReflectionTestUtils.setField(controller, "loginAttemptService", tentativas);
    }
    @AfterEach void limpar() { new LoginAttemptService().loginSucesso(EMAIL); MDC.clear(); }
    private String login() throws Exception { return controller.fazerLogin(admin, new BeanPropertyBindingResult(admin, "admin"), model, session); }
    @Test void deveAbrirFormularioDeLogin() {
        assertEquals("Usuarios/Admin/loginAdmin", controller.loginPage(model)); assertInstanceOf(loginAdminDTO.class, model.get("admin"));
    }
    @Test void deveIniciarSegundoFatorSemAutenticarAntecipadamente() throws Exception {
        ReflectionTestUtils.setField(controller, "twoFactorEnabled", true);
        when(usuarios.QueryLoginUsuario("admin", EMAIL)).thenReturn(new BCryptPasswordEncoder().encode(admin.getSenha()));
        try (var gerador = mockStatic(TwoFactorService.class)) {
            gerador.when(() -> TwoFactorService.gerarCodigo(EMAIL)).thenReturn("123456");
            assertEquals("redirect:/verificarAdmin", login());
        }
        assertNull(session.getAttribute("usuarioLogado")); assertEquals(EMAIL, session.getAttribute("email2FA"));
        assertEquals("admin", session.getAttribute("tipoUsuario")); verify(emails).enviarCodigo(EMAIL, "123456"); verify(tentativas).loginSucesso(EMAIL);
    }
    @Test void deveConcluirLoginQuandoSegundoFatorDesabilitado() throws Exception {
        when(usuarios.QueryLoginUsuario("admin", EMAIL)).thenReturn(new BCryptPasswordEncoder().encode(admin.getSenha()));
        when(usuarios.buscarPorEmailAdmin(EMAIL)).thenReturn(admin);
        assertEquals("redirect:/admin/painel", login()); assertSame(admin, session.getAttribute("usuarioLogado"));
        assertEquals(900, session.getMaxInactiveInterval()); verifyNoInteractions(twoFactor, emails);
    }
    @Test void deveRecusarCredenciaisInvalidasEContabilizarTentativa() throws Exception {
        assertEquals("Usuarios/Admin/loginAdmin", login()); assertNull(session.getAttribute("usuarioLogado"));
        assertEquals("Email ou senha inválidos.", model.get("mensagemDeErro")); verify(tentativas).loginFalhou(EMAIL); verifyNoInteractions(twoFactor, emails);
    }
    @Test void deveBloquearSemConsultarCredenciais() throws Exception {
        var real = new LoginAttemptService(); for (int i = 0; i < 5; i++) real.loginFalhou(EMAIL);
        assertEquals("Usuarios/Admin/loginAdmin", login()); assertTrue(model.get("mensagemDeErro").toString().contains("bloqueada"));
        verify(usuarios, never()).QueryLoginUsuario(any(), any()); verifyNoInteractions(twoFactor, emails);
    }
    @Test void deveRecusarDadosInvalidosAntesDeConsultarSenha() throws Exception {
        var result = new BeanPropertyBindingResult(admin, "admin"); result.reject("invalido");
        assertEquals("Usuarios/Admin/loginAdmin", controller.fazerLogin(admin, result, model, session));
        assertNotNull(model.get("mensagemDeErro")); verify(usuarios, never()).QueryLoginUsuario(any(), any());
    }
    private VerificarController verificacao() {
        var verificar = new VerificarController(); ReflectionTestUtils.setField(verificar, "usuarioDAO", usuarios);
        ReflectionTestUtils.setField(verificar, "twoFactorService", twoFactor); return verificar;
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void deveValidarCodigoAntesDeCriarSessao(boolean valido) throws Exception {
        session.setAttribute("redirect", "Login"); session.setAttribute("tipoUsuario", "admin"); session.setAttribute("email2FA", EMAIL);
        when(twoFactor.validarCodigo(EMAIL, "123456")).thenReturn(valido);
        if (valido) when(usuarios.buscarPorEmailAdmin(EMAIL)).thenReturn(admin);
        assertEquals(valido ? "redirect:/admin/painel" : "Geral/verificar", verificacao().verificarCodigo("123456", session, model));
        if (valido) { assertSame(admin, session.getAttribute("usuarioLogado")); assertEquals(900, session.getMaxInactiveInterval()); }
        else { assertNull(session.getAttribute("usuarioLogado")); verify(usuarios, never()).buscarPorEmailAdmin(any()); }
    }
    @Test void deveExigirEmailParaAbrirVerificacaoAdmin() {
        assertEquals(AdminFixture.LOGIN, verificacao().paginaVerificacaoAdmin(session)); verifyNoInteractions(twoFactor);
    }
}
