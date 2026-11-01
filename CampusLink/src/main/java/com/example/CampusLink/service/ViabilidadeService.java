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
import java.time.LocalDateTime;
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

    private static final int TETO_DIARIO_HORAS = 8;
    private static final int DURACAO_PADRAO_MIN = 60;

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM");
    private static final List<String> ORDEM_NIVEIS = List.of("concluido", "verde", "amarelo", "vermelho");

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
            LocalDate hoje = LocalDate.now(FUSO);
            DadosAlunos dados = carregar(idsAlunos, hoje);

            for (Long idAluno : idsAlunos) {
                resultadoPorAluno.put(idAluno, calcular(idAluno, hoje, dados.eventosDo(idAluno), dados.conteudosPorEvento(),
                        dados.concluidosDo(idAluno), dados.horasDo(idAluno)));
            }

            return resultadoPorAluno;

        } catch (SQLException e) {
            registrarErro("calcularParaAlunos", "Erro ao calcular viabilidade. alunos={}", idsAlunos, e);
            throw new RuntimeException("Não foi possível calcular a viabilidade dos alunos.", e);
        }
    }

    public Map<Long, List<ViabilidadeDTO>> listarAtrasados(List<Long> idsAlunos) {
        Map<Long, List<ViabilidadeDTO>> atrasadosPorAluno = new HashMap<>();

        if (idsAlunos.isEmpty()) {
            return atrasadosPorAluno;
        }

        try {
            Map<Long, List<Long>> turmasPorAluno = turmaRepository.buscarTurmasDosAlunos(idsAlunos);

            Set<Long> idsTurmas = new HashSet<>();
            turmasPorAluno.values().forEach(idsTurmas::addAll);

            LocalDateTime agora = LocalDateTime.now(FUSO);
            List<Evento> eventosPassados = idsTurmas.isEmpty()
                    ? List.of()
                    : eventoRepository.findByIdTurmaInAndStatusAndInicioLessThanOrderByInicioDesc(
                    idsTurmas, "ATIVO", agora);

            Map<Long, List<ConteudoDTO>> conteudosPorEvento =
                    conteudoRepository.listarPorEventos(eventosPassados.stream().map(Evento::getId).toList());
            Map<Long, Set<Long>> concluidosPorAluno = conteudoRepository.listarConcluidosDosAlunos(idsAlunos);

            Set<Long> idsConteudos = new HashSet<>();
            conteudosPorEvento.values().forEach(conteudos -> conteudos.forEach(conteudo -> idsConteudos.add(conteudo.getId())));
            Map<Long, LocalDateTime> prazosEncerrados =
                    conteudoRepository.listarPrazosEncerrados(new ArrayList<>(idsConteudos), agora);

            for (Long idAluno : idsAlunos) {
                List<Long> turmasAluno = turmasPorAluno.getOrDefault(idAluno, List.of());
                Set<Long> concluidos = concluidosPorAluno.getOrDefault(idAluno, Set.of());
                Set<Long> jaListados = new HashSet<>();
                List<ViabilidadeDTO> atrasados = new ArrayList<>();

                for (Evento evento : eventosPassados) {
                    if (!turmasAluno.contains(evento.getIdTurma())) {
                        continue;
                    }
                    for (ConteudoDTO conteudo : conteudosPorEvento.getOrDefault(evento.getId(), List.of())) {
                        if (concluidos.contains(conteudo.getId()) || !prazosEncerrados.containsKey(conteudo.getId())
                                || !jaListados.add(conteudo.getId())) {
                            continue;
                        }
                        ViabilidadeDTO item = novoResultado(idAluno, evento);
                        item.setIdConteudo(conteudo.getId());
                        item.setTituloConteudo(conteudo.getTitulo());
                        item.setCargaMin(duracaoDe(conteudo));
                        item.setNivel("atrasado");
                        item.setMotivo("O prazo terminou em " + evento.getInicio().format(FORMATO_DATA)
                                + " e este conteúdo não foi concluído.");
                        atrasados.add(item);
                    }
                }

                atrasadosPorAluno.put(idAluno, atrasados);
            }

            return atrasadosPorAluno;

        } catch (SQLException e) {
            registrarErro("listarAtrasados", "Erro ao listar conteúdos atrasados. alunos={}", idsAlunos, e);
            throw new RuntimeException("Não foi possível listar os conteúdos atrasados.", e);
        }
    }

    public Map<Long, Integer> minutosConcluidosRecentes(List<Long> idsAlunos, int dias) {
        try {
            return conteudoRepository.minutosConcluidosRecentes(idsAlunos, dias, DURACAO_PADRAO_MIN);
        } catch (SQLException e) {
            registrarErro("minutosConcluidosRecentes", "Erro ao calcular ritmo de estudos. alunos={}", idsAlunos, e);
            throw new RuntimeException("Não foi possível calcular o ritmo de estudos.", e);
        }
    }

    public int minutosNecessariosPorDia(ViabilidadeDTO resumoEvento) {
        long dias = ChronoUnit.DAYS.between(LocalDate.now(FUSO), resumoEvento.getDataEvento().toLocalDate());
        return dias <= 0 ? resumoEvento.getCargaMin() : (int) Math.ceil(resumoEvento.getCargaMin() / (double) dias);
    }

    private void registrarErro(String metodo, String mensagem, List<Long> idsAlunos, SQLException e) {
        logger.error(mensagem, idsAlunos, e);

        if (logger.isErrorEnabled()) {
            try {
                StringWriter excecaoLogBD = new StringWriter();
                e.printStackTrace(new PrintWriter(excecaoLogBD));

                UsuarioRepository.InserirLogsNoBD(null, "ERROR", ViabilidadeService.class.getName(), metodo, null,
                        MessageFormatter.arrayFormat(mensagem, new Object[]{idsAlunos}).getMessage(), null, excecaoLogBD.toString());

            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }

    public List<SessaoEstudoDTO> gerarPlano(Long idAluno) {
        try {
            LocalDate hoje = LocalDate.now(FUSO);
            DadosAlunos dados = carregar(List.of(idAluno), hoje);

            return planejar(hoje, dados.eventosDo(idAluno), dados.conteudosPorEvento(),
                    dados.concluidosDo(idAluno), dados.horasDo(idAluno));

        } catch (SQLException e) {
            registrarErro("gerarPlano", "Erro ao gerar plano de estudos. alunos={}", List.of(idAluno), e);
            throw new RuntimeException("Não foi possível gerar o plano de estudos.", e);
        }
    }

    List<SessaoEstudoDTO> planejar(LocalDate hoje,
                                   List<Evento> eventos,
                                   Map<Long, List<ConteudoDTO>> conteudosPorEvento,
                                   Set<Long> concluidos,
                                   Map<LocalDate, Integer> horasPorDia) {

        Map<LocalDate, Integer> livrePorDia = new HashMap<>();
        horasPorDia.forEach((dia, horas) -> {
            if (!dia.isBefore(hoje)) {
                livrePorDia.put(dia, horas * 60);
            }
        });

        List<SessaoEstudoDTO> plano = new ArrayList<>();
        Set<Long> planejados = new HashSet<>();
        Map<LocalDate, Integer> usadoPorDia = new HashMap<>();

        for (Evento evento : ordenarEventos(eventos)) {
            LocalDate dataEvento = evento.getInicio().toLocalDate();

            for (ConteudoDTO conteudo : ordenarConteudos(conteudosPorEvento.getOrDefault(evento.getId(), List.of()))) {
                if (concluidos.contains(conteudo.getId()) || !planejados.add(conteudo.getId())) {
                    continue;
                }

                int restante = duracaoDe(conteudo);
                for (LocalDate dia = hoje; dia.isBefore(dataEvento) && restante > 0; dia = dia.plusDays(1)) {
                    int livre = livrePorDia.getOrDefault(dia, 0);
                    if (livre == 0) {
                        continue;
                    }
                    int minutos = Math.min(livre, restante);
                    livrePorDia.put(dia, livre - minutos);
                    usadoPorDia.merge(dia, minutos, Integer::sum);
                    restante -= minutos;
                    plano.add(novaSessao(dia, evento, conteudo, minutos));
                }

                if (restante > 0) {
                    restante = sugerirHoras(hoje, evento, conteudo, restante, usadoPorDia, plano);
                }

                if (restante > 0) {
                    plano.add(novaSessao(null, evento, conteudo, restante));
                }
            }
        }

        plano.sort(Comparator.comparing(SessaoEstudoDTO::getData, Comparator.nullsLast(Comparator.naturalOrder())));
        return plano;
    }

    private int sugerirHoras(LocalDate hoje, Evento evento, ConteudoDTO conteudo, int restante,
                             Map<LocalDate, Integer> usadoPorDia, List<SessaoEstudoDTO> plano) {
        int teto = TETO_DIARIO_HORAS * 60;
        List<LocalDate> dias = new ArrayList<>();
        int espacoTotal = 0;

        for (LocalDate dia = hoje; dia.isBefore(evento.getInicio().toLocalDate()); dia = dia.plusDays(1)) {
            dias.add(dia);
            espacoTotal += Math.max(0, teto - usadoPorDia.getOrDefault(dia, 0));
        }

        int sugerir = Math.min(restante, espacoTotal);
        if (sugerir == 0) {
            return restante;
        }

        int nivel = 0;
        int alocado = 0;
        while (alocado < sugerir) {
            nivel++;
            alocado = 0;
            for (LocalDate dia : dias) {
                alocado += Math.max(0, nivel - usadoPorDia.getOrDefault(dia, 0));
            }
        }

        int excesso = alocado - sugerir;
        for (int indice = dias.size() - 1; indice >= 0; indice--) {
            LocalDate dia = dias.get(indice);
            int cota = Math.max(0, nivel - usadoPorDia.getOrDefault(dia, 0));
            int corte = Math.min(excesso, cota);
            cota -= corte;
            excesso -= corte;

            if (cota > 0) {
                usadoPorDia.merge(dia, cota, Integer::sum);
                SessaoEstudoDTO sessao = novaSessao(dia, evento, conteudo, cota);
                sessao.setSugestao(true);
                plano.add(sessao);
            }
        }

        return restante - sugerir;
    }

    List<ViabilidadeDTO> calcular(Long idAluno,
                                  LocalDate hoje,
                                  List<Evento> eventos,
                                  Map<Long, List<ConteudoDTO>> conteudosPorEvento,
                                  Set<Long> concluidos,
                                  Map<LocalDate, Integer> horasPorDia) {

        List<ViabilidadeDTO> resultado = new ArrayList<>();
        Map<Long, Integer> cargaAteConteudo = new HashMap<>();
        int cargaAcumulada = 0;

        for (Evento evento : ordenarEventos(eventos)) {
            LocalDate dataEvento = evento.getInicio().toLocalDate();
            int capacidade = capacidadeAte(hoje, dataEvento, horasPorDia);
            int maximo = (int) Math.max(0, ChronoUnit.DAYS.between(hoje, dataEvento)) * TETO_DIARIO_HORAS * 60;

            List<ConteudoDTO> conteudos = ordenarConteudos(conteudosPorEvento.getOrDefault(evento.getId(), List.of()));

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

    private record DadosAlunos(Map<Long, List<Long>> turmasPorAluno,
                               List<Evento> eventos,
                               Map<Long, List<ConteudoDTO>> conteudosPorEvento,
                               Map<Long, Set<Long>> concluidosPorAluno,
                               Map<Long, Map<LocalDate, Integer>> horasPorAluno) {

        List<Evento> eventosDo(Long idAluno) {
            List<Long> turmasAluno = turmasPorAluno.getOrDefault(idAluno, List.of());
            return eventos.stream().filter(evento -> turmasAluno.contains(evento.getIdTurma())).toList();
        }

        Set<Long> concluidosDo(Long idAluno) {
            return concluidosPorAluno.getOrDefault(idAluno, Set.of());
        }

        Map<LocalDate, Integer> horasDo(Long idAluno) {
            return horasPorAluno.getOrDefault(idAluno, Map.of());
        }
    }

    private DadosAlunos carregar(List<Long> idsAlunos, LocalDate hoje) throws SQLException {
        Map<Long, List<Long>> turmasPorAluno = turmaRepository.buscarTurmasDosAlunos(idsAlunos);

        Set<Long> idsTurmas = new HashSet<>();
        turmasPorAluno.values().forEach(idsTurmas::addAll);

        List<Evento> eventos = idsTurmas.isEmpty()
                ? List.of()
                : eventoRepository.findByIdTurmaInAndStatusAndInicioGreaterThanEqualOrderByInicioAsc(
                idsTurmas, "ATIVO", LocalDateTime.now(FUSO));

        Map<Long, List<ConteudoDTO>> conteudosPorEvento =
                conteudoRepository.listarPorEventos(eventos.stream().map(Evento::getId).toList());
        Map<Long, Set<Long>> concluidosPorAluno = conteudoRepository.listarConcluidosDosAlunos(idsAlunos);

        Map<Long, Map<LocalDate, Integer>> horasPorAluno = new HashMap<>();
        for (Disponibilidade disponibilidade : disponibilidadeRepository.findAllByIdAlunoIn(idsAlunos)) {
            horasPorAluno.computeIfAbsent(disponibilidade.getIdAluno(), idAluno -> new HashMap<>())
                    .merge(disponibilidade.getData(), disponibilidade.getHorasDisponiveis(), Integer::sum);
        }

        return new DadosAlunos(turmasPorAluno, eventos, conteudosPorEvento, concluidosPorAluno, horasPorAluno);
    }

    private List<Evento> ordenarEventos(List<Evento> eventos) {
        return eventos.stream()
                .sorted(Comparator.comparing(Evento::getInicio)
                        .thenComparing(evento -> pesoPrioridade(evento.getPrioridade())))
                .toList();
    }

    private List<ConteudoDTO> ordenarConteudos(List<ConteudoDTO> conteudos) {
        return conteudos.stream()
                .sorted(Comparator.comparing(conteudo -> pesoPrioridade(conteudo.getPrioridade())))
                .toList();
    }

    private SessaoEstudoDTO novaSessao(LocalDate dia, Evento evento, ConteudoDTO conteudo, int minutos) {
        SessaoEstudoDTO sessao = new SessaoEstudoDTO();
        sessao.setData(dia);
        sessao.setIdTurma(evento.getIdTurma());
        sessao.setIdEvento(evento.getId());
        sessao.setNomeEvento(evento.getNome());
        sessao.setDataEvento(evento.getInicio());
        sessao.setIdConteudo(conteudo.getId());
        sessao.setTituloConteudo(conteudo.getTitulo());
        sessao.setMinutos(minutos);
        return sessao;
    }

    private String classificar(int cargaMin, int capacidadeMin, int maximoMin) {
        if (cargaMin <= capacidadeMin) {
            return "verde";
        }
        if (cargaMin <= maximoMin) {
            return "amarelo";
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

    public int duracaoDe(ConteudoDTO conteudo) {
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
            case "amarelo" -> texto + " Adicione "
                    + (int) Math.ceil((item.getCargaMin() - item.getCapacidadeMin()) / 60.0)
                    + "h de disponibilidade até " + item.getDataEvento().minusDays(1).format(FORMATO_DATA)
                    + " para ficar possível.";
            case "vermelho" -> texto + " Não há tempo suficiente até o evento.";
            default -> texto;
        };
    }

    public String formatarHoras(int minutos) {
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
