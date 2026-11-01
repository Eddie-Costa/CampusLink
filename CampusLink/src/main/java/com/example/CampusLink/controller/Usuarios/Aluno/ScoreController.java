package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.SessaoEstudoDTO;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.dto.ViabilidadeDTO;
import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ConteudoService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class ScoreController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(ScoreController.class);

    private final AlunoRepository alunoRepository;
    private final TurmaRepository turmaRepository;
    private final ConteudoService conteudoService;
    private final ViabilidadeService viabilidadeService;

    @GetMapping("/score")
    public String exibirScore(Model model, HttpSession session) throws SQLException {

        String redirecionamento = verificarAcesso(session, "exibirScore");
        if (redirecionamento != null) {
            return redirecionamento;
        }

        Long idAluno = buscarIdAluno(session);
        if (idAluno == null) {
            return "redirect:/login";
        }

        List<TurmaDTO> turmas = turmaRepository.buscarTurmasDoUsuario("aluno", idAluno.toString());
        List<ViabilidadeDTO> resultados = viabilidadeService.calcularParaAluno(idAluno);
        List<ViabilidadeDTO> atrasados = viabilidadeService.listarAtrasados(List.of(idAluno)).getOrDefault(idAluno, List.of());
        Map<Long, ViabilidadeDTO> proximoEventoPorTurma = proximoEventoPorTurma(resultados);

        Map<Long, String> nomeTurmaPorId = new HashMap<>();
        turmas.forEach(turma -> nomeTurmaPorId.put(turma.getId(), turma.getNomeTurma()));

        Map<Long, Long> concluidosPorTurma = new HashMap<>();
        Map<Long, Integer> totalPorTurma = new HashMap<>();
        for (TurmaDTO turma : turmas) {
            List<ConteudoDTO> ativos = listarAtivos(turma.getId());
            Set<Long> concluidos = conteudoService.listarConcluidosDoAluno(idAluno, turma.getId());

            totalPorTurma.put(turma.getId(), ativos.size());
            concluidosPorTurma.put(turma.getId(), ativos.stream().filter(conteudo -> concluidos.contains(conteudo.getId())).count());
        }

        model.addAttribute("turmas", turmas);
        model.addAttribute("proximoEventoPorTurma", proximoEventoPorTurma);
        model.addAttribute("concluidosPorTurma", concluidosPorTurma);
        model.addAttribute("totalPorTurma", totalPorTurma);
        model.addAttribute("prioridades", montarPrioridades(resultados, atrasados));
        model.addAttribute("nomeTurmaPorId", nomeTurmaPorId);
        model.addAttribute("ritmo", montarRitmo(idAluno, resultados));

        return "Usuarios/Aluno/score";
    }

    @GetMapping("/score/turma/{id}")
    public String exibirScoreTurma(@PathVariable Long id,
                                   Model model,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) throws SQLException {

        String redirecionamento = verificarAcesso(session, "exibirScoreTurma");
        if (redirecionamento != null) {
            return redirecionamento;
        }

        Long idAluno = buscarIdAluno(session);
        if (idAluno == null) {
            return "redirect:/login";
        }

        TurmaDTO turma = turmaRepository.buscarTurmasDoUsuario("aluno", idAluno.toString()).stream()
                .filter(turmaAluno -> id.equals(turmaAluno.getId()))
                .findFirst()
                .orElse(null);

        if (turma == null) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Você não participa desta turma.");
            return "redirect:/score";
        }

        List<ViabilidadeDTO> resultadosTurma = viabilidadeService.calcularParaAluno(idAluno).stream()
                .filter(resultado -> id.equals(resultado.getIdTurma()))
                .toList();

        Map<Long, List<ViabilidadeDTO>> conteudosPorEvento = new HashMap<>();
        Set<Long> conteudosComEvento = new HashSet<>();
        for (ViabilidadeDTO resultado : resultadosTurma) {
            if (resultado.getIdConteudo() != null) {
                conteudosPorEvento.computeIfAbsent(resultado.getIdEvento(), idEvento -> new ArrayList<>()).add(resultado);
                conteudosComEvento.add(resultado.getIdConteudo());
            }
        }

        List<ConteudoDTO> conteudos = listarAtivos(id);
        Set<Long> concluidos = conteudoService.listarConcluidosDoAluno(idAluno, id);

        Map<Long, ViabilidadeDTO> atrasadosPorConteudo = new HashMap<>();
        viabilidadeService.listarAtrasados(List.of(idAluno)).getOrDefault(idAluno, List.of()).stream()
                .filter(atrasado -> id.equals(atrasado.getIdTurma()))
                .forEach(atrasado -> atrasadosPorConteudo.put(atrasado.getIdConteudo(), atrasado));

        Map<Long, Integer> minutosFaltandoPorEvento = new HashMap<>();
        Map<Long, Integer> minutosFaltandoPorConteudo = new HashMap<>();
        for (SessaoEstudoDTO sessao : viabilidadeService.gerarPlano(idAluno)) {
            if (sessao.getData() == null && id.equals(sessao.getIdTurma())) {
                minutosFaltandoPorEvento.merge(sessao.getIdEvento(), sessao.getMinutos(), Integer::sum);
                minutosFaltandoPorConteudo.merge(sessao.getIdConteudo(), sessao.getMinutos(), Integer::sum);
            }
        }

        Map<Long, Integer> duracaoPorConteudo = new HashMap<>();
        conteudos.forEach(conteudo -> duracaoPorConteudo.put(conteudo.getId(), viabilidadeService.duracaoDe(conteudo)));

        int duracaoPadrao = viabilidadeService.duracaoDe(new ConteudoDTO());
        Map<Long, Map<String, Object>> progressoPorEvento = new HashMap<>();
        conteudosPorEvento.forEach((idEvento, itens) -> {
            int concluidosEvento = 0;
            int minutosConcluidos = 0;
            int minutosTotal = 0;

            for (ViabilidadeDTO item : itens) {
                int duracao = duracaoPorConteudo.getOrDefault(item.getIdConteudo(), duracaoPadrao);
                minutosTotal += duracao;
                if ("concluido".equals(item.getNivel())) {
                    concluidosEvento++;
                    minutosConcluidos += duracao;
                }
            }

            progressoPorEvento.put(idEvento, Map.of(
                    "concluidos", concluidosEvento,
                    "total", itens.size(),
                    "percentual", itens.isEmpty() ? 0 : concluidosEvento * 100 / itens.size(),
                    "horasConcluidas", viabilidadeService.formatarHoras(minutosConcluidos),
                    "horasTotal", viabilidadeService.formatarHoras(minutosTotal)));
        });

        Map<Long, String> faltaPorEvento = new HashMap<>();
        minutosFaltandoPorEvento.forEach((idEvento, minutos) -> faltaPorEvento.put(idEvento, viabilidadeService.formatarHoras(minutos)));
        Map<Long, String> faltaPorConteudo = new HashMap<>();
        minutosFaltandoPorConteudo.forEach((idConteudo, minutos) -> faltaPorConteudo.put(idConteudo, viabilidadeService.formatarHoras(minutos)));

        model.addAttribute("turma", turma);
        model.addAttribute("eventos", resultadosTurma.stream().filter(resultado -> resultado.getIdConteudo() == null).toList());
        model.addAttribute("conteudos", conteudos);
        model.addAttribute("conteudosPorEvento", conteudosPorEvento);
        model.addAttribute("conteudosSemEvento", conteudos.stream().filter(conteudo -> !conteudosComEvento.contains(conteudo.getId())).toList());
        model.addAttribute("conteudosConcluidos", concluidos);
        model.addAttribute("atrasadosPorConteudo", atrasadosPorConteudo);
        model.addAttribute("faltaPorEvento", faltaPorEvento);
        model.addAttribute("progressoPorEvento", progressoPorEvento);
        model.addAttribute("faltaPorConteudo", faltaPorConteudo);
        model.addAttribute("totalConcluidos", conteudos.stream().filter(conteudo -> concluidos.contains(conteudo.getId())).count());

        return "Usuarios/Aluno/scoreTurma";
    }

    private String verificarAcesso(HttpSession session, String operacao) {

        if (session.getAttribute("usuarioLogado") == null || session.getAttribute("email2FA") == null) {
            return "redirect:/login";
        }

        if (!"aluno".equals(session.getAttribute("tipoUsuario"))) {
            logger.warn("Acesso recusado ao score: usuário não é aluno.");
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", ScoreController.class.getName(), operacao, null,
                            "Acesso recusado ao score: usuário não é aluno.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas";
        }

        return null;
    }

    private Long buscarIdAluno(HttpSession session) throws SQLException {
        String idAluno = alunoRepository.buscarPorIDAluno(session.getAttribute("email2FA").toString());
        return idAluno == null || idAluno.isBlank() ? null : Long.valueOf(idAluno);
    }

    private List<ConteudoDTO> listarAtivos(Long idTurma) {
        return conteudoService.listarPorTurma(idTurma).stream()
                .filter(conteudo -> "ativo".equals(conteudo.getStatus()))
                .toList();
    }

    private Map<String, Object> montarRitmo(Long idAluno, List<ViabilidadeDTO> resultados) {
        int minutosSemana = viabilidadeService.minutosConcluidosRecentes(List.of(idAluno), 7).getOrDefault(idAluno, 0);
        int mediaDiaria = minutosSemana / 7;

        Map<String, Object> ritmo = new HashMap<>();
        ritmo.put("horasSemana", viabilidadeService.formatarHoras(minutosSemana));
        ritmo.put("mediaDiaria", viabilidadeService.formatarHoras(mediaDiaria));

        ViabilidadeDTO proximo = viabilidadeService.proximoEventoPendente(resultados);
        if (proximo != null && !"concluido".equals(proximo.getNivel()) && proximo.getCargaMin() > 0) {
            int necessarioPorDia = viabilidadeService.minutosNecessariosPorDia(proximo);
            ritmo.put("proximoEvento", proximo);
            ritmo.put("necessarioPorDia", viabilidadeService.formatarHoras(necessarioPorDia));
            ritmo.put("noRitmo", mediaDiaria >= necessarioPorDia);
        }

        return ritmo;
    }

    private List<ViabilidadeDTO> montarPrioridades(List<ViabilidadeDTO> resultados, List<ViabilidadeDTO> atrasados) {
        List<ViabilidadeDTO> prioridades = new ArrayList<>(atrasados);
        Set<Long> jaListados = new HashSet<>();
        atrasados.forEach(atrasado -> jaListados.add(atrasado.getIdConteudo()));

        for (String nivel : List.of("vermelho", "amarelo")) {
            for (ViabilidadeDTO resultado : resultados) {
                if (resultado.getIdConteudo() != null && nivel.equals(resultado.getNivel())
                        && jaListados.add(resultado.getIdConteudo())) {
                    prioridades.add(resultado);
                }
            }
        }

        return prioridades;
    }

    private Map<Long, ViabilidadeDTO> proximoEventoPorTurma(List<ViabilidadeDTO> resultados) {
        Map<Long, List<ViabilidadeDTO>> resultadosPorTurma = new HashMap<>();
        for (ViabilidadeDTO resultado : resultados) {
            resultadosPorTurma.computeIfAbsent(resultado.getIdTurma(), idTurma -> new ArrayList<>()).add(resultado);
        }

        Map<Long, ViabilidadeDTO> proximoEvento = new HashMap<>();
        resultadosPorTurma.forEach((idTurma, resultadosTurma) ->
                proximoEvento.put(idTurma, viabilidadeService.proximoEventoPendente(resultadosTurma)));

        return proximoEvento;
    }
}
