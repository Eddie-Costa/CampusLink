// verificacao do login administrativo exercita o registro de falhas
package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.repository.UsuarioRepository;
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

// testes das tentativas de administrador esta classe confere validacao bloqueio e mensagens de historico
class AdminLoginLogTests {
    private static final String EMAIL = "admin-login-log@example.com";
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final LoginAttemptService tentativas = new LoginAttemptService();
    private final loginAdminController controller = new loginAdminController();
    private final loginAdminDTO admin = new loginAdminDTO();

    // preparar teste de login administrativo este metodo limpa tentativas e configura as dependencias simuladas
    @BeforeEach
    void preparar() {
        tentativas.loginSucesso(EMAIL);
        admin.setEmail(EMAIL);
        admin.setSenha("SenhaIncorreta1!");
        ReflectionTestUtils.setField(controller, "UsuarioRepository", usuarios);
        ReflectionTestUtils.setField(controller, "loginAttemptService", tentativas);
    }

    // limpar tentativas depois do teste este metodo remove o bloqueio do email usado no cenario
    @AfterEach
    void limpar() {
        tentativas.loginSucesso(EMAIL);
    }

    // testar dados invalidos no login este teste exige registro da recusa sem consultar a senha
    @Test
    void dadosInvalidosSaoAuditadosSemConsultarSenha() throws Exception {
        BeanPropertyBindingResult validacao = new BeanPropertyBindingResult(admin, "admin");
        validacao.reject("invalido");
        assertEquals("Usuarios/Admin/loginAdmin", controller.fazerLogin(
                admin, validacao, new ExtendedModelMap(), new MockHttpSession()));
        verify(usuarios, never()).QueryLoginUsuario(anyString(), anyString());
        verificarAviso("Dados de login inválidos", 1);
    }

    // testar falhas e bloqueio administrativo este teste confere os registros das tentativas e do bloqueio
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

    // conferir aviso de login este metodo verifica a mensagem e a quantidade de registros no banco simulado
    private void verificarAviso(String mensagem, int quantidade) throws Exception {
        verify(usuarios, times(quantidade)).InserirLogsNoBD(isNull(), eq("WARN"), anyString(), eq("fazerLogin"),
                isNull(), contains(mensagem), isNull(), isNull());
    }
}
