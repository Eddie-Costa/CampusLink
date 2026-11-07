// agenda academica recebe pedidos de eventos e disponibilidade de estudo
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.dto.ViabilidadeDTO;
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
import java.util.Set;
import java.util.stream.Collectors;

// controle do calendario esta classe separa acoes de alunos e professores e prepara os dados da agenda
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
    private final ConteudoRepository conteudoRepository;


    // abrir calendario este metodo carrega os eventos para o usuario com acesso confirmado
    @GetMapping
    public String exibirCalendario(Model model, HttpSession session) throws SQLException {

        // proteger calendario exige login e verificacao antes de listar eventos
        if (!usuarioPassouPeloLoginE2FA(session)) {

            logger.warn("Acesso recusado ao calendario");
            // registro de avisos confere se esta habilitado ao abrir calendario
            if (logger.isWarnEnabled()) {
                // gravar historico ao abrir calendario tenta salvar o registro no banco e informa falhas
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
        // registro de detalhes confere se esta habilitado ao abrir calendario
        if (logger.isDebugEnabled()) {
            // gravar historico ao abrir calendario tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "DEBUG", EventosController.class.getName(), "exibirCalendario", null,
                        MessageFormatter.arrayFormat("Calendario exibido para {}", new Object[]{obterTipoUsuario(session)}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        return "Geral/calendario-eventos";
    }


    // abrir cadastro de evento este metodo prepara as opcoes do formulario apos conferir o acesso
    @GetMapping("/cadastrar")
    public String exibirFormularioCadastro(Model model, HttpSession session) throws SQLException {

        // proteger cadastro de evento exige login e verificacao antes de abrir o formulario
        if (!usuarioPassouPeloLoginE2FA(session)) {return "redirect:/login";}

        carregarDadosDaPagina(model, session);
        return "Geral/cadastrar-eventos";
    }



    // listar conteudos para evento este metodo confere a turma do professor e informa os prazos encerrados
    @GetMapping("/conteudos")
    @ResponseBody
    public List<ConteudoDTO> listarConteudosDaTurma(@RequestParam("turmaId") Long turmaId, HttpSession session
    ) throws SQLException {

        // proteger conteudos do evento retorna lista vazia para sessoes invalidas ou perfis sem permissao
        if (!usuarioPassouPeloLoginE2FA(session) || !usuarioEhProfessor(session)) {

            return List.of();
        }

        Long idProfessor = buscarIdProfessor(session);

        // identificar professor dos conteudos retorna lista vazia quando o professor nao foi encontrado
        if (idProfessor == null) {return List.of();
        }

        // conferir turma dos conteudos recusa a lista quando o professor nao tem acesso a turma
        if (!professorTemAcessoATurma(idProfessor, turmaId
        )) {

            logger.warn("Professor {} tentou acessar a turma {}", idProfessor, turmaId);
            // registro de avisos confere se esta habilitado ao listar conteudos para evento
            if (logger.isWarnEnabled()) {
                // gravar historico ao listar conteudos para evento tenta salvar o registro no banco e informa falhas
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



    // cadastrar evento pelo professor este metodo valida turma horarios e conteudos antes de salvar
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

        // autenticar cadastro de evento envia sessoes nao verificadas para o login
        if (!usuarioPassouPeloLoginE2FA(session)) {

            return "redirect:/login";
        }

        // restringir cadastro de evento retorna ao calendario quando o usuario nao e professor
        if (!usuarioEhProfessor(session)) {

            return "redirect:/eventos";
        }

        Long idProfessor = buscarIdProfessor(session);

        // exigir professor do evento informa a falta de identificacao e retorna ao cadastro
        if (idProfessor == null) {

            redirectAttributes.addFlashAttribute("erro", "Nao foi possivel identificar o professor");
            return "redirect:/eventos/cadastrar";
        }

        // exigir nome do evento pede o nome quando o texto esta ausente ou vazio
        if (nome == null || nome.isBlank()) {

            redirectAttributes.addFlashAttribute("erro", "Informe o nome do evento");
            return "redirect:/eventos/cadastrar";
        }

        String erroHorario = EventoService.validarHorario(inicio, fim);
        // validar intervalo do evento reapresenta o cadastro com a mensagem de horario invalido
        if (erroHorario != null) {
            redirectAttributes.addFlashAttribute("erro", erroHorario);
            return "redirect:/eventos/cadastrar";
        }

        // impedir evento fora da turma recusa o cadastro quando o professor nao tem acesso
        if (!professorTemAcessoATurma(idProfessor, turmaId)) {

            logger.warn("Cadastro de evento recusado: professor sem acesso à turma. professorId={} turmaId={}", idProfessor, turmaId);
            // registro de avisos confere se esta habilitado ao cadastrar evento pelo professor
            if (logger.isWarnEnabled()) {
                // gravar historico ao cadastrar evento pelo professor tenta salvar o registro no banco e informa falhas
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

        // conferir materiais selecionados verifica o vinculo com a turma quando foram enviados conteudos
        if (conteudoIds != null) {

            boolean conteudoInvalido =
                    conteudoIds.stream().anyMatch(idConteudo -> !idsConteudosDaTurma.contains(idConteudo));

            // recusar material de outra turma interrompe o cadastro se algum conteudo nao pertence a turma
            if (conteudoInvalido) {

                logger.warn("Cadastro de evento recusado: conteúdo não pertence à turma. professorId={} turmaId={} conteudoIds={}", idProfessor, turmaId, conteudoIds);
                // registro de avisos confere se esta habilitado ao cadastrar evento pelo professor
                if (logger.isWarnEnabled()) {
                    // gravar historico ao cadastrar evento pelo professor tenta salvar o registro no banco e informa falhas
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
        turmaRepository.registrarAlteracao(session.getAttribute("email2FA").toString(), turmaId);
        logger.info("Evento cadastrado idProfessor={} idTurma={}", idProfessor, turmaId);
        // registro de informacoes confere se esta habilitado ao cadastrar evento pelo professor
        if (logger.isInfoEnabled()) {
            // gravar historico ao cadastrar evento pelo professor tenta salvar o registro no banco e informa falhas
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



    // delete evento pelo professor este metodo confere o responsavel antes de excluir o evento
    @PostMapping("/excluir")
    public String excluirEvento(

            @RequestParam("eventoId") Long eventoId, HttpSession session, RedirectAttributes redirectAttributes
    ) throws SQLException {

        // autenticar delete evento exige login e verificacao antes da exclusao
        if (!usuarioPassouPeloLoginE2FA(session)) {

            return "redirect:/login";
        }

        // restringir delete evento retorna ao calendario quando o perfil nao e professor
        if (!usuarioEhProfessor(session)) {

            return "redirect:/eventos";
        }

        Long idProfessor = buscarIdProfessor(session);

        // identificar responsavel pela exclusao interrompe quando o professor nao foi encontrado
        if (idProfessor == null) {

            redirectAttributes.addFlashAttribute("erro", "Nao foi possivel identificar o professor");
            return "redirect:/eventos";
        }

        Evento eventoEncontrado =
                eventoService.listarEventos().stream().filter(evento -> evento.getId() != null && evento.getId().equals(eventoId)
                        ).findFirst().orElse(null);

        // localizar evento para exclusao informa quando o evento ja nao existe
        if (eventoEncontrado == null) {

            redirectAttributes.addFlashAttribute("erro", "Evento nao encontrado");
            return "redirect:/eventos";
        }

        // autorizar delete evento recusa a exclusao quando o professor nao e o responsavel
        if (eventoEncontrado.getIdProfessor() == null || !eventoEncontrado.getIdProfessor().equals(idProfessor)) {

            logger.warn("Exclusão de evento recusada: professor não é responsável pelo evento. professorId={} eventoId={}", idProfessor, eventoId);
            // registro de avisos confere se esta habilitado ao delete evento pelo professor
            if (logger.isWarnEnabled()) {
                // gravar historico ao delete evento pelo professor tenta salvar o registro no banco e informa falhas
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
        // registro de informacoes confere se esta habilitado ao delete evento pelo professor
        if (logger.isInfoEnabled()) {
            // gravar historico ao delete evento pelo professor tenta salvar o registro no banco e informa falhas
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


    // cadastrar disponibilidade pela tela este metodo valida a data e as horas livres do aluno
    @PostMapping("/cadastrar-disponibilidade")
    public String cadastrarDisponibilidade(

            @RequestParam("data")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam("horasDisponiveis") Integer horasDisponiveis, HttpSession session, RedirectAttributes redirectAttributes

    ) throws SQLException {

        // autenticar disponibilidade exige login e verificacao antes de salvar horarios
        if (!usuarioPassouPeloLoginE2FA(session)) {

            return "redirect:/login";
        }

        // restringir disponibilidade aceita o cadastro apenas pelo perfil de aluno
        if (!usuarioEhAluno(session)) {

            return "redirect:/eventos";
        }

        Long idAluno = buscarIdAluno(session);

        // identificar aluno da disponibilidade interrompe quando o codigo do aluno nao foi encontrado
        if (idAluno == null) {

            redirectAttributes.addFlashAttribute("erro", "Nao foi possivel identificar o aluno");
            return "redirect:/eventos";
        }

        // validar horas disponiveis exige uma quantidade entre zero e vinte e quatro
        if (horasDisponiveis == null || horasDisponiveis < 0 || horasDisponiveis > 24) {

            redirectAttributes.addFlashAttribute("erro", "Informe uma quantidade entre 0 e 24 horas");
            return "redirect:/eventos/cadastrar";
        }

        // validar dia disponivel recusa data ausente ou anterior ao dia atual em sao paulo
        if (data == null || data.isBefore(LocalDate.now(ZoneId.of("America/Sao_Paulo")))) {

            redirectAttributes.addFlashAttribute("erro", "Não é possível cadastrar disponibilidade em dias que já passaram");
            return "redirect:/eventos/cadastrar";
        }

        disponibilidadeService.salvarDisponibilidade(idAluno, data, horasDisponiveis);
        redirectAttributes.addFlashAttribute("mensagem", "Disponibilidade cadastrada com sucesso!");
        return "redirect:/eventos";
    }




    // carregar calendario por perfil este metodo reune eventos turmas e o plano de estudos do aluno
    private void carregarDadosDaPagina(Model model, HttpSession session) throws SQLException {

        String tipoUsuario = obterTipoUsuario(session);
        List<Evento> eventos = eventoService.listarEventos();
        model.addAttribute("eventos", eventos);
        model.addAttribute("tipoUsuario", tipoUsuario);
        model.addAttribute("disponibilidades", List.of());
        model.addAttribute("conteudos", List.of());
        model.addAttribute("turmasProfessor", List.of());
        model.addAttribute("planoEstudo", List.of());
        model.addAttribute("conteudosPorEvento", Map.of());
        model.addAttribute("conteudosConcluidos", Set.of());
        model.addAttribute("prioridades", List.of());
        model.addAttribute("nomeTurmaPorId", Map.of());
        model.addAttribute("agora", LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));

        // preparar calendario do professor busca o codigo quando a sessao pertence a esse perfil
        if (usuarioEhProfessor(session)) {

            Long idProfessor = buscarIdProfessor(session);

            // carregar turmas do professor limita os eventos as turmas do professor identificado
            if (idProfessor != null) {

                List<TurmaDTO> turmasProfessor = turmaRepository.buscarTurmasDoUsuario("professor", idProfessor.toString());
                model.addAttribute("turmasProfessor", turmasProfessor);
                model.addAttribute("eventos", eventosDasTurmas(eventos, turmasProfessor));
            }
        }

        // preparar calendario do aluno busca o codigo quando a sessao pertence a esse perfil
        if (usuarioEhAluno(session)) {

            Long idAluno = buscarIdAluno(session);

            // carregar estudos do aluno inclui disponibilidade eventos progresso prioridades e plano quando o aluno existe
            if (idAluno != null) {

                model.addAttribute("disponibilidades", disponibilidadeService.listarPorAluno(idAluno));

                List<TurmaDTO> turmasAluno = turmaRepository.buscarTurmasDoUsuario("aluno", idAluno.toString());
                List<Evento> eventosDoAluno = eventosDasTurmas(eventos, turmasAluno);
                model.addAttribute("eventos", eventosDoAluno);
                model.addAttribute("conteudosPorEvento",
                        conteudoRepository.listarPorEventos(eventosDoAluno.stream().map(Evento::getId).toList()));
                model.addAttribute("conteudosConcluidos",
                        conteudoRepository.listarConcluidosDosAlunos(List.of(idAluno)).getOrDefault(idAluno, Set.of()));

                List<ViabilidadeDTO> resultados = viabilidadeService.calcularParaAluno(idAluno);
                model.addAttribute("proximoEventoRisco", viabilidadeService.proximoEventoPendente(resultados));
                model.addAttribute("prioridades", viabilidadeService.listarPrioridades(resultados));
                model.addAttribute("nomeTurmaPorId", turmasAluno.stream()
                        .collect(Collectors.toMap(TurmaDTO::getId, TurmaDTO::getNomeTurma, (nome, repetido) -> nome)));

                model.addAttribute("planoEstudo", viabilidadeService.gerarPlano(idAluno));
            }
        }
    }


    // filtrar eventos das turmas este metodo separa os eventos ligados as turmas informadas
    private List<Evento> eventosDasTurmas(List<Evento> eventos, List<TurmaDTO> turmas) {

        List<Long> idsTurmas = turmas.stream().map(TurmaDTO::getId).toList();

        return eventos.stream().filter(evento -> idsTurmas.contains(evento.getIdTurma())).toList();
    }




    // buscar aluno do calendario este metodo consulta o identificador pelo email da sessao
    private Long buscarIdAluno(HttpSession session) throws SQLException {

        Object email = session.getAttribute("email2FA");

        // exigir email do aluno encerra a busca de identificacao quando falta o email
        if (email == null) {
            return null;
        }

        String idAluno = alunoRepository.buscarPorIDAluno(email.toString());

        // conferir codigo do aluno evita converter uma identificacao vazia
        if (idAluno == null || idAluno.isBlank()) {
            return null;
        }

        return Long.valueOf(idAluno);
    }


    // buscar professor do calendario este metodo consulta o identificador pelo email da sessao
    private Long buscarIdProfessor(
            HttpSession session
    ) throws SQLException {

        Object email = session.getAttribute("email2FA");

        // exigir email do professor encerra a busca de identificacao quando falta o email
        if (email == null) {
            return null;
        }

        String idProfessor = professorRepository.buscarPorIDProfessor(email.toString());

        // conferir codigo do professor evita converter uma identificacao vazia
        if (idProfessor == null || idProfessor.isBlank()) {

            return null;
        }

        return Long.valueOf(idProfessor);
    }


    // verificar acesso a turma no calendario este metodo confere o vinculo do professor
    private boolean professorTemAcessoATurma(Long idProfessor, Long idTurma) throws SQLException {

        // validar consulta de acesso retorna falso quando falta professor ou turma
        if (idProfessor == null || idTurma == null) {
            return false;
        }

        List<TurmaDTO> turmasProfessor = turmaRepository.buscarTurmasDoUsuario("professor", idProfessor.toString());

        return turmasProfessor.stream().anyMatch(turma -> turma.getId() != null && turma.getId().equals(idTurma));
    }


    // validar login do calendario este metodo confere os dados de acesso presentes na sessao
    private boolean usuarioPassouPeloLoginE2FA(
            HttpSession session
    ) {

        return session.getAttribute("usuarioLogado") != null && session.getAttribute("email2FA") != null && !obterTipoUsuario(session).isBlank();
    }


    // consultar perfil do calendario este metodo le o tipo de usuario salvo na sessao
    private String obterTipoUsuario(
            HttpSession session
    ) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");

        // tratar perfil ausente usa texto vazio quando a sessao nao informa o tipo de usuario
        if (tipoUsuario == null) {
            return "";
        }

        return tipoUsuario.toString();
    }


    // verificar aluno no calendario este metodo compara o perfil conectado com aluno
    private boolean usuarioEhAluno(HttpSession session) {

        return "aluno".equalsIgnoreCase(obterTipoUsuario(session));
    }


    // verificar professor no calendario este metodo compara o perfil conectado com professor
    private boolean usuarioEhProfessor(HttpSession session) {

        return "professor".equalsIgnoreCase(obterTipoUsuario(session));
    }
}
