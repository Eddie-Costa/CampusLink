// verificacao da viabilidade com dados do banco confere conteudos atrasados prioridades proximo evento e formatacao das horas
package com.example.CampusLink.service;

import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.SessaoEstudoDTO;
import com.example.CampusLink.dto.ViabilidadeDTO;
import com.example.CampusLink.model.Disponibilidade;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import com.example.CampusLink.repository.EventoRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// testes da viabilidade com banco esta classe simula turmas eventos materiais e horas livres de dois alunos
class ViabilidadeCargaTests {

    private static final LocalDateTime AGORA = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

    private EventoRepository eventos;
    private ConteudoRepository conteudos;
    private TurmaRepository turmas;
    private DisponibilidadeRepository horas;
    private ViabilidadeService viabilidade;

    // preparar testes da viabilidade este metodo cria o servico com os quatro bancos simulados
    @BeforeEach
    void preparar() throws SQLException {
        eventos = mock(EventoRepository.class);
        conteudos = mock(ConteudoRepository.class);
        turmas = mock(TurmaRepository.class);
        horas = mock(DisponibilidadeRepository.class);
        viabilidade = new ViabilidadeService(eventos, conteudos, turmas, horas);
        ReflectionTestUtils.setField(viabilidade, "UsuarioRepository", mock(UsuarioRepository.class));
        when(turmas.buscarTurmasDosAlunos(anyList())).thenReturn(Map.of(7L, List.of(1L), 8L, List.of(2L)));
    }

    // testar atrasados este teste confere que so entram materiais da turma do aluno com prazo encerrado e sem conclusao
    @Test
    void listaSoMateriaisRealmenteAtrasados() throws SQLException {
        // preparar dois eventos passados com materiais repetidos concluidos e reabertos
        Evento prova = evento(5L, 1L, AGORA.minusDays(3));
        Evento outraTurma = evento(6L, 3L, AGORA.minusDays(2));
        when(eventos.findByIdTurmaInAndStatusAndInicioLessThanOrderByInicioDesc(any(), anyString(), any()))
                .thenReturn(List.of(prova, outraTurma));
        when(conteudos.listarPorEventos(anyList())).thenReturn(Map.of(
                5L, List.of(material(10L, 30), material(11L, null), material(12L, 45), material(10L, 30)),
                6L, List.of(material(13L, 20))));
        when(conteudos.listarConcluidosDosAlunos(anyList())).thenReturn(Map.of(7L, Set.of(11L)));
        when(conteudos.listarPrazosEncerrados(anyList(), any())).thenReturn(Map.of(10L, AGORA, 11L, AGORA, 13L, AGORA));

        // executar listagem
        Map<Long, List<ViabilidadeDTO>> atrasados = viabilidade.listarAtrasados(List.of(7L, 8L));

        // verificar apenas o material 10 uma vez para o aluno 7
        List<ViabilidadeDTO> doAluno = atrasados.get(7L);
        assertEquals(1, doAluno.size());
        assertEquals(10L, doAluno.get(0).getIdConteudo());
        assertEquals("atrasado", doAluno.get(0).getNivel());
        assertEquals(30, doAluno.get(0).getCargaMin());
        assertTrue(doAluno.get(0).getMotivo().startsWith("O prazo terminou em"));
        assertTrue(atrasados.get(8L).isEmpty());
    }

    // testar aluno sem turma este teste confere que nao ha consulta de eventos sem turmas e lista vazia sem alunos
    @Test
    void naoConsultaEventosSemTurmas() throws SQLException {
        // preparar aluno sem turmas
        when(turmas.buscarTurmasDosAlunos(anyList())).thenReturn(Map.of());

        // executar consultas
        Map<Long, List<ViabilidadeDTO>> atrasados = viabilidade.listarAtrasados(List.of(9L));
        List<ViabilidadeDTO> resultado = viabilidade.calcularParaAluno(9L);
        boolean semAlunos = viabilidade.listarAtrasados(List.of()).isEmpty() && viabilidade.calcularParaAlunos(List.of()).isEmpty();

        // verificar listas vazias e nenhuma consulta de eventos
        assertTrue(atrasados.get(9L).isEmpty());
        assertTrue(resultado.isEmpty());
        assertTrue(semAlunos);
        verifyNoInteractions(eventos);
    }

