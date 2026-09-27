package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.service.LoginAttemptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminLoginLogTests {
    private static final String EMAIL = "admin-login-log@example.com";
    private final usuarioDAO usuarios = mock(usuarioDAO.class);
    private final LoginAttemptService tentativas = new LoginAttemptService();
    private final loginAdminController controller = new loginAdminController();
    private final loginAdminDTO admin = new loginAdminDTO();

    @BeforeEach
    void preparar() {
        tentativas.loginSucesso(EMAIL);
        admin.setEmail(EMAIL);
        admin.setSenha("SenhaIncorreta1!");
        ReflectionTestUtils.setField(controller, "usuarioDAO", usuarios);
        ReflectionTestUtils.setField(controller, "loginAttemptService", tentativas);
    }

    @AfterEach
    void limpar() {
        tentativas.loginSucesso(EMAIL);
    }

    @Test
    void dadosInvalidosSaoAuditadosSemConsultarSenha() throws Exception {
        BeanPropertyBindingResult validacao = new BeanPropertyBindingResult(admin, "admin");
        validacao.reject("invalido");
        assertEquals("Usuarios/Admin/loginAdmin", controller.fazerLogin(
                admin, validacao, new ExtendedModelMap(), new MockHttpSession()));
        verify(usuarios, never()).QueryLoginUsuario(anyString(), anyString());
        verificarAviso("Dados de login inválidos", 1);
    }

    @Test
    void registraFalhasBloqueioENovaTentativaDuranteBloqueio() throws Exception {
        for (int i = 0; i < 6; i++) {
            controller.fazerLogin(admin, new BeanPropertyBindingResult(admin, "admin"),
                    new ExtendedModelMap(), new MockHttpSession());
        }
        verify(usuarios, times(5)).QueryLoginUsuario("admin", EMAIL);
        verificarAviso("Credenciais inválidas", 5);
        verificarAviso("Conta bloqueada por excesso de tentativas", 1);
        verificarAviso("Conta bloqueada para administrador", 1);
    }

    private void verificarAviso(String mensagem, int quantidade) throws Exception {
        verify(usuarios, times(quantidade)).InserirLogsNoBD(isNull(), eq("WARN"), anyString(), eq("fazerLogin"),
                isNull(), contains(mensagem), isNull(), isNull());
    }
}
