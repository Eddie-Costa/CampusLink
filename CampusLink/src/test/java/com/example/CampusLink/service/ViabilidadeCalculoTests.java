// verificacao do calculo de viabilidade confere o corte por data e o calculo guardado por aluno
package com.example.CampusLink.service;

import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.ViabilidadeDTO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import com.example.CampusLink.repository.EventoRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes do calculo de viabilidade esta classe garante que as otimizacoes nao mudam o resultado mostrado ao aluno
class ViabilidadeCalculoTests {

    private TurmaRepository turmas;
    private EventoRepository eventos;
    private ConteudoRepository conteudos;
    private ViabilidadeService viabilidade;
    private LocalDate hoje;

    // preparar testes da viabilidade este metodo cria o servico com os repositorios simulados
    @BeforeEach
    void preparar() {
        turmas = mock(TurmaRepository.class);
        eventos = mock(EventoRepository.class);
        conteudos = mock(ConteudoRepository.class);
        viabilidade = new ViabilidadeService(eventos, conteudos, turmas, mock(DisponibilidadeRepository.class));
        ReflectionTestUtils.setField(viabilidade, "UsuarioRepository", mock(UsuarioRepository.class));
        hoje = LocalDate.now();
    }

    // testar corte por data este teste confere que eventos posteriores nao mudam o risco dos eventos anteriores
    @Test
    void cortarEventosPosterioresNaoMudaRiscoDosAnteriores() {
        // preparar dois eventos, o segundo depois do dia de corte, e pouco tempo livre para gerar risco
        Evento prova = evento(1L, hoje.plusDays(3));
        Evento trabalho = evento(2L, hoje.plusDays(10));
        Map<Long, List<ConteudoDTO>> materiais = Map.of(1L, List.of(conteudo(11L, 120)), 2L, List.of(conteudo(22L, 300)));
        Map<LocalDate, Integer> horas = Map.of(hoje.plusDays(1), 1, hoje.plusDays(2), 1);

        // executar o calculo completo e o calculo cortado no dia da prova
        List<ViabilidadeDTO> completo = viabilidade.calcular(7L, hoje, List.of(prova, trabalho), materiais, Set.of(), horas);
        List<ViabilidadeDTO> cortado = viabilidade.calcular(7L, hoje, List.of(prova), materiais, Set.of(), horas);

        // verificar que a prova tem exatamente o mesmo resultado nos dois calculos
        assertTrue(completo.stream().anyMatch(item -> item.getIdEvento().equals(2L)));
        assertEquals(resumir(completo, 1L), resumir(cortado, 1L));
    }

    // testar calculo guardado este teste confere que o mesmo aluno nao recarrega os dados ate o calculo ser esquecido
    @Test
    void guardaCalculoDoAlunoAteSerEsquecido() throws Exception {
        // preparar um aluno com uma turma e um evento futuro
        Evento prova = evento(1L, hoje.plusDays(3));
        when(turmas.buscarTurmasDosAlunos(any())).thenReturn(Map.of(5L, List.of(100L)));
        when(eventos.findByIdTurmaInAndStatusAndInicioGreaterThanEqualOrderByInicioAsc(any(), eq("ATIVO"), any()))
                .thenReturn(List.of(prova));
        when(conteudos.listarPorEventos(any())).thenReturn(Map.of(1L, List.of(conteudo(11L, 60))));
        when(conteudos.listarConcluidosDosAlunos(any())).thenReturn(Map.of());

        // executar riscos e plano em seguida, como a tela do plano de estudos faz
        viabilidade.calcularParaAluno(5L);
        viabilidade.gerarPlano(5L);

        // verificar que os dados foram carregados uma unica vez para as duas consultas
        verify(turmas, times(1)).buscarTurmasDosAlunos(any());

        // executar de novo depois de esquecer o aluno, como acontece quando ele conclui um conteudo
        viabilidade.esquecerAluno(5L);
        viabilidade.calcularParaAluno(5L);

        // verificar que o calculo foi refeito com os dados atualizados
        verify(turmas, times(2)).buscarTurmasDosAlunos(any());
    }

    // montar evento de teste este metodo cria um evento da turma 100 as dezenove horas do dia informado
    private Evento evento(Long id, LocalDate dia) {
        Evento evento = new Evento("Evento " + id, dia.atTime(19, 0), dia.atTime(21, 0), null, "ALTA");
        ReflectionTestUtils.setField(evento, "id", id);
        evento.setIdTurma(100L);
        return evento;
    }

    // montar conteudo de teste este metodo cria um material com a duracao informada em minutos
    private ConteudoDTO conteudo(Long id, int minutos) {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setId(id);
        conteudo.setTitulo("Conteúdo " + id);
        conteudo.setDuracaoEstimadaMin(minutos);
        return conteudo;
    }

    // resumir resultados do evento este metodo junta os campos mostrados na tela para comparar os calculos
    private List<String> resumir(List<ViabilidadeDTO> resultados, Long idEvento) {
        return resultados.stream().filter(item -> idEvento.equals(item.getIdEvento()))
                .map(item -> item.getIdConteudo() + "|" + item.getNivel() + "|" + item.getCargaMin() + "|"
                        + item.getCapacidadeMin() + "|" + item.getMotivo())
                .toList();
    }
}
