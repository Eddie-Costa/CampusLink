package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginProfessorControllerTest {

    private static final String EMAIL = "login.professor.2fa@campuslink.invalid";
    private final TwoFactorService twoFactorService = new TwoFactorService();
    private final LoginAttemptService loginAttemptService = new LoginAttemptService();

    @AfterEach
    void limparEstado() {
        twoFactorService.limparCodigo(EMAIL);
        loginAttemptService.loginSucesso(EMAIL);
    }

    @Test
    void fazerLogin_deveIniciar2FAQuandoCredenciaisForemValidas() throws SQLException {
        String senha = "SenhaForte123!";
        UsuarioDaoFake usuarioDAO = new UsuarioDaoFake(new BCryptPasswordEncoder(10).encode(senha));
        EmailServiceSpy emailService = new EmailServiceSpy();
        loginProfessorController controller = new loginProfessorController();
        ReflectionTestUtils.setField(controller, "loginAttemptService", loginAttemptService);
        ReflectionTestUtils.setField(controller, "twoFactorService", twoFactorService);
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        ReflectionTestUtils.setField(controller, "usuarioDAO", usuarioDAO);
        ReflectionTestUtils.setField(controller, "twoFactorEnabled", true);
        loginProfessorDTO professor = new loginProfessorDTO();
        professor.setEmail(EMAIL);
        professor.setSenha(senha);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpSession session = new MockHttpSession();

        String view = controller.fazerLogin(professor,
                new BeanPropertyBindingResult(professor, "professor"), model, session);

        assertEquals("redirect:/verificarProfessor", view);
        assertEquals("professor", session.getAttribute("tipoUsuario"));
        assertNull(session.getAttribute("usuarioLogado"));
        assertEquals(EMAIL, emailService.destinatario);
        assertTrue(emailService.codigo.matches("[0-9]{6}"));
    }

    @Test
    void fazerLogin_deveRegistrarTentativaQuandoSenhaEstiverIncorreta() throws SQLException {
        UsuarioDaoFake usuarioDAO = new UsuarioDaoFake(new BCryptPasswordEncoder(10).encode("OutraSenha123!"));
        EmailServiceSpy emailService = new EmailServiceSpy();
        loginProfessorController controller = new loginProfessorController();
        ReflectionTestUtils.setField(controller, "loginAttemptService", loginAttemptService);
        ReflectionTestUtils.setField(controller, "twoFactorService", twoFactorService);
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        ReflectionTestUtils.setField(controller, "usuarioDAO", usuarioDAO);
        loginProfessorDTO professor = new loginProfessorDTO();
        professor.setEmail(EMAIL);
        professor.setSenha("SenhaErrada123!");
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.fazerLogin(professor,
                new BeanPropertyBindingResult(professor, "professor"), model, new MockHttpSession());

        assertEquals("Usuarios/Professor/loginProfessor", view);
        assertTrue(model.containsAttribute("mensagemDeErro"));
        assertEquals(1, loginAttemptService.getTentativas(EMAIL));
        assertNull(emailService.codigo);
    }

    private static class UsuarioDaoFake extends usuarioDAO {
        private final String senhaHash;

        private UsuarioDaoFake(String senhaHash) {
            this.senhaHash = senhaHash;
        }

        @Override
        public String QueryLoginUsuario(String tipoUsuario, String email) {
            return senhaHash;
        }
    }

    private static class EmailServiceSpy extends emailService {
        private String destinatario;
        private String codigo;

        @Override
        public void enviarCodigo(String para, String codigo) {
            destinatario = para;
            this.codigo = codigo;
        }
    }
}
