package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.service.LoginAttemptService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAlunoControllerTest {

    private static final String EMAIL = "login.aluno.falha.critica@campuslink.invalid";
    private final LoginAttemptService loginAttemptService = new LoginAttemptService();
    private final TwoFactorService twoFactorService = new TwoFactorService();

    @AfterEach
    void limparEstado() {
        loginAttemptService.loginSucesso(EMAIL);
        twoFactorService.limparCodigo(EMAIL);
    }

    @Test
    void fazerLogin_deveContarTentativaQuandoSenhaEstiverIncorreta() throws SQLException {
        UsuarioDaoFake usuarioDAO = new UsuarioDaoFake(hash("OutraSenha123!"));
        EmailServiceSpy emailService = new EmailServiceSpy();
        loginAlunoController controller = criarController(usuarioDAO, emailService);
        loginAlunoDTO aluno = aluno("SenhaErrada123!");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpSession session = new MockHttpSession();

        String view = controller.fazerLogin(aluno, new BeanPropertyBindingResult(aluno, "aluno"), model, session);

        assertEquals("Usuarios/Aluno/loginAluno", view);
        assertTrue(model.containsAttribute("mensagemDeErro"));
        assertEquals(1, loginAttemptService.getTentativas(EMAIL));
        assertFalse(emailService.codigoEnviado);
        assertNull(session.getAttribute("usuarioLogado"));
    }

    @Test
    void fazerLogin_deveRecusarContaBloqueadaSemConsultarBanco() throws SQLException {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.loginFalhou(EMAIL);
        }
        UsuarioDaoFake usuarioDAO = new UsuarioDaoFake(hash("SenhaForte123!"));
        EmailServiceSpy emailService = new EmailServiceSpy();
        loginAlunoController controller = criarController(usuarioDAO, emailService);
        loginAlunoDTO aluno = aluno("SenhaForte123!");
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.fazerLogin(aluno, new BeanPropertyBindingResult(aluno, "aluno"),
                model, new MockHttpSession());

        assertEquals("Usuarios/Aluno/loginAluno", view);
        assertEquals("Conta bloqueada por muitas tentativas. Tente mais tarde.", model.getAttribute("mensagemDeErro"));
        assertNull(usuarioDAO.tipoConsultado);
        assertFalse(emailService.codigoEnviado);
    }

    private loginAlunoController criarController(UsuarioDaoFake usuarioDAO, EmailServiceSpy emailService) {
        loginAlunoController controller = new loginAlunoController();
        ReflectionTestUtils.setField(controller, "loginAttemptService", loginAttemptService);
        ReflectionTestUtils.setField(controller, "twoFactorService", twoFactorService);
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        ReflectionTestUtils.setField(controller, "usuarioDAO", usuarioDAO);
        ReflectionTestUtils.setField(controller, "twoFactorEnabled", true);
        return controller;
    }

    private loginAlunoDTO aluno(String senha) {
        loginAlunoDTO aluno = new loginAlunoDTO();
        aluno.setEmail(EMAIL);
        aluno.setSenha(senha);
        return aluno;
    }

    private String hash(String senha) {
        return new BCryptPasswordEncoder(10).encode(senha);
    }

    private static class UsuarioDaoFake extends usuarioDAO {
        private final String senhaHash;
        private String tipoConsultado;

        private UsuarioDaoFake(String senhaHash) {
            this.senhaHash = senhaHash;
        }

        @Override
        public String QueryLoginUsuario(String tipoUsuario, String email) {
            tipoConsultado = tipoUsuario;
            return senhaHash;
        }
    }

    private static class EmailServiceSpy extends emailService {
        private boolean codigoEnviado;

        @Override
        public void enviarCodigo(String para, String codigo) {
            codigoEnviado = true;
        }
    }
}
