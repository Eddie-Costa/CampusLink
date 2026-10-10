package com.example.CampusLink.service;

import com.example.CampusLink.dao.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RevisaoConteudoServiceTest {
    @Mock RevisaoConteudoDAO dao;
    @Mock usuarioDAO usuarios;
    RevisaoConteudoService service;
    @BeforeEach void preparar() {
        service = new RevisaoConteudoService(dao);
        ReflectionTestUtils.setField(service, "usuarioDAO", usuarios);
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void deveRetornarResultadoDaLiberacao(boolean autorizado) throws Exception {
        when(dao.liberarConteudo(10L)).thenReturn(autorizado);
        assertEquals(autorizado, service.liberarConteudo(10L));
        verify(dao).liberarConteudo(10L);
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void deveRetornarResultadoDaReprovacao(boolean autorizado) throws Exception {
        when(dao.reprovarConteudo(10L, "Motivo")).thenReturn(autorizado);
        assertEquals(autorizado, service.reprovarConteudo(10L, "Motivo"));
        verify(dao).reprovarConteudo(10L, "Motivo");
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"  "})
    void deveRecusarReprovacaoSemComentario(String motivo) {
        assertFalse(service.reprovarConteudo(10L, motivo));
        verifyNoInteractions(dao);
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void deveEncapsularFalhaDePersistenciaPreservandoCausa(boolean liberar) throws Exception {
        SQLException causa = new SQLException("falha simulada");
        if (liberar) when(dao.liberarConteudo(10L)).thenThrow(causa);
        else when(dao.reprovarConteudo(10L, "Motivo")).thenThrow(causa);
        var erro = assertThrows(RuntimeException.class, () -> {
            if (liberar) service.liberarConteudo(10L); else service.reprovarConteudo(10L, "Motivo");
        });
        assertEquals(liberar ? "Não foi possível liberar o conteúdo." : "Não foi possível reprovar o conteúdo.", erro.getMessage());
        assertSame(causa, erro.getCause());
    }
}
