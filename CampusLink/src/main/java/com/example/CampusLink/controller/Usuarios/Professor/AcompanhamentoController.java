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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AcompanhamentoController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(AcompanhamentoController.class);

    private static final List<String> NIVEIS = List.of("verde", "amarelo", "laranja", "vermelho", "concluido", "sem_evento");
    private static final List<String> GRAVIDADE = List.of("vermelho", "laranja", "amarelo", "verde", "concluido", "sem_evento");

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

        model.addAttribute("turmas", turmaRepository.buscarTurmasDoUsuario("professor", idProfessor));

        return "Usuarios/Professor/acompanhamento";
    }

    @GetMapping("/acompanhamento/turma/{id}")
    public String exibirAcompanhamentoTurma(@PathVariable Long id,
                                            Model model,
                                            HttpSession session,
                                            RedirectAttributes redirectAttributes) throws SQLException {

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
                List.of(id), "ATIVO", LocalDate.now(ZoneId.of("America/Sao_Paulo")).atStartOfDay());

        Map<Long, List<ConteudoDTO>> conteudosPorEvento = new HashMap<>();
        Map<Long, Map<String, Long>> contagemPorEvento = new HashMap<>();
        Map<Long, Map<Long, Map<String, Long>>> contagemPorConteudo = new HashMap<>();

        for (Evento evento : eventos) {
            List<ConteudoDTO> conteudos = conteudoRepository.listarPorEvento(evento.getId());
            conteudosPorEvento.put(evento.getId(), conteudos);
            contagemPorEvento.put(evento.getId(), novaContagem());

            Map<Long, Map<String, Long>> contagemConteudos = new HashMap<>();
            conteudos.forEach(conteudo -> contagemConteudos.put(conteudo.getId(), novaContagem()));
            contagemPorConteudo.put(evento.getId(), contagemConteudos);
        }

        List<usuarioAdminDTO> alunos = adminTurmaRepository.listarAlunosDaTurma(id);

        for (usuarioAdminDTO aluno : alunos) {
            for (ViabilidadeDTO resultado : resultadosDaTurma(aluno, id)) {
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
        model.addAttribute("conteudosPorEvento", conteudosPorEvento);
        model.addAttribute("contagemPorEvento", contagemPorEvento);
        model.addAttribute("contagemPorConteudo", contagemPorConteudo);

        return "Usuarios/Professor/acompanhamentoTurma";
    }

    @GetMapping("/acompanhamento/turma/{id}/evento/{idEvento}")
    public String exibirAcompanhamentoEvento(@PathVariable Long id,
                                             @PathVariable Long idEvento,
                                             Model model,
                                             HttpSession session,
                                             RedirectAttributes redirectAttributes) throws SQLException {

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

        Map<Long, Map<Long, String>> nivelPorAlunoEConteudo = new HashMap<>();
        Map<Long, String> situacaoPorAluno = new HashMap<>();
        Map<Long, Long> concluidosPorAluno = new HashMap<>();

        for (usuarioAdminDTO aluno : alunos) {
            Map<Long, String> niveis = new HashMap<>();
            String situacao = "sem_evento";
            long concluidos = 0;

            for (ViabilidadeDTO resultado : resultadosDaTurma(aluno, id)) {
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
        model.addAttribute("conteudos", conteudos);
        model.addAttribute("alunos", alunosOrdenados);
        model.addAttribute("nivelPorAlunoEConteudo", nivelPorAlunoEConteudo);
        model.addAttribute("situacaoPorAluno", situacaoPorAluno);
        model.addAttribute("concluidosPorAluno", concluidosPorAluno);

        return "Usuarios/Professor/acompanhamentoEvento";
    }

    private TurmaDTO turmaDoProfessor(String idProfessor, Long idTurma) throws SQLException {
        return turmaRepository.buscarTurmasDoUsuario("professor", idProfessor).stream()
                .filter(turmaProfessor -> idTurma.equals(turmaProfessor.getId()))
                .findFirst()
                .orElse(null);
    }

    private List<ViabilidadeDTO> resultadosDaTurma(usuarioAdminDTO aluno, Long idTurma) {
        return viabilidadeService.calcularParaAluno(aluno.getIdPerfil()).stream()
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
