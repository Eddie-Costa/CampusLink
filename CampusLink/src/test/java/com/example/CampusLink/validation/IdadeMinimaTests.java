// testes da idade minima conferem a regra de 17 anos usada nos cadastros
package com.example.CampusLink.validation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// testes da idade minima esta classe usa o dia 16/11/2026 como hoje para os casos de fronteira
class IdadeMinimaTests {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 16);
    private static final String MENOR = "É preciso ter pelo menos 17 anos para se cadastrar no CampusLink.";
    private static final String INVALIDA = "Informe uma data de nascimento válida.";

    // testar quem completa 17 anos hoje este teste confere que o cadastro e aceito no dia do aniversario
    @Test
    void aceitaQuemCompleta17AnosHoje() {
        // preparar nascimento exatamente 17 anos antes de hoje
        String nascimento = "2009-11-16";

        // executar validacao
        String erro = IdadeMinima.validar(nascimento, HOJE);

        // verificar que nao ha erro
        assertNull(erro);
    }

    // testar quem completa 17 anos amanha este teste confere a recusa de quem ainda tem 16 anos
    @Test
    void recusaQuemAindaTem16Anos() {
        // preparar nascimento que completa 17 anos so amanha
        String nascimento = "2009-11-17";

        // executar validacao
        String erro = IdadeMinima.validar(nascimento, HOJE);

        // verificar mensagem de idade minima
        assertEquals(MENOR, erro);
    }

    // testar datas invalidas este teste confere a recusa de data futura inexistente e vazia
    @Test
    void recusaDataFuturaInexistenteOuVazia() {
        // preparar datas invalidas

        // executar validacoes e verificar mensagem de data invalida
        assertEquals(INVALIDA, IdadeMinima.validar("2030-01-01", HOJE));
        assertEquals(INVALIDA, IdadeMinima.validar("2004-02-30", HOJE));
        assertEquals(INVALIDA, IdadeMinima.validar(null, HOJE));
    }
}
