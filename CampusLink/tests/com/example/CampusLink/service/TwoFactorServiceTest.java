package com.example.CampusLink.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoFactorServiceTest {

    private static final String CHAVE_TESTE = "teste:2fa:codigo-seis-digitos";
    private final TwoFactorService twoFactorService = new TwoFactorService();

    @AfterEach
    void limparEstadoDoTeste() {
        twoFactorService.limparCodigo(CHAVE_TESTE);
    }

    @Test
    void gerarCodigo_deveRetornarSeisDigitosNumericos() {
        String codigo = TwoFactorService.gerarCodigo(CHAVE_TESTE);

        assertTrue(codigo.matches("[0-9]{6}"));
    }

    @Test
    void validarCodigo_deveAceitarCodigoCorretoUmaUnicaVez() {
        String codigo = TwoFactorService.gerarCodigo(CHAVE_TESTE);

        assertTrue(twoFactorService.validarCodigo(CHAVE_TESTE, codigo));
        assertFalse(twoFactorService.validarCodigo(CHAVE_TESTE, codigo));
    }

    @Test
    void validarCodigo_devePreservarCodigoCorretoAposTentativaIncorreta() {
        String codigoCorreto = TwoFactorService.gerarCodigo(CHAVE_TESTE);

        assertFalse(twoFactorService.validarCodigo(CHAVE_TESTE, "000000"));
        assertTrue(twoFactorService.validarCodigo(CHAVE_TESTE, codigoCorreto));
    }

    @Test
    void validarCodigo_deveRejeitarCodigoQuandoNaoExisteVerificacaoAtiva() {
        assertFalse(twoFactorService.validarCodigo(CHAVE_TESTE, "123456"));
    }

    @Test
    void gerarCodigo_deveInformarMensagemQuandoChaveForInvalida() {
        IllegalArgumentException chaveNula = assertThrows(
                IllegalArgumentException.class,
                () -> TwoFactorService.gerarCodigo(null));
        IllegalArgumentException chaveEmBranco = assertThrows(
                IllegalArgumentException.class,
                () -> TwoFactorService.gerarCodigo("   "));

        assertEquals("a chave de verificacao e obrigatoria", chaveNula.getMessage());
        assertEquals("a chave de verificacao e obrigatoria", chaveEmBranco.getMessage());
    }
}
