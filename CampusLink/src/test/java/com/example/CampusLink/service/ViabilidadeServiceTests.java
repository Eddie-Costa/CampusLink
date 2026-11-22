// verificacao do calculo de viabilidade confere os niveis de risco e o plano de estudos sugerido ao aluno
package com.example.CampusLink.service;

import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.SessaoEstudoDTO;
import com.example.CampusLink.dto.ViabilidadeDTO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import com.example.CampusLink.repository.EventoRepository;
import com.example.CampusLink.repository.TurmaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

// testes da viabilidade esta classe usa datas e cargas ficticias para conferir as regras do calculo sem acessar o banco
class ViabilidadeServiceTests {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);
    private static final Long ALUNO = 7L;

    private ViabilidadeService viabilidade;
    private Map<Long, List<ConteudoDTO>> conteudosPorEvento;
    private Map<LocalDate, Integer> horasPorDia;

    // preparar testes da viabilidade este metodo cria o servico e zera eventos conteudos e disponibilidade de cada teste
    @BeforeEach
    void preparar() {
        viabilidade = new ViabilidadeService(mock(EventoRepository.class), mock(ConteudoRepository.class),
                mock(TurmaRepository.class), mock(DisponibilidadeRepository.class));
        conteudosPorEvento = new HashMap<>();
        horasPorDia = new HashMap<>();
    }

    // testar nivel possivel este teste confere que a carga dentro da disponibilidade cadastrada e classificada como possivel
    @Test
    void classificaComoPossivelQuandoCargaCabeNaDisponibilidade() {
        // preparar prova em tres dias com 2h de conteudo e 2h de disponibilidade hoje
        Evento prova = evento(1L, "Prova", 3, 19, "ALTA");
        conteudosPorEvento.put(1L, List.of(conteudo(10L, 120, "alta")));
        horasPorDia.put(HOJE, 2);

        // executar calculo da viabilidade
        List<ViabilidadeDTO> resultado = viabilidade.calcular(ALUNO, HOJE, List.of(prova), conteudosPorEvento, Set.of(), horasPorDia);

        // verificar evento e conteudo como possiveis
        assertEquals("verde", resumo(resultado, 1L).getNivel());
        assertEquals("verde", item(resultado, 1L, 10L).getNivel());
    }

    // testar nivel replanejar este teste confere a classificacao e a orientacao de quantas horas faltam cadastrar
    @Test
    void classificaComoReplanejarEInformaHorasQueFaltamCadastrar() {
        // preparar prova em tres dias com 5h de conteudo e apenas 2h de disponibilidade
        Evento prova = evento(1L, "Prova", 3, 19, "ALTA");
        conteudosPorEvento.put(1L, List.of(conteudo(10L, 300, "alta")));
        horasPorDia.put(HOJE, 2);

        // executar calculo da viabilidade
        ViabilidadeDTO resumo = resumo(viabilidade.calcular(ALUNO, HOJE, List.of(prova), conteudosPorEvento, Set.of(), horasPorDia), 1L);

        // verificar nivel replanejar e o pedido de 3h a mais ate a vespera do evento
        assertEquals("amarelo", resumo.getNivel());
        assertTrue(resumo.getMotivo().contains("Adicione 3h de disponibilidade até 12/11"), resumo.getMotivo());
    }

    // testar nivel impossivel este teste confere que a carga acima de 8h por dia de estudo e classificada como impossivel
    @Test
    void classificaComoImpossivelQuandoNemOitoHorasPorDiaBastam() {
        // preparar prova em dois dias com quase 17h de conteudo para no maximo 16h de estudo
        Evento prova = evento(1L, "Prova", 2, 19, "ALTA");
        conteudosPorEvento.put(1L, List.of(conteudo(10L, 1000, "alta")));

        // executar calculo da viabilidade
        ViabilidadeDTO resumo = resumo(viabilidade.calcular(ALUNO, HOJE, List.of(prova), conteudosPorEvento, Set.of(), horasPorDia), 1L);

        // verificar nivel impossivel e a explicacao ao aluno
        assertEquals("vermelho", resumo.getNivel());
        assertTrue(resumo.getMotivo().contains("Não há tempo suficiente"), resumo.getMotivo());
    }

    // testar dias de evento este teste confere que as horas cadastradas em dia de evento nao entram na capacidade
    @Test
    void desconsideraDisponibilidadeCadastradaEmDiaDeEvento() {
        // preparar dois eventos e 8h cadastradas justamente no dia do primeiro evento
        Evento prova = evento(1L, "Prova", 2, 19, "ALTA");
        Evento trabalho = evento(2L, "Trabalho", 4, 19, "MEDIA");
        conteudosPorEvento.put(1L, List.of(conteudo(10L, 60, "alta")));
        conteudosPorEvento.put(2L, List.of(conteudo(20L, 60, "media")));
        horasPorDia.put(HOJE, 1);
        horasPorDia.put(HOJE.plusDays(2), 8);
        horasPorDia.put(HOJE.plusDays(3), 1);

        // executar calculo da viabilidade
        ViabilidadeDTO trabalhoResumo = resumo(viabilidade.calcular(ALUNO, HOJE, List.of(prova, trabalho),
                conteudosPorEvento, Set.of(), horasPorDia), 2L);

        // verificar capacidade de 2h para o trabalho sem as 8h do dia da prova
        assertEquals(120, trabalhoResumo.getCapacidadeMin());
    }

    // testar carga acumulada este teste confere que o segundo evento soma a carga do primeiro e que conteudo repetido conta nos dois
    @Test
    void acumulaCargaEntreEventosEContaConteudoRepetidoEmCadaEvento() {
        // preparar o mesmo conteudo de 1h na prova e na prova final que ainda tem outro conteudo de 1h
        Evento prova = evento(1L, "Prova", 2, 19, "ALTA");
        Evento provaFinal = evento(2L, "Prova final", 5, 19, "ALTA");
        conteudosPorEvento.put(1L, List.of(conteudo(10L, 60, "alta")));
        conteudosPorEvento.put(2L, List.of(conteudo(10L, 60, "alta"), conteudo(20L, 60, "media")));

        // executar calculo da viabilidade
        List<ViabilidadeDTO> resultado = viabilidade.calcular(ALUNO, HOJE, List.of(prova, provaFinal),
                conteudosPorEvento, Set.of(), horasPorDia);

        // verificar 2h ate o conteudo repetido e 3h no total da prova final
        assertEquals(120, item(resultado, 2L, 10L).getCargaMin());
        assertEquals(180, resumo(resultado, 2L).getCargaMin());
        // a carga so do evento separa o que e dele do que veio da prova anterior
        assertEquals(60, resumo(resultado, 1L).getCargaEventoMin());
        assertEquals(120, resumo(resultado, 2L).getCargaEventoMin());
    }

    // testar eventos no mesmo dia este teste confere que a prioridade alta e calculada antes da media mesmo sendo mais tarde
    @Test
    void ordenaEventosDoMesmoDiaPelaPrioridadeAntesDoHorario() {
        // preparar trabalho de prioridade media pela manha e seminario de prioridade alta a noite no mesmo dia
        Evento trabalho = evento(1L, "Trabalho", 3, 10, "MEDIA");
        Evento seminario = evento(2L, "Seminario", 3, 20, "ALTA");
        conteudosPorEvento.put(1L, List.of(conteudo(10L, 60, "media")));
        conteudosPorEvento.put(2L, List.of(conteudo(20L, 120, "alta")));

        // executar calculo da viabilidade
        List<ViabilidadeDTO> resultado = viabilidade.calcular(ALUNO, HOJE, List.of(trabalho, seminario),
                conteudosPorEvento, Set.of(), horasPorDia);

        // verificar seminario calculado primeiro e trabalho somando a carga do seminario com explicacao
        assertEquals(120, resumo(resultado, 2L).getCargaMin());
        assertEquals(180, resumo(resultado, 1L).getCargaMin());
        assertTrue(resumo(resultado, 1L).getMotivo().contains("incluindo 2h de Seminario, no mesmo dia"),
                resumo(resultado, 1L).getMotivo());
    }

    // testar conteudo concluido este teste confere que o conteudo ja concluido nao entra na carga do evento
    @Test
    void naoContaConteudoConcluidoNaCarga() {
        // preparar prova com um conteudo concluido de 1h e outro pendente de 2h
        Evento prova = evento(1L, "Prova", 3, 19, "ALTA");
        conteudosPorEvento.put(1L, List.of(conteudo(10L, 60, "alta"), conteudo(20L, 120, "media")));

        // executar calculo com o primeiro conteudo concluido
        List<ViabilidadeDTO> resultado = viabilidade.calcular(ALUNO, HOJE, List.of(prova), conteudosPorEvento,
                Set.of(10L), horasPorDia);

        // verificar conteudo marcado como concluido e carga apenas do pendente
        assertEquals("concluido", item(resultado, 1L, 10L).getNivel());
        assertEquals(120, resumo(resultado, 1L).getCargaMin());
    }

    // testar plano de estudos este teste confere que a disponibilidade dos primeiros dias e usada antes das sugestoes
    @Test
    void planejaDisponibilidadeDosPrimeirosDiasAntesDeSugerirHoras() {
        // preparar prova em tres dias com 5h de conteudo e 2h cadastradas hoje e amanha
        Evento prova = evento(1L, "Prova", 3, 19, "ALTA");
        conteudosPorEvento.put(1L, List.of(conteudo(10L, 300, "alta")));
        horasPorDia.put(HOJE, 2);
        horasPorDia.put(HOJE.plusDays(1), 2);

        // executar montagem do plano
        List<SessaoEstudoDTO> plano = viabilidade.planejar(HOJE, List.of(prova), conteudosPorEvento, Set.of(), horasPorDia);

        // verificar 2h hoje e amanha na disponibilidade e 1h sugerida no dia livre restante
        assertEquals(120, minutos(plano, HOJE, false));
        assertEquals(120, minutos(plano, HOJE.plusDays(1), false));
        assertEquals(60, minutos(plano, HOJE.plusDays(2), true));
        assertTrue(plano.stream().noneMatch(sessao -> sessao.getData() == null));
    }

    // testar limite do plano este teste confere o teto de 8h por dia o excedente sem data e que dia de evento nao recebe estudo
    @Test
    void limitaSugestaoAOitoHorasPorDiaESemUsarDiaDeEvento() {
        // preparar prova amanha e entrega depois de amanha com 4h cadastradas no dia da prova
        Evento prova = evento(1L, "Prova", 1, 19, "ALTA");
        Evento entrega = evento(2L, "Entrega", 2, 19, "MEDIA");
        conteudosPorEvento.put(1L, List.of(conteudo(10L, 60, "alta")));
        conteudosPorEvento.put(2L, List.of(conteudo(20L, 1000, "media")));
        horasPorDia.put(HOJE.plusDays(1), 4);

        // executar montagem do plano
        List<SessaoEstudoDTO> plano = viabilidade.planejar(HOJE, List.of(prova, entrega), conteudosPorEvento, Set.of(), horasPorDia);

        // verificar nenhum estudo no dia da prova 8h hoje e o restante como faltou
        assertTrue(plano.stream().noneMatch(sessao -> HOJE.plusDays(1).equals(sessao.getData())));
        assertEquals(480, plano.stream().filter(sessao -> HOJE.equals(sessao.getData())).mapToInt(SessaoEstudoDTO::getMinutos).sum());
        assertEquals(580, plano.stream().filter(sessao -> sessao.getData() == null).mapToInt(SessaoEstudoDTO::getMinutos).sum());
    }

    // montar evento do teste este metodo cria um evento da turma 1 a alguns dias de hoje no horario informado
    private Evento evento(Long id, String nome, int diasAteEvento, int hora, String prioridade) {
        LocalDateTime inicio = LocalDateTime.of(HOJE.plusDays(diasAteEvento), LocalTime.of(hora, 0));
        Evento evento = new Evento(nome, inicio, inicio.plusHours(1), null, prioridade);
        evento.setIdTurma(1L);
        ReflectionTestUtils.setField(evento, "id", id);
        return evento;
    }

    // montar conteudo do teste este metodo cria um conteudo com duracao em minutos e prioridade
    private ConteudoDTO conteudo(Long id, int minutos, String prioridade) {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setId(id);
        conteudo.setTitulo("Conteudo " + id);
        conteudo.setDuracaoEstimadaMin(minutos);
        conteudo.setPrioridade(prioridade);
        return conteudo;
    }

    // localizar resumo do evento este metodo devolve a linha do evento sem conteudo associado
    private ViabilidadeDTO resumo(List<ViabilidadeDTO> resultado, Long idEvento) {
        return resultado.stream()
                .filter(linha -> idEvento.equals(linha.getIdEvento()) && linha.getIdConteudo() == null)
                .findFirst().orElseThrow();
    }

    // localizar conteudo no evento este metodo devolve a linha do conteudo dentro do evento informado
    private ViabilidadeDTO item(List<ViabilidadeDTO> resultado, Long idEvento, Long idConteudo) {
        return resultado.stream()
                .filter(linha -> idEvento.equals(linha.getIdEvento()) && idConteudo.equals(linha.getIdConteudo()))
                .findFirst().orElseThrow();
    }

    // somar minutos do dia este metodo totaliza as sessoes do dia separando disponibilidade cadastrada e sugestao
    private int minutos(List<SessaoEstudoDTO> plano, LocalDate dia, boolean sugestao) {
        return plano.stream()
                .filter(sessao -> dia.equals(sessao.getData()) && sessao.isSugestao() == sugestao)
                .mapToInt(SessaoEstudoDTO::getMinutos).sum();
    }
}
