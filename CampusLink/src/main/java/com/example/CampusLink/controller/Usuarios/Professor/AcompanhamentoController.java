package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.dto.ViabilidadeDTO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.AdminTurmaRepository;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.EventoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ViabilidadeService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class AcompanhamentoController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(AcompanhamentoController.class);

    private static final List<String> NIVEIS = List.of("verde", "amarelo", "vermelho", "concluido", "sem_evento");
    private static final List<String> GRAVIDADE = List.of("atrasado", "vermelho", "amarelo", "verde", "concluido", "sem_evento");

    private final ProfessorRepository professorRepository;
    private final TurmaRepository turmaRepository;
    private final AdminTurmaRepository adminTurmaRepository;
    private final EventoRepository eventoRepository;
    private final ConteudoRepository conteudoRepository;
    private final ViabilidadeService viabilidadeService;

    @GetMapping("/acompanhamento")
    public String exibirAcompanhamento(Model model, HttpSession session) throws SQLException {

        String redirecionamento = verificarAcesso(session, "exibirAcompanhamento");
        if (redirecionamento != null) {
            return redirecionamento;
        }

        String idProfessor = buscarIdProfessor(session);
        if (idProfessor == null) {
            return "redirect:/login";
        }

        List<TurmaDTO> turmas = turmaRepository.buscarTurmasDoUsuario("professor", idProfessor);
        model.addAttribute("turmas", TurmaRepository.ordenarPorNome(turmas));
        model.addAttribute("turmasRecentes",
                turmaRepository.recentesDentre(session.getAttribute("email2FA").toString(), turmas, 5));

        return "Usuarios/Professor/acompanhamento";
    }

    @GetMapping("/acompanhamento/turma/{id}")
    public String exibirAcompanhamentoTurma(@PathVariable Long id,
                                            Model model,
                                            HttpSession session,
                                            RedirectAttributes redirectAttributes) throws SQLException {

        long inicio = System.currentTimeMillis();

        String redirecionamento = verificarAcesso(session, "exibirAcompanhamentoTurma");
        if (redirecionamento != null) {
            return redirecionamento;
        }

        String idProfessor = buscarIdProfessor(session);
        if (idProfessor == null) {
            return "redirect:/login";
        }

        TurmaDTO turma = turmaDoProfessor(idProfessor, id);

        if (turma == null) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Você não tem acesso a esta turma.");
            return "redirect:/acompanhamento";
        }

        List<Evento> eventos = eventoRepository.findByIdTurmaInAndStatusAndInicioGreaterThanEqualOrderByInicioAsc(
                List.of(id), "ATIVO", LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));

        Map<Long, List<ConteudoDTO>> conteudosPorEvento =
                conteudoRepository.listarPorEventos(eventos.stream().map(Evento::getId).toList());
        Map<Long, Map<String, Long>> contagemPorEvento = new HashMap<>();
        Map<Long, Map<Long, Map<String, Long>>> contagemPorConteudo = new HashMap<>();

        for (Evento evento : eventos) {
            List<ConteudoDTO> conteudos = conteudosPorEvento.computeIfAbsent(evento.getId(), idEvento -> new ArrayList<>());
            contagemPorEvento.put(evento.getId(), novaContagem());

            Map<Long, Map<String, Long>> contagemConteudos = new HashMap<>();
            conteudos.forEach(conteudo -> contagemConteudos.put(conteudo.getId(), novaContagem()));
            contagemPorConteudo.put(evento.getId(), contagemConteudos);
        }

        List<usuarioAdminDTO> alunos = adminTurmaRepository.listarAlunosDaTurma(id);
        Map<Long, List<ViabilidadeDTO>> resultadosPorAluno = calcularParaAlunos(alunos);
        Map<Long, List<ViabilidadeDTO>> atrasadosPorAluno =
                viabilidadeService.listarAtrasados(alunos.stream().map(usuarioAdminDTO::getIdPerfil).toList());

        Map<Long, String> tituloPorConteudo = new HashMap<>();
        Map<Long, Set<Long>> alunosVermelhosPorConteudo = new HashMap<>();
        List<Map<String, Object>> alunosComAtraso = new ArrayList<>();

        for (usuarioAdminDTO aluno : alunos) {
            long atrasadosDoAluno = resultadosDaTurma(atrasadosPorAluno, aluno, id).size();
            if (atrasadosDoAluno > 0) {
                alunosComAtraso.add(Map.of("nome", aluno.getNome(), "atrasados", atrasadosDoAluno));
            }

            for (ViabilidadeDTO resultado : resultadosDaTurma(resultadosPorAluno, aluno, id)) {
                if (resultado.getIdConteudo() != null && "vermelho".equals(resultado.getNivel())) {
                    tituloPorConteudo.put(resultado.getIdConteudo(), resultado.getTituloConteudo());
                    alunosVermelhosPorConteudo.computeIfAbsent(resultado.getIdConteudo(), idConteudo -> new HashSet<>())
                            .add(aluno.getIdPerfil());
                }

                if (resultado.getIdConteudo() == null) {
                    Map<String, Long> contagemEvento = contagemPorEvento.get(resultado.getIdEvento());
                    if (contagemEvento != null) {
                        contagemEvento.merge(resultado.getNivel(), 1L, Long::sum);
                    }
                    continue;
                }

                Map<Long, Map<String, Long>> contagemConteudos = contagemPorConteudo.get(resultado.getIdEvento());
                if (contagemConteudos != null && contagemConteudos.containsKey(resultado.getIdConteudo())) {
                    contagemConteudos.get(resultado.getIdConteudo()).merge(resultado.getNivel(), 1L, Long::sum);
                }
            }
        }

        model.addAttribute("turma", turma);
        model.addAttribute("totalAlunos", alunos.size());
        model.addAttribute("eventos", eventos);
        model.addAttribute("eventosPassados", eventoRepository.findByIdTurmaInAndStatusAndInicioLessThanOrderByInicioDesc(
                List.of(id), "ATIVO", LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))));
        model.addAttribute("conteudosPorEvento", conteudosPorEvento);
        model.addAttribute("contagemPorEvento", contagemPorEvento);
        model.addAttribute("contagemPorConteudo", contagemPorConteudo);
        model.addAttribute("conteudosCriticos", rankingCriticos(tituloPorConteudo, alunosVermelhosPorConteudo));
        model.addAttribute("alunosComAtraso", alunosComAtraso.stream()
                .sorted(Comparator.comparing((Map<String, Object> item) -> -(Long) item.get("atrasados"))
                        .thenComparing(item -> (String) item.get("nome"), String.CASE_INSENSITIVE_ORDER))
                .toList());

        registrarTempo("exibirAcompanhamentoTurma", id, alunos.size(), inicio);
        return "Usuarios/Professor/acompanhamentoTurma";
    }

    @GetMapping("/acompanhamento/turma/{id}/evento/{idEvento}")
    public String exibirAcompanhamentoEvento(@PathVariable Long id,
                                             @PathVariable Long idEvento,
                                             Model model,
                                             HttpSession session,
                                             RedirectAttributes redirectAttributes) throws SQLException {

        long inicio = System.currentTimeMillis();

        String redirecionamento = verificarAcesso(session, "exibirAcompanhamentoEvento");
        if (redirecionamento != null) {
            return redirecionamento;
        }

        String idProfessor = buscarIdProfessor(session);
        if (idProfessor == null) {
            return "redirect:/login";
        }

        TurmaDTO turma = turmaDoProfessor(idProfessor, id);
        if (turma == null) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Você não tem acesso a esta turma.");
            return "redirect:/acompanhamento";
        }

        Evento evento = eventoRepository.findById(idEvento).orElse(null);
        if (evento == null || !id.equals(evento.getIdTurma())) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Evento não encontrado nesta turma.");
            return "redirect:/acompanhamento/turma/" + id;
        }

        List<ConteudoDTO> conteudos = conteudoRepository.listarPorEvento(idEvento);
        List<usuarioAdminDTO> alunos = adminTurmaRepository.listarAlunosDaTurma(id);
        boolean eventoPassado = evento.getInicio().isBefore(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
        Map<Long, List<ViabilidadeDTO>> resultadosPorAluno = eventoPassado ? Map.of() : calcularParaAlunos(alunos);
        Map<Long, Set<Long>> conteudosFeitosPorAluno = eventoPassado
                ? conteudoRepository.listarConcluidosDosAlunos(alunos.stream().map(usuarioAdminDTO::getIdPerfil).toList())
                : Map.of();

        Map<Long, Map<Long, String>> nivelPorAlunoEConteudo = new HashMap<>();
        Map<Long, String> situacaoPorAluno = new HashMap<>();
        Map<Long, Long> concluidosPorAluno = new HashMap<>();

        for (usuarioAdminDTO aluno : alunos) {
            Map<Long, String> niveis = new HashMap<>();
            String situacao = "sem_evento";
            long concluidos = 0;

            if (eventoPassado) {
                Set<Long> feitos = conteudosFeitosPorAluno.getOrDefault(aluno.getIdPerfil(), Set.of());
                for (ConteudoDTO conteudo : conteudos) {
                    boolean feito = feitos.contains(conteudo.getId());
                    niveis.put(conteudo.getId(), feito ? "concluido" : "atrasado");
                    concluidos += feito ? 1 : 0;
                }
                if (!conteudos.isEmpty()) {
                    situacao = concluidos == conteudos.size() ? "concluido" : "atrasado";
                }
            }

            for (ViabilidadeDTO resultado : resultadosDaTurma(resultadosPorAluno, aluno, id)) {
                if (!idEvento.equals(resultado.getIdEvento())) {
                    continue;
                }
                if (resultado.getIdConteudo() == null) {
                    situacao = resultado.getNivel();
                } else {
                    niveis.put(resultado.getIdConteudo(), resultado.getNivel());
                    if ("concluido".equals(resultado.getNivel())) {
                        concluidos++;
                    }
                }
            }

            nivelPorAlunoEConteudo.put(aluno.getIdPerfil(), niveis);
            situacaoPorAluno.put(aluno.getIdPerfil(), situacao);
            concluidosPorAluno.put(aluno.getIdPerfil(), concluidos);
        }

        List<usuarioAdminDTO> alunosOrdenados = alunos.stream()
                .sorted(Comparator.comparing((usuarioAdminDTO aluno) -> GRAVIDADE.indexOf(situacaoPorAluno.get(aluno.getIdPerfil())))
                        .thenComparing(usuarioAdminDTO::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        model.addAttribute("turma", turma);
        model.addAttribute("evento", evento);
        model.addAttribute("eventoPassado", eventoPassado);
        model.addAttribute("conteudos", conteudos);
        model.addAttribute("alunos", alunosOrdenados);
        model.addAttribute("nivelPorAlunoEConteudo", nivelPorAlunoEConteudo);
        model.addAttribute("situacaoPorAluno", situacaoPorAluno);
        model.addAttribute("concluidosPorAluno", concluidosPorAluno);

        Map<Long, String> horasSemanaPorAluno = new HashMap<>();
        Map<Long, Integer> minutosSemana = viabilidadeService.minutosConcluidosRecentes(
                alunos.stream().map(usuarioAdminDTO::getIdPerfil).toList(), 7);
        alunos.forEach(aluno -> horasSemanaPorAluno.put(aluno.getIdPerfil(),
                viabilidadeService.formatarHoras(minutosSemana.getOrDefault(aluno.getIdPerfil(), 0))));
        model.addAttribute("horasSemanaPorAluno", horasSemanaPorAluno);

        registrarTempo("exibirAcompanhamentoEvento", id, alunos.size(), inicio);
        return "Usuarios/Professor/acompanhamentoEvento";
    }

    private List<Map<String, Object>> rankingCriticos(Map<Long, String> tituloPorConteudo,
                                                      Map<Long, Set<Long>> vermelhosPorConteudo) {
        List<Map<String, Object>> ranking = new ArrayList<>();

        tituloPorConteudo.forEach((idConteudo, titulo) -> {
            long vermelhos = vermelhosPorConteudo.getOrDefault(idConteudo, Set.of()).size();
            ranking.add(Map.of("titulo", titulo, "vermelhos", vermelhos));
        });

        ranking.sort(Comparator.comparing((Map<String, Object> item) -> -(Long) item.get("vermelhos"))
                .thenComparing(item -> (String) item.get("titulo"), String.CASE_INSENSITIVE_ORDER));
        return ranking;
    }

    private void registrarTempo(String operacao, Long idTurma, int quantidadeAlunos, long inicio) {
        long tempoMs = System.currentTimeMillis() - inicio;

        logger.info("Acompanhamento carregado. operacao={} turmaId={} alunos={} tempoMs={}", operacao, idTurma, quantidadeAlunos, tempoMs);
        if (logger.isInfoEnabled()) {
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", AcompanhamentoController.class.getName(), operacao, null,
                        MessageFormatter.arrayFormat("Acompanhamento carregado. operacao={} turmaId={} alunos={} tempoMs={}",
                                new Object[]{operacao, idTurma, quantidadeAlunos, tempoMs}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }

    private TurmaDTO turmaDoProfessor(String idProfessor, Long idTurma) throws SQLException {
        return turmaRepository.buscarTurmasDoUsuario("professor", idProfessor).stream()
                .filter(turmaProfessor -> idTurma.equals(turmaProfessor.getId()))
                .findFirst()
                .orElse(null);
    }

    private Map<Long, List<ViabilidadeDTO>> calcularParaAlunos(List<usuarioAdminDTO> alunos) {
        return viabilidadeService.calcularParaAlunos(alunos.stream().map(usuarioAdminDTO::getIdPerfil).toList());
    }

    private List<ViabilidadeDTO> resultadosDaTurma(Map<Long, List<ViabilidadeDTO>> resultadosPorAluno,
                                                   usuarioAdminDTO aluno,
                                                   Long idTurma) {
        return resultadosPorAluno.getOrDefault(aluno.getIdPerfil(), List.of()).stream()
                .filter(resultado -> idTurma.equals(resultado.getIdTurma()))
                .toList();
    }

    private Map<String, Long> novaContagem() {
        Map<String, Long> contagem = new LinkedHashMap<>();
        NIVEIS.forEach(nivel -> contagem.put(nivel, 0L));
        return contagem;
    }

    private String buscarIdProfessor(HttpSession session) throws SQLException {
        String idProfessor = professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString());
        return idProfessor == null || idProfessor.isBlank() ? null : idProfessor;
    }

    private String verificarAcesso(HttpSession session, String operacao) {

        if (session.getAttribute("usuarioLogado") == null || session.getAttribute("email2FA") == null) {
            return "redirect:/login";
        }

        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            logger.warn("Acesso recusado ao acompanhamento: usuário não é professor.");
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", AcompanhamentoController.class.getName(), operacao, null,
                            "Acesso recusado ao acompanhamento: usuário não é professor.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas";
        }

        return null;
    }
}