    // testar calculo e plano com banco este teste confere que eventos futuros e horas livres chegam ao calculo e ao plano
    @Test
    void calculaViabilidadeEPlanoComDadosDoBanco() throws SQLException {
        // preparar evento em cinco dias com uma hora de material e duas horas livres por dia
        LocalDate hoje = AGORA.toLocalDate();
        Evento prova = evento(5L, 1L, AGORA.plusDays(5).withHour(19));
        when(eventos.findByIdTurmaInAndStatusAndInicioGreaterThanEqualOrderByInicioAsc(any(), anyString(), any())).thenReturn(List.of(prova));
        when(conteudos.listarPorEventos(anyList())).thenReturn(Map.of(5L, List.of(material(10L, 60))));
        when(conteudos.listarConcluidosDosAlunos(anyList())).thenReturn(Map.of());
        when(horas.findAllByIdAlunoIn(anyList())).thenReturn(List.of(
                new Disponibilidade(7L, hoje.plusDays(1), 1), new Disponibilidade(7L, hoje.plusDays(1), 1),
                new Disponibilidade(7L, hoje.plusDays(2), 2)));

        // executar calculo e plano
        List<ViabilidadeDTO> resultado = viabilidade.calcularParaAluno(7L);
        List<SessaoEstudoDTO> plano = viabilidade.gerarPlano(7L);

        // verificar evento possivel e sessao sugerida antes da prova
        assertTrue(resultado.stream().anyMatch(item -> "verde".equals(item.getNivel())));
        assertFalse(plano.isEmpty());
        assertTrue(plano.get(0).getData().isBefore(prova.getInicio().toLocalDate()));
    }

    // testar prioridades e proximo evento este teste confere a ordem vermelho amarelo e o primeiro evento nao concluido
    @Test
    void escolhePrioridadesEProximoEvento() {
        // preparar resultados misturados
        ViabilidadeDTO concluido = item(null, "concluido");
        ViabilidadeDTO pendente = item(null, "amarelo");
        ViabilidadeDTO amarelo = item(10L, "amarelo");
        ViabilidadeDTO vermelho = item(11L, "vermelho");
        ViabilidadeDTO vermelhoRepetido = item(11L, "vermelho");
        ViabilidadeDTO verde = item(12L, "verde");
        List<ViabilidadeDTO> resultados = List.of(concluido, amarelo, verde, vermelho, pendente, vermelhoRepetido);

        // executar escolhas
        List<ViabilidadeDTO> prioridades = viabilidade.listarPrioridades(resultados);
        ViabilidadeDTO proximo = viabilidade.proximoEventoPendente(resultados);
        ViabilidadeDTO nenhum = viabilidade.proximoEventoPendente(List.of(amarelo));

        // verificar ordem sem repeticao e evento pendente
        assertEquals(List.of(vermelho, amarelo), prioridades);
        assertEquals(pendente, proximo);
        assertNull(nenhum);
    }

    // testar prioridades por evento este teste confere so resumos de evento, vermelho antes de amarelo, e os conteudos que faltam
    @Test
    void listaEventosPrioritariosComConteudosPendentes() {
        // preparar resumos de tres eventos e conteudos de um deles
        ViabilidadeDTO eventoAmarelo = item(null, "amarelo");
        ViabilidadeDTO eventoVerde = item(null, "verde");
        ViabilidadeDTO eventoVermelho = item(null, "vermelho");
        eventoVermelho.setIdEvento(7L);
        ViabilidadeDTO conteudoPendente = item(10L, "vermelho");
        conteudoPendente.setIdEvento(7L);
        ViabilidadeDTO conteudoConcluido = item(11L, "concluido");
        conteudoConcluido.setIdEvento(7L);
        List<ViabilidadeDTO> resultados = List.of(eventoAmarelo, conteudoPendente, eventoVerde, conteudoConcluido, eventoVermelho);

        // executar escolhas
        List<ViabilidadeDTO> eventos = viabilidade.listarEventosPrioritarios(resultados);
        var pendentes = viabilidade.conteudosPendentesPorEvento(resultados);

        // verificar ordem, ausencia de conteudos soltos e de concluidos
        assertEquals(List.of(eventoVermelho, eventoAmarelo), eventos);
        assertEquals(List.of(conteudoPendente), pendentes.get(7L));
    }

