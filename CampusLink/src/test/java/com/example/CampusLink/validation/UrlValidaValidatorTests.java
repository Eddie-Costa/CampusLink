// verificacao dos links dos materiais confere o bloqueio de protocolos perigosos enderecos internos e dominios nao autorizados
package com.example.CampusLink.validation;

import com.example.CampusLink.service.DominioService;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes do verificador de links esta classe simula a lista de dominios autorizados pelo administrador
class UrlValidaValidatorTests {

    private UrlValidaValidator validador;
    private DominioService dominios;
    private ConstraintValidatorContext contexto;

    // preparar testes dos links este metodo cria o verificador com dominios e mensagens simulados
    @BeforeEach
    void preparar() {
        validador = new UrlValidaValidator();
        dominios = mock(DominioService.class);
        contexto = mock(ConstraintValidatorContext.class, Mockito.RETURNS_DEEP_STUBS);
        ReflectionTestUtils.setField(validador, "dominioService", dominios);
    }

    // testar protocolo perigoso este teste confere que links javascript sao recusados antes de consultar dominios
    @Test
    void recusaProtocoloJavascript() {
        // preparar link que executaria codigo no navegador
        String link = "javascript:alert(1)";

        // executar validacao
        boolean valido = validador.isValid(link, contexto);

        // verificar recusa sem consultar a lista de dominios
        assertFalse(valido);
        verify(dominios, never()).hostAutorizado(anyString());
    }

    // testar endereco interno este teste confere que links para o proprio servidor ou rede interna sao recusados
    @Test
    void recusaEnderecosDaRedeInterna() {
        // preparar links para a maquina local e para a rede interna

        // executar validacoes
        boolean local = validador.isValid("http://localhost:8080/admin", contexto);
        boolean redeInterna = validador.isValid("http://192.168.0.10/arquivo", contexto);

        // verificar recusa dos dois enderecos sem consultar dominios
        assertFalse(local);
        assertFalse(redeInterna);
        verify(dominios, never()).hostAutorizado(anyString());
    }

    // testar dominio nao autorizado este teste confere que links de sites nao aprovados pelo administrador sao recusados
    @Test
    void recusaDominioNaoAutorizado() {
        // preparar site fora da lista de dominios aprovados
        when(dominios.hostAutorizado("site-desconhecido.com")).thenReturn(false);

        // executar validacao
        boolean valido = validador.isValid("https://site-desconhecido.com/material", contexto);

        // verificar recusa do link
        assertFalse(valido);
    }

    // testar dominio autorizado este teste confere que links de sites aprovados sao aceitos
    @Test
    void aceitaDominioAutorizado() {
        // preparar site aprovado pelo administrador
        when(dominios.hostAutorizado("www.youtube.com")).thenReturn(true);

        // executar validacao
        boolean valido = validador.isValid("https://www.youtube.com/watch?v=abc", contexto);

        // verificar aceite do link
        assertTrue(valido);
    }
}
