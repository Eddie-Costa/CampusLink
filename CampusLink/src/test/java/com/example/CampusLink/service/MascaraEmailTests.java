// verificacao da mascara de email confere o que o administrador ve dos outros usuarios
package com.example.CampusLink.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// testes da mascara de email esta classe confere emails comuns curtos ausentes e invalidos
class MascaraEmailTests {

    private final MascaraEmail mascara = new MascaraEmail();

    // testar email comum este teste confere que so as duas primeiras letras e o dominio aparecem
    @Test
    void escondeOMeioDoEmail() {
        // executar e verificar
        assertEquals("an***@gmail.com", mascara.mascarar("ana.souza@gmail.com"));
        assertEquals("a***@teste.com", mascara.mascarar("a@teste.com"));
    }

    // testar valores sem email este teste confere que nada do valor original e revelado
    @Test
    void naoRevelaValoresSemEmail() {
        // executar e verificar
        assertEquals("Não informado", mascara.mascarar(null));
        assertEquals("Não informado", mascara.mascarar(" "));
        assertEquals("***", mascara.mascarar("sem-arroba"));
        assertEquals("***", mascara.mascarar("@dominio.com"));
    }
}
