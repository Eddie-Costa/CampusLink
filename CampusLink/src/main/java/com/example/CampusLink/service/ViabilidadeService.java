package com.example.CampusLink.service;

import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.ViabilidadeDTO;
import com.example.CampusLink.model.Disponibilidade;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import com.example.CampusLink.repository.EventoRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ViabilidadeService {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(ViabilidadeService.class);

    private static final double FOLGA_VERDE = 0.7;
    private static final int TETO_DIARIO_HORAS = 8;
    private static final int DURACAO_PADRAO_MIN = 60;

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM");
    private static final List<String> ORDEM_NIVEIS = List.of("concluido", "verde", "amarelo", "laranja", "vermelho");

    private final EventoRepository eventoRepository;
    private final ConteudoRepository conteudoRepository;
    private final TurmaRepository turmaRepository;
    private final DisponibilidadeRepository disponibilidadeRepository;

    public List<ViabilidadeDTO> calcularParaAluno(Long idAluno) {
        return calcularParaAlunos(List.of(idAluno)).getOrDefault(idAluno, List.of());
    }

    public Map<Long, List<ViabilidadeDTO>> calcularParaAlunos(List<Long> idsAlunos) {
        Map<Long, List<ViabilidadeDTO>> resultadoPorAluno = new HashMap<>();

        if (idsAlunos.isEmpty()) {
            return resultadoPorAluno;
        }

        try {
            Map<Long, List<Long>> turmasPorAluno = turmaRepository.buscarTurmasDosAlunos(idsAlunos);

            Set<Long> idsTurmas = new HashSet<>();
            turmasPorAluno.values().forEach(idsTurmas::addAll);

            LocalDate hoje = LocalDate.now(FUSO);
            List<Evento> eventos = idsTurmas.isEmpty()
                    ? List.of()
                    : eventoRepository.findByIdTurmaInAndStatusAndInicioGreaterThanEqualOrderByInicioAsc(
                    idsTurmas, "ATIVO", hoje.atStartOfDay());

            Map<Long, List<ConteudoDTO>> conteudosPorEvento =
                    conteudoRepository.listarPorEventos(eventos.stream().map(Evento::getId).toList());
            Map<Long, Set<Long>> concluidosPorAluno = conteudoRepository.listarConcluidosDosAlunos(idsAlunos);

            Map<Long, Map<LocalDate, Integer>> horasPorAluno = new HashMap<>();
            for (Disponibilidade disponibilidade : disponibilidadeRepository.findAllByIdAlunoIn(idsAlunos)) {
                horasPorAluno.computeIfAbsent(disponibilidade.getIdAluno(), idAluno -> new HashMap<>())
                        .merge(disponibilidade.getData(), disponibilidade.getHorasDisponiveis(), Integer::sum);
            }

            for (Long idAluno : idsAlunos) {
                List<Long> turmasAluno = turmasPorAluno.getOrDefault(idAluno, List.of());
                List<Evento> eventosAluno = eventos.stream()
                        .filter(evento -> turmasAluno.contains(evento.getIdTurma()))
                        .toList();

                resultadoPorAluno.put(idAluno, calcular(idAluno, hoje, eventosAluno, conteudosPorEvento,
                        concluidosPorAluno.getOrDefault(idAluno, Set.of()),
                        horasPorAluno.getOrDefault(idAluno, Map.of())));
            }

            return resultadoPorAluno;

        } catch (SQLException e) {
            logger.error("Erro ao calcular viabilidade. alunos={}", idsAlunos, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));

                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ViabilidadeService.class.getName(), "calcularParaAlunos", null,
                            MessageFormatter.arrayFormat("Erro ao calcular viabilidade. alunos={}", new Object[]{idsAlunos}).getMessage(), null, excecaoLogBD.toString());

                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            throw new RuntimeException("Não foi possível calcular a viabilidade dos alunos.", e);
        }
    }

    List<ViabilidadeDTO> calcular(Long idAluno,
                                  LocalDate hoje,
                                  List<Evento> eventos,
                                  Map<Long, List<ConteudoDTO>> conteudosPorEvento,
                                  Set<Long> concluidos,
                                  Map<LocalDate, Integer> horasPorDia) {

        List<Evento> eventosOrdenados = eventos.stream()
                .sorted(Comparator.comparing(Evento::getInicio)
                        .thenComparing(evento -> pesoPrioridade(evento.getPrioridade())))
                .toList();

        List<ViabilidadeDTO> resultado = new ArrayList<>();
        Map<Long, Integer> cargaAteConteudo = new HashMap<>();
        int cargaAcumulada = 0;

        for (Evento evento : eventosOrdenados) {
            LocalDate dataEvento = evento.getInicio().toLocalDate();
            int capacidade = capacidadeAte(hoje, dataEvento, horasPorDia);
            int maximo = (int) Math.max(0, ChronoUnit.DAYS.between(hoje, dataEvento)) * TETO_DIARIO_HORAS * 60;

            List<ConteudoDTO> conteudos = new ArrayList<>(conteudosPorEvento.getOrDefault(evento.getId(), List.of()));
            conteudos.sort(Comparator.comparing(conteudo -> pesoPrioridade(conteudo.getPrioridade())));

            String piorNivel = "concluido";

            for (ConteudoDTO conteudo : conteudos) {
                ViabilidadeDTO item = novoResultado(idAluno, evento);
                item.setIdConteudo(conteudo.getId());
                item.setTituloConteudo(conteudo.getTitulo());
                item.setCapacidadeMin(capacidade);

                if (concluidos.contains(conteudo.getId())) {
                    item.setNivel("concluido");
                    item.setMotivo("Conteúdo concluído.");
                } else {
                    if (!cargaAteConteudo.containsKey(conteudo.getId())) {
                        cargaAcumulada += duracaoDe(conteudo);
                        cargaAteConteudo.put(conteudo.getId(), cargaAcumulada);
                    }
                    int carga = cargaAteConteudo.get(conteudo.getId());
                    item.setCargaMin(carga);
                    item.setNivel(classificar(carga, capacidade, maximo));
                    item.setMotivo(montarMotivo(item));
                }

                piorNivel = pior(piorNivel, item.getNivel());
                resultado.add(item);
            }

            ViabilidadeDTO resumoEvento = novoResultado(idAluno, evento);
            resumoEvento.setCargaMin(cargaAcumulada);
            resumoEvento.setCapacidadeMin(capacidade);
            if (conteudos.isEmpty()) {
                resumoEvento.setNivel("verde");
                resumoEvento.setMotivo("Nenhum conteúdo associado a este evento.");
            } else {
                resumoEvento.setNivel(piorNivel);
                resumoEvento.setMotivo("concluido".equals(piorNivel)
                        ? "Todos os conteúdos deste evento foram concluídos."
                        : montarMotivo(resumoEvento));
            }
            resultado.add(resumoEvento);
        }

        return resultado;
    }

    public ViabilidadeDTO proximoEventoPendente(List<ViabilidadeDTO> resultados) {
        ViabilidadeDTO proximo = null;

        for (ViabilidadeDTO resultado : resultados) {
            if (resultado.getIdConteudo() != null) {
                continue;
            }
            if (proximo == null || ("concluido".equals(proximo.getNivel()) && !"concluido".equals(resultado.getNivel()))) {
                proximo = resultado;
            }
        }

        return proximo;
    }

    private String classificar(int cargaMin, int capacidadeMin, int maximoMin) {
        if (cargaMin <= capacidadeMin * FOLGA_VERDE) {
            return "verde";
        }
        if (cargaMin <= capacidadeMin) {
            return "amarelo";
        }
        if (cargaMin <= maximoMin) {
            return "laranja";
        }
        return "vermelho";
    }

    private int capacidadeAte(LocalDate hoje, LocalDate dataEvento, Map<LocalDate, Integer> horasPorDia) {
        int horas = 0;
        for (LocalDate dia = hoje; dia.isBefore(dataEvento); dia = dia.plusDays(1)) {
            horas += horasPorDia.getOrDefault(dia, 0);
        }
        return horas * 60;
    }

    private int duracaoDe(ConteudoDTO conteudo) {
        return conteudo.getDuracaoEstimadaMin() != null ? conteudo.getDuracaoEstimadaMin() : DURACAO_PADRAO_MIN;
    }

    private int pesoPrioridade(String prioridade) {
        if (prioridade == null) {
            return 1;
        }
        return switch (prioridade.trim().toUpperCase()) {
            case "ALTA" -> 0;
            case "BAIXA" -> 2;
            default -> 1;
        };
    }

    private String pior(String nivelAtual, String novoNivel) {
        return ORDEM_NIVEIS.indexOf(novoNivel) > ORDEM_NIVEIS.indexOf(nivelAtual) ? novoNivel : nivelAtual;
    }

    private ViabilidadeDTO novoResultado(Long idAluno, Evento evento) {
        ViabilidadeDTO item = new ViabilidadeDTO();
        item.setIdAluno(idAluno);
        item.setIdTurma(evento.getIdTurma());
        item.setIdEvento(evento.getId());
        item.setNomeEvento(evento.getNome());
        item.setDataEvento(evento.getInicio());
        return item;
    }

    private String montarMotivo(ViabilidadeDTO item) {
        String texto = "Faltam " + formatarHoras(item.getCargaMin()) + " de estudo e você tem "
                + formatarHoras(item.getCapacidadeMin()) + " disponíveis até "
                + item.getDataEvento().format(FORMATO_DATA) + ".";

        return switch (item.getNivel()) {
            case "laranja" -> texto + " Cadastre mais horas de disponibilidade.";
            case "vermelho" -> texto + " Não há tempo suficiente até o evento.";
            default -> texto;
        };
    }

    private String formatarHoras(int minutos) {
        int horas = minutos / 60;
        int resto = minutos % 60;

        if (horas == 0 && resto == 0) {
            return "0h";
        }
        if (horas == 0) {
            return resto + "min";
        }
        return resto == 0 ? horas + "h" : horas + "h" + String.format("%02d", resto);
    }
}
