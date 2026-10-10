package com.example.CampusLink.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private static final String EMAIL_TESTE = "teste.login.attempt.increment@campuslink.invalid";
    private final LoginAttemptService loginAttemptService = new LoginAttemptService();

    @AfterEach
    void limparEstadoDoTeste() {
        loginAttemptService.loginSucesso(EMAIL_TESTE);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void loginFalhou_deveContarTentativasSemBloquearAntesDoLimite(int quantidade) {
        for (int tentativa = 0; tentativa < quantidade; tentativa++) {
            loginAttemptService.loginFalhou(EMAIL_TESTE);
        }

        assertEquals(quantidade, loginAttemptService.getTentativas(EMAIL_TESTE));
        assertFalse(LoginAttemptService.estaBloqueado(EMAIL_TESTE));
    }

    @Test
    void estaBloqueado_deveRetornarVerdadeiroNaQuintaFalha() {
        for (int tentativa = 0; tentativa < 5; tentativa++) {
            loginAttemptService.loginFalhou(EMAIL_TESTE);
        }

        assertEquals(5, loginAttemptService.getTentativas(EMAIL_TESTE));
        assertTrue(LoginAttemptService.estaBloqueado(EMAIL_TESTE));
    }

    @Test
    void loginSucesso_deveRemoverBloqueioEZerarTentativas() {
        for (int tentativa = 0; tentativa < 5; tentativa++) {
            loginAttemptService.loginFalhou(EMAIL_TESTE);
        }

        loginAttemptService.loginSucesso(EMAIL_TESTE);

        assertEquals(0, loginAttemptService.getTentativas(EMAIL_TESTE));
        assertFalse(LoginAttemptService.estaBloqueado(EMAIL_TESTE));
    }
}
