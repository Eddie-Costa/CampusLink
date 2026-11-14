package com.example.CampusLink.service;

import com.example.CampusLink.dto.Admin.SessaoLogDTO;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessaoLogServiceTests {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final SessaoLogService service = new SessaoLogService(usuarios);

    @Test
    void combinaFiltrosEAjustaPaginaForaDoLimite() throws Exception {
        LocalDate inicio = LocalDate.of(2026, 11, 1);
        LocalDate fim = LocalDate.of(2026, 11, 12);
        when(usuarios.contarSessoesEncerradas("Maria", 5L, inicio, fim, 10, 60)).thenReturn(11L);
        when(usuarios.listarSessoesEncerradas(1, "Maria", 5L, inicio, fim, 10, 60)).thenReturn(List.of(new SessaoLogDTO()));
        var pagina = service.listar(99, "Maria", 5L, inicio, fim, 10, 60);
        assertEquals(1, pagina.getNumber());
        assertEquals(10, pagina.getSize());
        assertEquals(2, pagina.getTotalPages());
        assertEquals(11, pagina.getTotalElements());
        verify(usuarios).listarSessoesEncerradas(1, "Maria", 5L, inicio, fim, 10, 60);
    }

    @Test
    void rejeitaIntervalosInvalidosAntesDeConsultarOBanco() {
        LocalDate hoje = LocalDate.of(2026, 11, 12);
        assertThrows(IllegalArgumentException.class, () -> service.listar(0, "", null, hoje, hoje.minusDays(1), null, null));
        assertThrows(IllegalArgumentException.class, () -> service.listar(0, "", null, null, null, -1, null));
        assertThrows(IllegalArgumentException.class, () -> service.listar(0, "", null, null, null, 20, 10));
        assertThrows(IllegalArgumentException.class, () -> service.listar(0, "", 0L, null, null, null, null));
        verifyNoInteractions(usuarios);
    }

    @Test
    void listaVaziaUsaPrimeiraPagina() throws Exception {
        when(usuarios.listarSessoesEncerradas(0, "", null, null, null, null, null)).thenReturn(List.of());
        var pagina = service.listar(-1, "", null, null, null, null, null);
        assertTrue(pagina.isEmpty());
        assertEquals(0, pagina.getNumber());
    }

    @Test
    void ocultaOcorrenciasSensiveisInteirasEPreservaSeparacaoDasDemais() {
        String primeira = "2026-11-12 10:00:00+00 [INFO] Turmas eventoId=1 Turma consultada\n";
        String ultima = "2026-11-12 10:02:00+00 [INFO] SessaoLogListener Historico consolidado.\n";
        for (String dado : List.of("senha=Segredo1!", "{\n\"access_token\":\n\"Segredo1!\"\n}",
                "Código de autenticação: Segredo1!", "Authorization: Bearer Segredo1!",
                "https://pessoa:Segredo1!@exemplo.com", "api_key=Segredo1!", "Cookie: Segredo1!")) {
            String resultado = service.ocultarDadosSensiveis(primeira + "2026-11-12 10:01:00+00 [DEBUG] " + dado + "\n" + ultima);
            assertTrue(resultado.startsWith(primeira));
            assertTrue(resultado.endsWith(ultima));
            assertTrue(resultado.contains("Registro ocultado"));
            assertFalse(resultado.contains("Segredo1!"));
        }
    }

    @Test
    void protegeTambemHistoricoLegadoSemSeparacao() {
        assertFalse(service.ocultarDadosSensiveis("token:\nvalor-secreto\ncontinuação").contains("valor-secreto"));
        assertEquals("linha 1\nlinha 2", service.ocultarDadosSensiveis("linha 1\nlinha 2"));
        assertEquals("Nenhum log disponível nesta sessão.", service.ocultarDadosSensiveis(null));
    }

    @Test
    void detalheRetornaSomenteTextoProtegidoENaoGravaNoBanco() throws Exception {
        UUID id = UUID.randomUUID();
        SessaoLogDTO sessao = new SessaoLogDTO();
        sessao.setLogCompleto("password=segredo");
        when(usuarios.buscarSessaoEncerrada(id)).thenReturn(sessao);
        assertFalse(service.buscar(id).getLogCompleto().contains("segredo"));
        verify(usuarios).buscarSessaoEncerrada(id);
        verifyNoMoreInteractions(usuarios);
    }

    @Test
    void calculaDuracaoMaiorQueUmDiaELidaComDatasAusentes() {
        SessaoLogDTO sessao = new SessaoLogDTO();
        assertEquals("Não disponível", sessao.getDuracao());
        sessao.setInicio(LocalDateTime.of(2026, 11, 10, 23, 0));
        sessao.setFim(sessao.getInicio().plusHours(25).plusMinutes(2).plusSeconds(3));
        assertEquals("25h 02min 03s", sessao.getDuracao());
        sessao.setFim(sessao.getInicio().minusSeconds(1));
        assertEquals("Não disponível", sessao.getDuracao());
    }
}
