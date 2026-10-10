package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoutControllerTest {

    @Test
    void logout_deveVoltarParaLoginQuandoNaoExisteUsuarioNaSessao() {
        MockHttpSession session = new MockHttpSession();

        String view = new LogoutController().logout(session);

        assertEquals("redirect:/login", view);
        assertFalse(session.isInvalid());
    }

    @Test
    void logout_deveInvalidarSessaoEEncaminharParaHomeQuandoUsuarioEstaAutenticado() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", new loginAlunoDTO());
        session.setAttribute("tipoUsuario", "aluno");
        MDC.put("aluno", "aluno.teste@campuslink.invalid");
        MDC.put("professor", "professor.teste@campuslink.invalid");
        MDC.put("sessionId", session.getId());

        try {
            String view = new LogoutController().logout(session);

            assertEquals("redirect:/home", view);
            assertTrue(session.isInvalid());
            assertNull(MDC.get("aluno"));
            assertNull(MDC.get("professor"));
            assertNull(MDC.get("sessionId"));
        } finally {
            MDC.remove("aluno");
            MDC.remove("professor");
            MDC.remove("sessionId");
        }
    }
}
