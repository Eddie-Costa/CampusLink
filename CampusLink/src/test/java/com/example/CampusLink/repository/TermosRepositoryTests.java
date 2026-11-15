// verificacao do banco de termos confere versao atual publicacao aceite e revogacao do consentimento
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.TermoAceiteDTO;
import com.example.CampusLink.dto.TermoVersaoDTO;
import com.example.CampusLink.dto.TermoVersaoResumoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes do banco de termos esta classe usa o banco simulado com jdbctemplate real
class TermosRepositoryTests {

    private static final TermoVersaoDTO TERMO = new TermoVersaoDTO(3L, "2.0", "texto", "abc123", null);

    private BancoSimulado banco;
    private TermosRepository termos;

    // preparar testes dos termos este metodo liga o repositorio ao banco simulado
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        termos = new TermosRepository(banco.jdbcTemplate());
    }

    // testar versoes publicadas este teste confere a versao atual a lista com aceites e a ausencia de versao
    @Test
    void buscaVersaoAtualEListaVersoes() {
        // preparar versao publicada com aceites
        banco.quandoConsultar("TERMOS_VERSOES", linha("id", 3L, "versao", "2.0", "conteudo", "texto",
                "hash_sha256", "abc123", "publicado_em", LocalDateTime.of(2026, 11, 1, 9, 0), "aceites", 12L));

        // executar consultas
        TermoVersaoDTO atual = termos.buscarVersaoAtual();
        List<TermoVersaoResumoDTO> versoes = termos.listarVersoes();
        banco.semResultado("TERMOS_VERSOES");
        TermoVersaoDTO nenhuma = termos.buscarVersaoAtual();

        // verificar dados
        assertEquals("abc123", atual.hashSha256());
        assertEquals(12L, versoes.get(0).aceites());
        assertNull(nenhuma);
    }

    // testar publicacao este teste confere a conferencia de versao repetida e a gravacao da nova versao
    @Test
    void publicaNovaVersaoSemRepetir() {
        // preparar versao ja existente
        banco.quandoConsultar("SELECT EXISTS", linha("exists", true));

        // executar conferencia e publicacao
        boolean existe = termos.existeVersaoOuConteudo("2.0", "abc123");
        termos.publicar("3.0", "novo texto", "def456");

        // verificar parametros
        assertTrue(existe);
        assertEquals(List.of("3.0", "novo texto", "def456"), banco.ultimo("INSERT INTO public.\"TERMOS_VERSOES\"").valores());
    }

    // testar aceite do usuario este teste confere aceite pelo codigo e pelo hash da versao e o aceite ativo
    @Test
    void confereERegistraAceite() {
        // preparar aceite inexistente da versao atual
        banco.quandoConsultar("SELECT EXISTS", linha("exists", false));
        banco.quandoConsultar("c.aceito_em\n", linha("versao", "2.0", "hash_sha256", "abc123", "aceito_em", LocalDateTime.now()));

        // executar conferencia registro e busca do aceite
        boolean aceitou = termos.possuiAceite(2L, TERMO);
        termos.registrarAceite(2L, TERMO);
        TermoAceiteDTO ativo = termos.buscarAceiteAtivo(2L);

        // verificar hash usado e aceite encontrado
        assertFalse(aceitou);
        assertEquals(List.of(2L, 3L, "abc123"), banco.comandos().get(0).valores());
        assertTrue(banco.comandos().get(1).sql().contains("ON CONFLICT"));
        assertEquals("2.0", ativo.versao());
    }

    // testar revogacao este teste confere quantos aceites ativos foram revogados e a busca sem aceite ativo
    @Test
    void revogaAceitesAtivos() {
        // preparar um aceite ativo
        banco.quandoAlterar("SET revogado_em = now()", 1);
        banco.semResultado("c.aceito_em\n");

        // executar revogacao e busca
        int revogados = termos.revogarAceites(2L);
        TermoAceiteDTO ativo = termos.buscarAceiteAtivo(2L);

        // verificar total e ausencia de aceite
        assertEquals(1, revogados);
        assertNull(ativo);
    }
}
