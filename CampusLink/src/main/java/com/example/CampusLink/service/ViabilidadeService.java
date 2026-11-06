// planejamento academico avalia a carga de estudo antes dos eventos
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// viabilidade de estudo esta classe calcula riscos atrasos prioridades e distribuicao de horas por aluno
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

    // calcular risco do aluno este metodo consulta a viabilidade de estudo de um unico aluno
    public List<ViabilidadeDTO> calcularParaAluno(Long idAluno) {
        return calcularParaAlunos(List.of(idAluno)).getOrDefault(idAluno, List.of());
    }

    // calcular risco de varios alunos este metodo carrega os dados em conjunto e avalia cada participante
    public Map<Long, List<ViabilidadeDTO>> calcularParaAlunos(List<Long> idsAlunos) {
        Map<Long, List<ViabilidadeDTO>> resultadoPorAluno = new HashMap<>();

        // calcular viabilidade ignora a consulta quando nao existem alunos
        if (idsAlunos.isEmpty()) {
            return resultadoPorAluno;
        }

        // calcular viabilidade dos alunos tenta carregar os dados e calcular a situacao de cada aluno
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

    // listar materiais atrasados este metodo identifica conteudos nao concluidos com prazo encerrado nas turmas dos alunos
    public Map<Long, List<ViabilidadeDTO>> listarAtrasados(List<Long> idsAlunos) {
        Map<Long, List<ViabilidadeDTO>> atrasadosPorAluno = new HashMap<>();

        // listar atrasos ignora a consulta quando nao existem alunos
        if (idsAlunos.isEmpty()) {
            return atrasadosPorAluno;
        }

        // listar conteudos atrasados tenta reunir eventos prazos e progresso para identificar atrasos de cada aluno
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
                    // filtrar eventos do aluno ignora eventos de turmas em que o aluno nao participa
                    if (!turmasAluno.contains(evento.getIdTurma())) {
                        continue;
                    }
                    for (ConteudoDTO conteudo : conteudosPorEvento.getOrDefault(evento.getId(), List.of())) {
                        // filtrar conteudos atrasados ignora materiais concluidos sem prazo encerrado ou ja listados
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

    // consultar minutos concluidos recentemente este metodo soma duracoes estimadas usando o valor padrao quando necessario
    public Map<Long, Integer> minutosConcluidosRecentes(List<Long> idsAlunos, int dias) {
        // calcular ritmo de estudos consulta os minutos concluidos pelos alunos no periodo
        try {
            return conteudoRepository.minutosConcluidosRecentes(idsAlunos, dias, DURACAO_PADRAO_MIN);
        } catch (SQLException e) {
            registrarErro("minutosConcluidosRecentes", "Erro ao calcular ritmo de estudos. alunos={}", idsAlunos, e);
            throw new RuntimeException("Não foi possível calcular o ritmo de estudos.", e);
        }
    }

    // calcular minutos diarios necessarios este metodo divide a carga restante pelos dias ate o evento
    public int minutosNecessariosPorDia(ViabilidadeDTO resumoEvento) {
        long dias = ChronoUnit.DAYS.between(LocalDate.now(FUSO), resumoEvento.getDataEvento().toLocalDate());
        return dias <= 0 ? resumoEvento.getCargaMin() : (int) Math.ceil(resumoEvento.getCargaMin() / (double) dias);
    }

    // registrar erro no planejamento este metodo grava a falha junto aos alunos envolvidos
    private void registrarErro(String metodo, String mensagem, List<Long> idsAlunos, SQLException e) {
        logger.error(mensagem, idsAlunos, e);

        // registro de erros confere se esta habilitado ao registrar erro no planejamento
        if (logger.isErrorEnabled()) {
            // gravar historico ao registrar erro no planejamento tenta salvar o registro no banco e informa falhas
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

    // gerar plano do aluno este metodo carrega eventos materiais e disponibilidade para distribuir os estudos
    public List<SessaoEstudoDTO> gerarPlano(Long idAluno) {
        // gerar plano de estudos tenta carregar os dados do aluno e distribuir suas sessoes
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

    // distribuir plano de estudos este metodo ocupa horas livres sugere horas extras e aponta o que nao cabe
    List<SessaoEstudoDTO> planejar(LocalDate hoje,
                                   List<Evento> eventos,
                                   Map<Long, List<ConteudoDTO>> conteudosPorEvento,
                                   Set<Long> concluidos,
                                   Map<LocalDate, Integer> horasPorDia) {

        Map<LocalDate, Integer> livrePorDia = new HashMap<>();
        horasPorDia.forEach((dia, horas) -> {
            // considerar disponibilidade futura inclui apenas datas a partir de hoje
            if (!dia.isBefore(hoje)) {
                livrePorDia.put(dia, horas * 60);
            }
        });

        List<SessaoEstudoDTO> plano = new ArrayList<>();
        Set<String> planejados = new HashSet<>();
        Map<LocalDate, Integer> usadoPorDia = new HashMap<>();
        Set<LocalDate> diasDeEvento = diasComEvento(eventos);

        for (Evento evento : ordenarEventos(eventos)) {
            LocalDate dataEvento = evento.getInicio().toLocalDate();

            for (ConteudoDTO conteudo : ordenarConteudos(conteudosPorEvento.getOrDefault(evento.getId(), List.of()))) {
                // planejar conteudo pendente ignora materiais concluidos ou ja planejados para a data do evento
                if (concluidos.contains(conteudo.getId()) || !planejados.add(conteudo.getId() + "@" + dataEvento)) {
                    continue;
                }

                int restante = duracaoDe(conteudo);
                for (LocalDate dia = hoje; dia.isBefore(dataEvento) && restante > 0; dia = dia.plusDays(1)) {
                    int livre = diasDeEvento.contains(dia) ? 0 : livrePorDia.getOrDefault(dia, 0);
                    // distribuir tempo de estudo ignora dias sem minutos livres
                    if (livre == 0) {
                        continue;
                    }
                    int minutos = Math.min(livre, restante);
                    livrePorDia.put(dia, livre - minutos);
                    usadoPorDia.merge(dia, minutos, Integer::sum);
                    restante -= minutos;
                    plano.add(novaSessao(dia, evento, conteudo, minutos));
                }

                // sugerir horas extras procura horarios adicionais quando ainda resta tempo de estudo
                if (restante > 0) {
                    restante = sugerirHoras(hoje, evento, conteudo, restante, usadoPorDia, diasDeEvento, plano);
                }

                // registrar estudo sem data inclui o tempo restante que nao coube no calendario
                if (restante > 0) {
                    plano.add(novaSessao(null, evento, conteudo, restante));
                }
            }
        }

        plano.sort(Comparator.comparing(SessaoEstudoDTO::getData, Comparator.nullsLast(Comparator.naturalOrder())));
        return plano;
    }

    // sugerir horas extras este metodo distribui a carga restante respeitando o limite diario e os dias de evento
    private int sugerirHoras(LocalDate hoje, Evento evento, ConteudoDTO conteudo, int restante,
                             Map<LocalDate, Integer> usadoPorDia, Set<LocalDate> diasDeEvento, List<SessaoEstudoDTO> plano) {
        int teto = TETO_DIARIO_HORAS * 60;
        List<LocalDate> dias = new ArrayList<>();
        int espacoTotal = 0;

        for (LocalDate dia = hoje; dia.isBefore(evento.getInicio().toLocalDate()); dia = dia.plusDays(1)) {
            // sugerir dia de estudo ignora dias que ja possuem eventos
            if (diasDeEvento.contains(dia)) {
                continue;
            }
            dias.add(dia);
            espacoTotal += Math.max(0, teto - usadoPorDia.getOrDefault(dia, 0));
        }

        int sugerir = Math.min(restante, espacoTotal);
        // encerrar sugestao de horas retorna o tempo restante quando nao ha minutos a sugerir
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

            // acrescentar sessao sugerida registra uma sessao quando existe tempo disponivel na cota
            if (cota > 0) {
                usadoPorDia.merge(dia, cota, Integer::sum);
                SessaoEstudoDTO sessao = novaSessao(dia, evento, conteudo, cota);
                sessao.setSugestao(true);
                plano.add(sessao);
            }
        }

        return restante - sugerir;
    }

    // calcular risco acumulado este metodo compara a carga dos materiais com o tempo disponivel ate cada evento
    List<ViabilidadeDTO> calcular(Long idAluno,
                                  LocalDate hoje,
                                  List<Evento> eventos,
                                  Map<Long, List<ConteudoDTO>> conteudosPorEvento,
                                  Set<Long> concluidos,
                                  Map<LocalDate, Integer> horasPorDia) {

        List<ViabilidadeDTO> resultado = new ArrayList<>();
        Map<String, Integer> cargaAteConteudo = new HashMap<>();
        Map<LocalDate, Map<String, Integer>> cargaPorEventoNoDia = new HashMap<>();
        int cargaAcumulada = 0;
        Set<LocalDate> diasDeEvento = diasComEvento(eventos);

        for (Evento evento : ordenarEventos(eventos)) {
            LocalDate dataEvento = evento.getInicio().toLocalDate();
            int cargaAntesDoEvento = cargaAcumulada;
            String complemento = descreverOutrosEventosDoDia(cargaPorEventoNoDia.getOrDefault(dataEvento, Map.of()));
            int capacidade = capacidadeAte(hoje, dataEvento, horasPorDia, diasDeEvento);
            int maximo = diasDeEstudoAte(hoje, dataEvento, diasDeEvento) * TETO_DIARIO_HORAS * 60;

            List<ConteudoDTO> conteudos = ordenarConteudos(conteudosPorEvento.getOrDefault(evento.getId(), List.of()));

            String piorNivel = "concluido";

            for (ConteudoDTO conteudo : conteudos) {
                ViabilidadeDTO item = novoResultado(idAluno, evento);
                item.setIdConteudo(conteudo.getId());
                item.setTituloConteudo(conteudo.getTitulo());
                item.setCapacidadeMin(capacidade);

                // classificar conteudo concluido marca como concluido ou calcula o risco do material pendente
                if (concluidos.contains(conteudo.getId())) {
                    item.setNivel("concluido");
                    item.setMotivo("Conteúdo concluído.");
                } else {
                    // acumular carga de estudo conta o tempo do conteudo uma vez para cada data de evento
                    if (!cargaAteConteudo.containsKey(conteudo.getId() + "@" + dataEvento)) {
                        cargaAcumulada += duracaoDe(conteudo);
                        cargaAteConteudo.put(conteudo.getId() + "@" + dataEvento, cargaAcumulada);
                    }
                    int carga = cargaAteConteudo.get(conteudo.getId() + "@" + dataEvento);
                    item.setCargaMin(carga);
                    item.setNivel(classificar(carga, capacidade, maximo));
                    item.setMotivo(montarMotivo(item, complemento));
                }

                piorNivel = pior(piorNivel, item.getNivel());
                resultado.add(item);
            }

            ViabilidadeDTO resumoEvento = novoResultado(idAluno, evento);
            resumoEvento.setCargaMin(cargaAcumulada);
            resumoEvento.setCapacidadeMin(capacidade);
            // resumir risco do evento usa verde sem conteudos ou o pior nivel dos materiais
            if (conteudos.isEmpty()) {
                resumoEvento.setNivel("verde");
                resumoEvento.setMotivo("Nenhum conteúdo associado a este evento.");
            } else {
                resumoEvento.setNivel(piorNivel);
                resumoEvento.setMotivo("concluido".equals(piorNivel)
                        ? "Todos os conteúdos deste evento foram concluídos."
                        : montarMotivo(resumoEvento, complemento));
            }
            resultado.add(resumoEvento);

            // acumular carga do evento registra o tempo adicional de estudo exigido pelo evento
            if (cargaAcumulada > cargaAntesDoEvento) {
                cargaPorEventoNoDia.computeIfAbsent(dataEvento, dia -> new LinkedHashMap<>())
                        .merge(evento.getNome(), cargaAcumulada - cargaAntesDoEvento, Integer::sum);
            }
        }

        return resultado;
    }

    // listar prioridades de estudo este metodo seleciona materiais vermelhos antes dos amarelos sem repeticao
    public List<ViabilidadeDTO> listarPrioridades(List<ViabilidadeDTO> resultados) {
        List<ViabilidadeDTO> prioridades = new ArrayList<>();
        Set<Long> jaListados = new HashSet<>();

        for (String nivel : List.of("vermelho", "amarelo")) {
            for (ViabilidadeDTO resultado : resultados) {
                // listar prioridades inclui conteudos do nivel escolhido sem repetir materiais
                if (resultado.getIdConteudo() != null && nivel.equals(resultado.getNivel())
                        && jaListados.add(resultado.getIdConteudo())) {
                    prioridades.add(resultado);
                }
            }
        }

        return prioridades;
    }

    // buscar proximo evento pendente este metodo prefere o primeiro resumo ainda nao concluido
    public ViabilidadeDTO proximoEventoPendente(List<ViabilidadeDTO> resultados) {
        ViabilidadeDTO proximo = null;

        for (ViabilidadeDTO resultado : resultados) {
            // buscar proximo evento ignora resultados individuais de conteudos
            if (resultado.getIdConteudo() != null) {
                continue;
            }
            // escolher evento pendente prefere um evento ainda nao concluido
            if (proximo == null || ("concluido".equals(proximo.getNivel()) && !"concluido".equals(resultado.getNivel()))) {
                proximo = resultado;
            }
        }

        return proximo;
    }

    // dados carregados do planejamento este registro reune turmas eventos materiais conclusoes e disponibilidade dos alunos
    private record DadosAlunos(Map<Long, List<Long>> turmasPorAluno,
                               List<Evento> eventos,
                               Map<Long, List<ConteudoDTO>> conteudosPorEvento,
                               Map<Long, Set<Long>> concluidosPorAluno,
                               Map<Long, Map<LocalDate, Integer>> horasPorAluno) {

        // listar eventos do aluno carregado este metodo separa os eventos ligados as turmas do participante
        List<Evento> eventosDo(Long idAluno) {
            List<Long> turmasAluno = turmasPorAluno.getOrDefault(idAluno, List.of());
            return eventos.stream().filter(evento -> turmasAluno.contains(evento.getIdTurma())).toList();
        }

        // consultar conclusoes carregadas este metodo retorna os materiais concluidos do aluno ou um conjunto vazio
        Set<Long> concluidosDo(Long idAluno) {
            return concluidosPorAluno.getOrDefault(idAluno, Set.of());
        }

        // consultar horas carregadas este metodo retorna a disponibilidade diaria do aluno ou um mapa vazio
        Map<LocalDate, Integer> horasDo(Long idAluno) {
            return horasPorAluno.getOrDefault(idAluno, Map.of());
        }
    }

    // carregar dados do planejamento este metodo reune turmas eventos futuros conteudos conclusoes e horas dos alunos
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

    // ordenar eventos para estudo este metodo prioriza dia depois importancia e horario
    private List<Evento> ordenarEventos(List<Evento> eventos) {
        return eventos.stream()
                .sorted(Comparator.comparing((Evento evento) -> evento.getInicio().toLocalDate())
                        .thenComparing(evento -> pesoPrioridade(evento.getPrioridade()))
                        .thenComparing(Evento::getInicio))
                .toList();
    }

    // ordenar conteudos para estudo este metodo organiza os materiais pelo peso da prioridade
    private List<ConteudoDTO> ordenarConteudos(List<ConteudoDTO> conteudos) {
        return conteudos.stream()
                .sorted(Comparator.comparing(conteudo -> pesoPrioridade(conteudo.getPrioridade())))
                .toList();
    }

    // montar sessao de estudo este metodo relaciona dia evento conteudo e minutos previstos
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

    // classificar risco de estudo este metodo escolhe verde amarelo ou vermelho conforme carga e capacidade
    private String classificar(int cargaMin, int capacidadeMin, int maximoMin) {
        // classificar risco verde usa verde quando o estudo cabe na disponibilidade
        if (cargaMin <= capacidadeMin) {
            return "verde";
        }
        // classificar risco amarelo usa amarelo quando o estudo cabe no limite ampliado
        if (cargaMin <= maximoMin) {
            return "amarelo";
        }
        return "vermelho";
    }

    // calcular capacidade ate o evento este metodo soma horas livres anteriores ao prazo sem dias de evento
    private int capacidadeAte(LocalDate hoje, LocalDate dataEvento, Map<LocalDate, Integer> horasPorDia, Set<LocalDate> diasDeEvento) {
        int horas = 0;
        for (LocalDate dia = hoje; dia.isBefore(dataEvento); dia = dia.plusDays(1)) {
            // calcular capacidade de estudo soma as horas apenas em dias sem eventos
            if (!diasDeEvento.contains(dia)) {
                horas += horasPorDia.getOrDefault(dia, 0);
            }
        }
        return horas * 60;
    }

    // contar dias de estudo este metodo conta as datas anteriores ao prazo sem dias de evento
    private int diasDeEstudoAte(LocalDate hoje, LocalDate dataEvento, Set<LocalDate> diasDeEvento) {
        int dias = 0;
        for (LocalDate dia = hoje; dia.isBefore(dataEvento); dia = dia.plusDays(1)) {
            // contar dias de estudo considera apenas dias sem eventos
            if (!diasDeEvento.contains(dia)) {
                dias++;
            }
        }
        return dias;
    }

    // identificar dias de evento este metodo reune as datas de inicio sem repeticao
    private Set<LocalDate> diasComEvento(List<Evento> eventos) {
        Set<LocalDate> dias = new HashSet<>();
        eventos.forEach(evento -> dias.add(evento.getInicio().toLocalDate()));
        return dias;
    }

    // consultar duracao para estudo este metodo usa os minutos do material ou a duracao padrao
    public int duracaoDe(ConteudoDTO conteudo) {
        return conteudo.getDuracaoEstimadaMin() != null ? conteudo.getDuracaoEstimadaMin() : DURACAO_PADRAO_MIN;
    }

    // pesar prioridade de estudo este metodo coloca alta antes de media e baixa
    private int pesoPrioridade(String prioridade) {
        // definir peso da prioridade usa o peso padrao quando nao ha prioridade
        if (prioridade == null) {
            return 1;
        }
        return switch (prioridade.trim().toUpperCase()) {
            case "ALTA" -> 0;
            case "BAIXA" -> 2;
            default -> 1;
        };
    }

    // escolher maior risco este metodo retorna o nivel mais grave entre os dois informados
    private String pior(String nivelAtual, String novoNivel) {
        return ORDEM_NIVEIS.indexOf(novoNivel) > ORDEM_NIVEIS.indexOf(nivelAtual) ? novoNivel : nivelAtual;
    }

    // preparar resultado de viabilidade este metodo associa aluno turma e dados do evento
    private ViabilidadeDTO novoResultado(Long idAluno, Evento evento) {
        ViabilidadeDTO item = new ViabilidadeDTO();
        item.setIdAluno(idAluno);
        item.setIdTurma(evento.getIdTurma());
        item.setIdEvento(evento.getId());
        item.setNomeEvento(evento.getNome());
        item.setDataEvento(evento.getInicio());
        return item;
    }

    // explicar carga do mesmo dia este metodo resume as horas exigidas por outros eventos na mesma data
    private String descreverOutrosEventosDoDia(Map<String, Integer> cargaPorEvento) {
        // descrever eventos do dia retorna vazio quando nao existem cargas de outros eventos
        if (cargaPorEvento.isEmpty()) {
            return "";
        }

        int total = cargaPorEvento.values().stream().mapToInt(Integer::intValue).sum();
        // descrever unico evento mostra o nome e o tempo quando existe apenas outro evento
        if (cargaPorEvento.size() == 1) {
            return " (incluindo " + formatarHoras(total) + " de " + cargaPorEvento.keySet().iterator().next() + ", no mesmo dia)";
        }
        return " (incluindo " + formatarHoras(total) + " de outros " + cargaPorEvento.size() + " eventos no mesmo dia)";
    }

    // explicar risco calculado este metodo descreve a carga o tempo livre e a necessidade de mais horas
    private String montarMotivo(ViabilidadeDTO item, String complemento) {
        String texto = "Faltam " + formatarHoras(item.getCargaMin()) + " de estudo" + complemento + " e você tem "
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

    // formatar tempo de estudo este metodo transforma minutos em horas e minutos para exibicao
    public String formatarHoras(int minutos) {
        int horas = minutos / 60;
        int resto = minutos % 60;

        // formatar tempo vazio apresenta zero horas quando nao ha minutos
        if (horas == 0 && resto == 0) {
            return "0h";
        }
        // formatar tempo curto mostra apenas minutos quando nao completa uma hora
        if (horas == 0) {
            return resto + "min";
        }
        return resto == 0 ? horas + "h" : horas + "h" + String.format("%02d", resto);
    }
}
