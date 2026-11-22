// verificacao dos dias de descanso confere fins de semana, feriados fixos e feriados moveis calculados pela pascoa
package com.example.CampusLink.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes dos dias de descanso esta classe usa datas de 2026 conferidas no calendario
class DiasDeDescansoTests {

    // testar pascoa este teste confere o algoritmo em anos conhecidos
    @Test
    void calculaPascoa() {
        assertEquals(LocalDate.of(2026, 4, 5), DiasDeDescanso.pascoa(2026));
        assertEquals(LocalDate.of(2027, 3, 28), DiasDeDescanso.pascoa(2027));
    }

    // testar descanso este teste confere fim de semana, feriados fixos e moveis e um dia util comum
    @Test
    void reconheceFimDeSemanaEFeriados() {
        assertTrue(DiasDeDescanso.ehDescanso(LocalDate.of(2026, 11, 28)));  // sabado
        assertTrue(DiasDeDescanso.ehDescanso(LocalDate.of(2026, 11, 29)));  // domingo
        assertTrue(DiasDeDescanso.ehDescanso(LocalDate.of(2026, 11, 20)));  // consciencia negra
        assertTrue(DiasDeDescanso.ehDescanso(LocalDate.of(2026, 12, 25)));  // natal
        assertTrue(DiasDeDescanso.ehDescanso(LocalDate.of(2026, 2, 16)));   // segunda de carnaval
        assertTrue(DiasDeDescanso.ehDescanso(LocalDate.of(2026, 2, 17)));   // terca de carnaval
        assertTrue(DiasDeDescanso.ehDescanso(LocalDate.of(2026, 4, 3)));    // sexta-feira santa
        assertTrue(DiasDeDescanso.ehDescanso(LocalDate.of(2026, 6, 4)));    // corpus christi
        assertFalse(DiasDeDescanso.ehDescanso(LocalDate.of(2026, 11, 24))); // terca comum
    }
}