    // testar horas por dia e formatacao este teste confere a divisao da carga pelos dias restantes e o texto das horas
    @Test
    void calculaHorasPorDiaEFormataTempo() {
        // preparar evento em quatro dias com cinco horas e evento de hoje com noventa minutos
        ViabilidadeDTO futuro = item(null, "verde");
        futuro.setDataEvento(AGORA.plusDays(4));
        futuro.setCargaMin(300);
        ViabilidadeDTO hoje = item(null, "vermelho");
        hoje.setDataEvento(AGORA);
        hoje.setCargaMin(90);

        // executar calculos
        int porDiaFuturo = viabilidade.minutosNecessariosPorDia(futuro);
        int porDiaHoje = viabilidade.minutosNecessariosPorDia(hoje);

        // verificar divisao e formatos
        assertEquals(75, porDiaFuturo);
        assertEquals(90, porDiaHoje);
        assertEquals("0h", viabilidade.formatarHoras(0));
        assertEquals("45min", viabilidade.formatarHoras(45));
        assertEquals("2h", viabilidade.formatarHoras(120));
        assertEquals("1h05", viabilidade.formatarHoras(65));
        assertEquals(60, viabilidade.duracaoDe(material(1L, null)));
    }

    // testar falhas do banco este teste confere a mensagem de cada consulta quando o banco esta indisponivel
    @Test
    void trocaFalhasDoBancoPorMensagens() throws SQLException {
        // preparar banco indisponivel
        when(turmas.buscarTurmasDosAlunos(anyList())).thenThrow(new SQLException("banco"));
        when(conteudos.minutosConcluidosRecentes(anyList(), anyInt(), anyInt())).thenThrow(new SQLException("banco"));

        // executar consultas e verificar mensagens
        assertTrue(assertThrows(RuntimeException.class, () -> viabilidade.calcularParaAluno(7L)).getMessage().contains("viabilidade"));
        assertTrue(assertThrows(RuntimeException.class, () -> viabilidade.listarAtrasados(List.of(7L))).getMessage().contains("atrasados"));
        assertTrue(assertThrows(RuntimeException.class, () -> viabilidade.gerarPlano(7L)).getMessage().contains("viabilidade"));
        assertTrue(assertThrows(RuntimeException.class, () -> viabilidade.minutosConcluidosRecentes(List.of(7L), 7)).getMessage().contains("ritmo"));
    }

    // montar evento este metodo cria um evento ativo da turma informada
    private Evento evento(Long id, Long idTurma, LocalDateTime inicio) {
        Evento evento = new Evento("Prova " + id, inicio, inicio.plusHours(2), null, "ALTA");
        ReflectionTestUtils.setField(evento, "id", id);
        evento.setIdTurma(idTurma);
        return evento;
    }

    // montar material este metodo cria um material com duracao em minutos
    private ConteudoDTO material(Long id, Integer minutos) {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setId(id);
        conteudo.setTitulo("Material " + id);
        conteudo.setDuracaoEstimadaMin(minutos);
        conteudo.setPrioridade("media");
        return conteudo;
    }

    // montar resultado este metodo cria um item de viabilidade do evento ou do material
    private ViabilidadeDTO item(Long idConteudo, String nivel) {
        ViabilidadeDTO item = new ViabilidadeDTO();
        item.setIdConteudo(idConteudo);
        item.setNivel(nivel);
        return item;
    }
}
