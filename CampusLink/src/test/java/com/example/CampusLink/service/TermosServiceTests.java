// verificacao do servico dos termos confere hash sha-256 publicacao de versoes e revogacao
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

// testes dos termos de uso esta classe confere a integridade do texto e as regras de nova versao
class TermosServiceTests {

    private TermosRepository repositorio;
    private TermosService termos;

    // preparar testes dos termos este metodo cria o servico com o repositorio simulado
    @BeforeEach
    void preparar() {
        repositorio = mock(TermosRepository.class);
        termos = new TermosService(repositorio);
        ReflectionTestUtils.setField(termos, "UsuarioRepository", mock(UsuarioRepository.class));
    }

    // testar hash conhecido este teste confere o sha-256 de um texto de referencia
    @Test
    void calculaSha256ConhecidoEmHexadecimal() {
        assertEquals("falha-intencional", TermosService.calcularHash("abc"));
    }

    // testar termo integro este teste confere que o texto que bate com o hash e devolvido
    @Test
    void devolveTermoQuandoTextoConfereComHash() {
        TermoVersaoDTO termo = versao("Texto dos termos", TermosService.calcularHash("Texto dos termos"));
        when(repositorio.buscarVersaoAtual()).thenReturn(termo);

        assertEquals(termo, termos.versaoAtual());
    }

    // testar termo adulterado este teste confere que texto diferente do hash gravado e recusado
    @Test
    void recusaTermoAlteradoDepoisDoRegistroDoHash() {
        when(repositorio.buscarVersaoAtual())
                .thenReturn(versao("Texto alterado", TermosService.calcularHash("Texto original")));

        assertThrows(IllegalStateException.class, termos::versaoAtual);
    }

    // testar ausencia de versao este teste confere a recusa quando nenhum termo foi publicado
    @Test
    void recusaQuandoNaoHaVersaoPublicada() {
        when(repositorio.buscarVersaoAtual()).thenReturn(null);

        assertThrows(IllegalStateException.class, termos::versaoAtual);
    }

    // testar quebras de linha este teste confere que o mesmo texto gera o mesmo hash em qualquer sistema
    @Test
    void publicaComQuebrasDeLinhaPadronizadasEHashDoTextoGravado() {
        String hash = termos.publicarNovaVersao(" 1.1 ", "Linha 1\r\nLinha 2\r\n\r\n");

        assertEquals(TermosService.calcularHash("Linha 1\nLinha 2\n"), hash);
        verify(repositorio).publicar("1.1", "Linha 1\nLinha 2\n", hash);
    }

    // testar dados invalidos este teste confere a recusa de numero fora do formato e texto vazio
    @Test
    void recusaVersaoForaDoFormatoOuTextoVazio() {
        assertThrows(IllegalArgumentException.class, () -> termos.publicarNovaVersao("v2", "Texto"));
        assertThrows(IllegalArgumentException.class, () -> termos.publicarNovaVersao("2.0", "   \r\n "));
        verify(repositorio, never()).publicar(any(), any(), any());
    }

    // testar versao repetida este teste confere a recusa de numero ou texto ja publicados
    @Test
    void recusaVersaoOuTextoJaPublicados() {
        when(repositorio.existeVersaoOuConteudo(eq("1.0"), any())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> termos.publicarNovaVersao("1.0", "Texto novo"));
        verify(repositorio, never()).publicar(any(), any(), any());
    }

    // testar revogacao este teste confere que apenas os aceites do proprio usuario sao revogados
    @Test
    void revogaSomenteOsAceitesDoProprioUsuario() {
        when(repositorio.revogarAceites(7L)).thenReturn(1);

        termos.revogarAceite(7L);

        verify(repositorio).revogarAceites(7L);
        verify(repositorio, never()).revogarAceites(8L);
    }

    // montar versao do teste este metodo cria o termo com o texto e o hash informados
    private TermoVersaoDTO versao(String conteudo, String hash) {
        return new TermoVersaoDTO(1L, "1.0", conteudo, hash, OffsetDateTime.now());
    }
}
