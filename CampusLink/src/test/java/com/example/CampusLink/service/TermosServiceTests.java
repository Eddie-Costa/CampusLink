package com.example.CampusLink.service;

import com.example.CampusLink.dto.TermoVersaoDTO;
import com.example.CampusLink.repository.TermosRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TermosServiceTests {

    private TermosRepository repositorio;
    private TermosService termos;

    @BeforeEach
    void preparar() {
        repositorio = mock(TermosRepository.class);
        termos = new TermosService(repositorio);
        ReflectionTestUtils.setField(termos, "UsuarioRepository", mock(UsuarioRepository.class));
    }

    @Test
    void calculaSha256ConhecidoEmHexadecimal() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TermosService.calcularHash("abc"));
    }

    @Test
    void devolveTermoQuandoTextoConfereComHash() {
        TermoVersaoDTO termo = versao("Texto dos termos", TermosService.calcularHash("Texto dos termos"));
        when(repositorio.buscarVersaoAtual()).thenReturn(termo);

        assertEquals(termo, termos.versaoAtual());
    }

    @Test
    void recusaTermoAlteradoDepoisDoRegistroDoHash() {
        when(repositorio.buscarVersaoAtual())
                .thenReturn(versao("Texto alterado", TermosService.calcularHash("Texto original")));

        assertThrows(IllegalStateException.class, termos::versaoAtual);
    }

    @Test
    void recusaQuandoNaoHaVersaoPublicada() {
        when(repositorio.buscarVersaoAtual()).thenReturn(null);

        assertThrows(IllegalStateException.class, termos::versaoAtual);
    }

    @Test
    void publicaComQuebrasDeLinhaPadronizadasEHashDoTextoGravado() {
        String hash = termos.publicarNovaVersao(" 1.1 ", "Linha 1\r\nLinha 2\r\n\r\n");

        assertEquals(TermosService.calcularHash("Linha 1\nLinha 2\n"), hash);
        verify(repositorio).publicar("1.1", "Linha 1\nLinha 2\n", hash);
    }

    @Test
    void recusaVersaoForaDoFormatoOuTextoVazio() {
        assertThrows(IllegalArgumentException.class, () -> termos.publicarNovaVersao("v2", "Texto"));
        assertThrows(IllegalArgumentException.class, () -> termos.publicarNovaVersao("2.0", "   \r\n "));
        verify(repositorio, never()).publicar(any(), any(), any());
    }

    @Test
    void recusaVersaoOuTextoJaPublicados() {
        when(repositorio.existeVersaoOuConteudo(eq("1.0"), any())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> termos.publicarNovaVersao("1.0", "Texto novo"));
        verify(repositorio, never()).publicar(any(), any(), any());
    }

    @Test
    void revogaSomenteOsAceitesDoProprioUsuario() {
        when(repositorio.revogarAceites(7L)).thenReturn(1);

        termos.revogarAceite(7L);

        verify(repositorio).revogarAceites(7L);
        verify(repositorio, never()).revogarAceites(8L);
    }

    private TermoVersaoDTO versao(String conteudo, String hash) {
        return new TermoVersaoDTO(1L, "1.0", conteudo, hash, OffsetDateTime.now());
    }
}
