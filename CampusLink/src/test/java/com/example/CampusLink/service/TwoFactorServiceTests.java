// verificacao da verificacao em duas etapas confere uso unico expiracao e espera para reenviar o codigo
package com.example.CampusLink.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes do codigo de verificacao esta classe limpa os codigos guardados em memoria ao final de cada teste
class TwoFactorServiceTests {

    private static final String EMAIL = "aluno.2fa@teste.com";

    private TwoFactorService doisFatores;

    // preparar testes do codigo este metodo cria o servico de verificacao
    @BeforeEach
    void preparar() {
        doisFatores = new TwoFactorService();
    }

    // limpar codigos do teste este metodo remove o codigo do email para nao influenciar outros testes
    @AfterEach
    void limpar() {
        doisFatores.limparCodigo(EMAIL);
    }

    // testar uso unico do codigo este teste confere que o mesmo codigo nao autentica duas vezes
    @Test
    void codigoValidoSoPodeSerUsadoUmaVez() {
        // preparar codigo enviado ao email
        String codigo = TwoFactorService.gerarCodigo(EMAIL);

        // executar duas validacoes com o mesmo codigo
        boolean primeiraVez = doisFatores.validarCodigo(EMAIL, codigo);
        boolean segundaVez = doisFatores.validarCodigo(EMAIL, codigo);

        // verificar aceite apenas na primeira tentativa
        assertTrue(primeiraVez);
        assertFalse(segundaVez);
    }

    // testar codigo expirado este teste confere que o codigo correto e recusado depois de cinco minutos
    @Test
    @SuppressWarnings("unchecked")
    void recusaCodigoCorretoDepoisDeExpirar() {
        // preparar codigo cujo horario de expiracao ja passou
        String codigo = TwoFactorService.gerarCodigo(EMAIL);
        Map<String, Long> expiracao = (Map<String, Long>) ReflectionTestUtils.getField(TwoFactorService.class, "expiracao");
        expiracao.put(EMAIL, System.currentTimeMillis() - 1);

        // executar validacao com o codigo correto
        boolean aceito = doisFatores.validarCodigo(EMAIL, codigo);

        // verificar recusa do codigo vencido
        assertFalse(aceito);
    }

    // testar reenvio antecipado este teste confere que um novo codigo so pode ser pedido apos o intervalo de espera
    @Test
    void recusaReenvioAntesDoIntervaloDeEspera() {
        // preparar codigo recem enviado
        String codigo = TwoFactorService.gerarCodigo(EMAIL);

        // executar pedido de reenvio imediato
        assertThrows(IllegalStateException.class, () -> doisFatores.reenviarCodigo(EMAIL));

        // verificar que o codigo anterior continua valendo
        assertTrue(doisFatores.validarCodigo(EMAIL, codigo));
    }
}
