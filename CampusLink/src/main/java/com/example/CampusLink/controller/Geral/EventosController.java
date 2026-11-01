package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.DisponibilidadeService;
import com.example.CampusLink.service.EventoService;
import com.example.CampusLink.service.ViabilidadeService;
import com.example.CampusLink.repository.UsuarioRepository;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/eventos")
@RequiredArgsConstructor
public class EventosController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(EventosController.class);
    private final EventoService eventoService;
    private final DisponibilidadeService disponibilidadeService;
    private final ConteudoService conteudoService;
    private final AlunoRepository alunoRepository;
    private final ProfessorRepository professorRepository;
    private final TurmaRepository turmaRepository;
    private final ViabilidadeService viabilidadeService;

    // mostra a tela do calendário
    @GetMapping
    public String exibirCalendario(Model model, HttpSession session) throws SQLException {

        if (!usuarioPassouPeloLoginE2FA(session)) {

            logger.warn("Acesso recusado ao calendario");
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", EventosController.class.getName(), "exibirCalendario", null,
                            "Acesso recusado ao calendario", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        carregarDadosDaPagina(model, session);
        logger.debug("Calendario exibido para {}", obterTipoUsuario(session));
        if (logger.isDebugEnabled()) {
            try {
                UsuarioRepository.InserirLogsNoBD(null, "DEBUG", EventosController.class.getName(), "exibirCalendario", null,
                        MessageFormatter.arrayFormat("Calendario exibido para {}", new Object[]{obterTipoUsuario(session)}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        return "Geral/calendario-eventos";
    }


    @GetMapping("/cadastrar")
    public String exibirFormularioCadastro(Model model, HttpSession session) throws SQLException {

        if (!usuarioPassouPeloLoginE2FA(session)) {return "redirect:/login";}

        carregarDadosDaPagina(model, session);
        return "Geral/cadastrar-eventos";
    }


    // carrega os conteúdos da turma escolhida
    @GetMapping("/conteudos")
    @ResponseBody
    public List<ConteudoDTO> listarConteudosDaTurma(@RequestParam("turmaId") Long turmaId, HttpSession session
    ) throws SQLException {

        if (!usuarioPassouPeloLoginE2FA(session) || !usuarioEhProfessor(session)) {

            return List.of();
        }

        Long idProfessor = buscarIdProfessor(session);

        if (idProfessor == null) {return List.of();
        }

        if (!professorTemAcessoATurma(idProfessor, turmaId
        )) {

            logger.warn("Professor {} tentou acessar a turma {}", idProfessor, turmaId);
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", EventosController.class.getName(), "listarConteudosDaTurma", null,
                            MessageFormatter.arrayFormat("Professor {} tentou acessar a turma {}", new Object[]{idProfessor, turmaId}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return List.of();
        }

        List<ConteudoDTO> conteudos = conteudoService.listarPorTurma(turmaId);
        Map<Long, LocalDateTime> prazosEncerrados =
                conteudoService.listarPrazosEncerrados(conteudos.stream().map(ConteudoDTO::getId).toList());
        conteudos.forEach(conteudo -> conteudo.setPrazoEncerradoEm(prazosEncerrados.get(conteudo.getId())));

        return conteudos;
    }


    // valida os dados antes de salvar
    @PostMapping("/cadastrar")
    public String cadastrarEvento(

            @RequestParam("nome") String nome,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "descricao", required = false) String descricao,
            @RequestParam("inicio")
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime inicio,
            @RequestParam("fim")
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime fim,
            @RequestParam(value = "prioridade", required = false, defaultValue = "MEDIA") String prioridade,
            @RequestParam("turmaId") Long turmaId,
            @RequestParam(value = "conteudoIds", required = false) List<Long> conteudoIds, HttpSession session, RedirectAttributes redirectAttributes

    ) throws SQLException {

        if (!usuarioPassouPeloLoginE2FA(session)) {

            return "redirect:/login";
        }

        if (!usuarioEhProfessor(session)) {

            return "redirect:/eventos";
        }

        Long idProfessor = buscarIdProfessor(session);

        if (idProfessor == null) {

            redirectAttributes.addFlashAttribute("erro", "Nao foi possivel identificar o professor");
            return "redirect:/eventos/cadastrar";
        }

        if (nome == null || nome.isBlank()) {

            redirectAttributes.addFlashAttribute("erro", "Informe o nome do evento");
            return "redirect:/eventos/cadastrar";
        }

        String erroHorario = EventoService.validarHorario(inicio, fim);
        if (erroHorario != null) {
            redirectAttributes.addFlashAttribute("erro", erroHorario);
            return "redirect:/eventos/cadastrar";
        }

        if (!professorTemAcessoATurma(idProfessor, turmaId)) {

            logger.warn("Cadastro de evento recusado: professor sem acesso à turma. professorId={} turmaId={}", idProfessor, turmaId);
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", EventosController.class.getName(), "cadastrarEvento", null,
                            MessageFormatter.arrayFormat("Cadastro de evento recusado: professor sem acesso à turma. professorId={} turmaId={}", new Object[]{idProfessor, turmaId}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("erro", "Voce nao possui acesso a turma selecionada");
            return "redirect:/eventos/cadastrar";
        }

        List<ConteudoDTO> conteudosDaTurma = conteudoService.listarPorTurma(
                turmaId);

        List<Long> idsConteudosDaTurma = conteudosDaTurma.stream().map(ConteudoDTO::getId).toList();

        if (conteudoIds != null) {

            boolean conteudoInvalido =
                    conteudoIds.stream().anyMatch(idConteudo -> !idsConteudosDaTurma.contains(idConteudo));

            if (conteudoInvalido) {

                logger.warn("Cadastro de evento recusado: conteúdo não pertence à turma. professorId={} turmaId={} conteudoIds={}", idProfessor, turmaId, conteudoIds);
                if (logger.isWarnEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", EventosController.class.getName(), "cadastrarEvento", null,
                                MessageFormatter.arrayFormat("Cadastro de evento recusado: conteúdo não pertence à turma. professorId={} turmaId={} conteudoIds={}", new Object[]{idProfessor, turmaId, conteudoIds}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("erro", "Um dos conteudos nao pertence a turma");
                return "redirect:/eventos/cadastrar";
            }
        }

        Evento evento = new Evento(nome, inicio, fim, null, prioridade);
        evento.setTipo(tipo);
        evento.setDescricao(descricao);
        evento.setIdProfessor(idProfessor);
        evento.setIdTurma(turmaId);
        eventoService.adicionarEvento(evento, conteudoIds);
        logger.info("Evento cadastrado idProfessor={} idTurma={}", idProfessor, turmaId);
        if (logger.isInfoEnabled()) {
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", EventosController.class.getName(), "cadastrarEvento", null,
                        MessageFormatter.arrayFormat("Evento cadastrado idProfessor={} idTurma={}", new Object[]{idProfessor, turmaId}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        redirectAttributes.addFlashAttribute("mensagem", "Evento cadastrado com sucesso!");
        return "redirect:/eventos";
    }


    // apaga somente eventos do professor logado
    @PostMapping("/excluir")
    public String excluirEvento(

            @RequestParam("eventoId") Long eventoId, HttpSession session, RedirectAttributes redirectAttributes
    ) throws SQLException {

        if (!usuarioPassouPeloLoginE2FA(session)) {

            return "redirect:/login";
        }

        if (!usuarioEhProfessor(session)) {

            return "redirect:/eventos";
        }

        Long idProfessor = buscarIdProfessor(session);

        if (idProfessor == null) {

            redirectAttributes.addFlashAttribute("erro", "Nao foi possivel identificar o professor");
            return "redirect:/eventos";
        }

        Evento eventoEncontrado =
                eventoService.listarEventos().stream().filter(evento -> evento.getId() != null && evento.getId().equals(eventoId)
                        ).findFirst().orElse(null);

        if (eventoEncontrado == null) {

            redirectAttributes.addFlashAttribute("erro", "Evento nao encontrado");
            return "redirect:/eventos";
        }

        if (eventoEncontrado.getIdProfessor() == null || !eventoEncontrado.getIdProfessor().equals(idProfessor)) {

            logger.warn("Exclusão de evento recusada: professor não é responsável pelo evento. professorId={} eventoId={}", idProfessor, eventoId);
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", EventosController.class.getName(), "excluirEvento", null,
                            MessageFormatter.arrayFormat("Exclusão de evento recusada: professor não é responsável pelo evento. professorId={} eventoId={}", new Object[]{idProfessor, eventoId}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("erro", "Voce nao possui permissao para excluir este evento");
            return "redirect:/eventos";
        }

        eventoService.excluirEvento(eventoId);
        logger.info("Evento {} excluido pelo professor {}", eventoId, idProfessor);
        if (logger.isInfoEnabled()) {
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", EventosController.class.getName(), "excluirEvento", null,
                        MessageFormatter.arrayFormat("Evento {} excluido pelo professor {}", new Object[]{eventoId, idProfessor}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        redirectAttributes.addFlashAttribute("mensagem", "Evento excluido com sucesso!");
        return "redirect:/eventos";
    }


    @PostMapping("/cadastrar-disponibilidade")
    public String cadastrarDisponibilidade(

            @RequestParam("data")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam("horasDisponiveis") Integer horasDisponiveis, HttpSession session, RedirectAttributes redirectAttributes

    ) throws SQLException {

        if (!usuarioPassouPeloLoginE2FA(session)) {

            return "redirect:/login";
        }

        if (!usuarioEhAluno(session)) {

            return "redirect:/eventos";
        }

        Long idAluno = buscarIdAluno(session);

        if (idAluno == null) {

            redirectAttributes.addFlashAttribute("erro", "Nao foi possivel identificar o aluno");
            return "redirect:/eventos";
        }

        if (horasDisponiveis == null || horasDisponiveis < 0 || horasDisponiveis > 24) {

            redirectAttributes.addFlashAttribute("erro", "Informe uma quantidade entre 0 e 24 horas");
            return "redirect:/eventos/cadastrar";
        }

        if (data == null || data.isBefore(LocalDate.now(ZoneId.of("America/Sao_Paulo")))) {

            redirectAttributes.addFlashAttribute("erro", "Não é possível cadastrar disponibilidade em dias que já passaram");
            return "redirect:/eventos/cadastrar";
        }

        disponibilidadeService.salvarDisponibilidade(idAluno, data, horasDisponiveis);
        redirectAttributes.addFlashAttribute("mensagem", "Disponibilidade cadastrada com sucesso!");
        return "redirect:/eventos";
    }


    // carrega os dados usados nas telas

    private void carregarDadosDaPagina(Model model, HttpSession session) throws SQLException {

        String tipoUsuario = obterTipoUsuario(session);
        List<Evento> eventos = eventoService.listarEventos();
        model.addAttribute("eventos", eventos);
        model.addAttribute("tipoUsuario", tipoUsuario);
        model.addAttribute("disponibilidades", List.of());
        model.addAttribute("conteudos", List.of());
        model.addAttribute("turmasProfessor", List.of());
        model.addAttribute("planoEstudo", List.of());

        if (usuarioEhProfessor(session)) {

            Long idProfessor = buscarIdProfessor(session);

            if (idProfessor != null) {

                List<TurmaDTO> turmasProfessor = turmaRepository.buscarTurmasDoUsuario("professor", idProfessor.toString());
                model.addAttribute("turmasProfessor", turmasProfessor);
                model.addAttribute("eventos", eventosDasTurmas(eventos, turmasProfessor));
            }
        }

        if (usuarioEhAluno(session)) {

            Long idAluno = buscarIdAluno(session);

            if (idAluno != null) {

                model.addAttribute("disponibilidades", disponibilidadeService.listarPorAluno(idAluno));

                model.addAttribute("eventos",
                        eventosDasTurmas(eventos, turmaRepository.buscarTurmasDoUsuario("aluno", idAluno.toString())));

                model.addAttribute("proximoEventoRisco",
                        viabilidadeService.proximoEventoPendente(viabilidadeService.calcularParaAluno(idAluno)));

                model.addAttribute("planoEstudo", viabilidadeService.gerarPlano(idAluno));
            }
        }
    }


    private List<Evento> eventosDasTurmas(List<Evento> eventos, List<TurmaDTO> turmas) {

        List<Long> idsTurmas = turmas.stream().map(TurmaDTO::getId).toList();

        return eventos.stream().filter(evento -> idsTurmas.contains(evento.getIdTurma())).toList();
    }


    // busca os ids usando o email salvo na sessão

    private Long buscarIdAluno(HttpSession session) throws SQLException {

        Object email = session.getAttribute("email2FA");

        if (email == null) {
            return null;
        }

        String idAluno = alunoRepository.buscarPorIDAluno(email.toString());

        if (idAluno == null || idAluno.isBlank()) {
            return null;
        }

        return Long.valueOf(idAluno);
    }


    private Long buscarIdProfessor(
            HttpSession session
    ) throws SQLException {

        Object email = session.getAttribute("email2FA");

        if (email == null) {
            return null;
        }

        String idProfessor = professorRepository.buscarPorIDProfessor(email.toString());

        if (idProfessor == null || idProfessor.isBlank()) {

            return null;
        }

        return Long.valueOf(idProfessor);
    }


    private boolean professorTemAcessoATurma(Long idProfessor, Long idTurma) throws SQLException {

        if (idProfessor == null || idTurma == null) {
            return false;
        }

        List<TurmaDTO> turmasProfessor = turmaRepository.buscarTurmasDoUsuario("professor", idProfessor.toString());

        return turmasProfessor.stream().anyMatch(turma -> turma.getId() != null && turma.getId().equals(idTurma));
    }


    private boolean usuarioPassouPeloLoginE2FA(
            HttpSession session
    ) {

        return session.getAttribute("usuarioLogado") != null && session.getAttribute("email2FA") != null && !obterTipoUsuario(session).isBlank();
    }


    private String obterTipoUsuario(
            HttpSession session
    ) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");

        if (tipoUsuario == null) {
            return "";
        }

        return tipoUsuario.toString();
    }


    private boolean usuarioEhAluno(HttpSession session) {

        return "aluno".equalsIgnoreCase(obterTipoUsuario(session));
    }


    private boolean usuarioEhProfessor(HttpSession session) {

        return "professor".equalsIgnoreCase(obterTipoUsuario(session));
    }
}
