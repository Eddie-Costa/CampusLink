package com.example.CampusLink.service;

import com.example.CampusLink.dto.DashboardFiltro;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class DashboardFiltroTests {
    private final LocalDateTime agora = LocalDateTime.of(2026, 11, 2, 12, 0);

    @Test
    void periodoIncluiHojeSemAcrescentarUmDiaExtra() {
        assertEquals(LocalDate.of(2026,10,27), filtro("7").getInicio());
        assertEquals(LocalDate.of(2026,10,4), filtro("30").getInicio());
        assertEquals(LocalDate.of(2026,11,1), filtro("mes").getInicio());
        assertNull(filtro("todos").getInicio());
    }

    @Test
    void normalizaFiltrosDesconhecidos() {
        var filtro = new DashboardFiltro("inválido",-1,"URGENTE","outro",agora);
        assertEquals("todos",filtro.getPeriodo());
        assertEquals("todos",filtro.getPrioridade());
        assertEquals("todos",filtro.getPerfil());
        assertEquals(0,filtro.getTurma());
    }

    private DashboardFiltro filtro(String periodo) {
        return new DashboardFiltro(periodo,0,"todos","todos",agora);
    }
}
