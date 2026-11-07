// ambiente de turmas recebe as acoes sobre participantes e materiais
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.dto.TurmaDTO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.service.ConteudoService;
import org.springframework.web.multipart.MultipartFile;
import com.example.CampusLink.service.ArquivoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;
import java.util.List;
import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.repository.UsuarioRepository;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.helpers.MessageFormatter;
import com.example.CampusLink.service.DominioService;

import java.util.HashMap;
import java.util.Map;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

// controle das turmas esta classe prepara o ambiente e encaminha cadastro edicao progresso e exclusao
@Controller
public class turmasController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(turmasController.class);

    @Autowired
    private ProfessorRepository professorRepository;

    @Autowired
    private AlunoRepository alunoRepository;

    @Autowired
    private TurmaRepository turmaRepository;

    @Autowired
    private ConteudoService conteudoService;

    @Autowired
    private ArquivoService arquivoService;

    @Autowired
    private DominioService dominioService;

    // listar minhas turmas este metodo carrega as turmas do usuario e destaca as usadas recentemente
    @GetMapping("/turmas")
    public String Turmas(Model model, HttpSession session) throws SQLException {
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        model.addAttribute("turma", new TurmaDTO());
        List<TurmaDTO> turmas = List.of();


        String tipoUsuario = session.getAttribute("tipoUsuario").toString();
        // listar turmas do aluno consulta apenas os vinculos do aluno conectado
        if ("aluno".equalsIgnoreCase(tipoUsuario)) {
            turmas = turmaRepository.buscarTurmasDoUsuario("aluno", alunoRepository.buscarPorIDAluno(session.getAttribute("email2FA").toString()));
        }
        // listar turmas do professor consulta apenas os vinculos do professor conectado
        else if ("professor".equalsIgnoreCase(tipoUsuario)) {
            turmas = turmaRepository.buscarTurmasDoUsuario("professor", professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString()));
        }

        model.addAttribute("turmas", TurmaRepository.ordenarPorNome(turmas));
        model.addAttribute("turmasRecentes",
                turmaRepository.recentesDentre(session.getAttribute("email2FA").toString(), turmas, 5));

        return "Geral/turmas";
    }

    // abrir ambiente da turma este metodo reune participantes conteudos arquivos e permissoes para a tela
    @GetMapping("/turmas/{id}")
    public String ambienteTurma(@PathVariable String id,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String tipo,
                                Model model, HttpSession session,
                                RedirectAttributes redirectAttributes) throws SQLException {
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        Long idTurma;
        try {
            idTurma = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return "redirect:/turmas";
        }

        if (!participaDaTurma(session, idTurma, "ambienteTurma")) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Você não participa desta turma.");
            return "redirect:/turmas";
        }

        model.addAttribute("idTurma", id);
        TurmaDTO turma = turmaRepository.buscarTurmaPorId(id);
        // tratar turma inexistente retorna a lista quando a turma solicitada nao foi encontrada
        if (turma == null) {
            return "redirect:/turmas";
        }

        boolean isTurmaAdmin = false;
        // preparar gerenciamento da turma identifica o professor e confere se ele e o proprietario
        if (session.getAttribute("tipoUsuario") != null && "professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            String idProfessor = professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString());
            isTurmaAdmin = turma.getIdProprietario() != null && turma.getIdProprietario().toString().equals(idProfessor);
            model.addAttribute("idProfessorLogado", idProfessor == null || idProfessor.isBlank() ? null : Long.parseLong(idProfessor));

            // listar solicitacoes de dominio tenta carregar os pedidos do professor e registra falhas
            try {
                model.addAttribute("solicitacoesDominio",
                        dominioService.listarPorProfessor(Long.parseLong(idProfessor)));
            } catch (RuntimeException e) {
                logger.error("Erro ao carregar solicitações de domínio do professor {}", idProfessor, e);
            }
        }

        List<TurmaDTO> participantesTurma = turmaRepository.buscarParticipantesDaTurma(id);
        boolean isProfessorTurma = session.getAttribute("tipoUsuario") != null
                && "professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())
                && session.getAttribute("email2FA") != null
                && professorPodeGerenciarParticipantes(turma, participantesTurma,
                session.getAttribute("email2FA").toString());

        model.addAttribute("turma", turma);
        // preparar edicao da turma copia os dados atuais quando nao existe formulario anterior
        if (!model.containsAttribute("turmaEdicao")) {
            TurmaDTO turmaEdicao = new TurmaDTO();
            turmaEdicao.setId(turma.getId());
            turmaEdicao.setNomeTurma(turma.getNomeTurma());
            turmaEdicao.setDescricao(turma.getDescricao());
            model.addAttribute("turmaEdicao", turmaEdicao);
        }
        model.addAttribute("participantesTurma", participantesTurma);
        model.addAttribute("isTurmaAdmin", isTurmaAdmin);
        model.addAttribute("isProfessorTurma", isProfessorTurma);
        // preparar novo conteudo cria os dados vazios quando nao existe formulario anterior
        if (!model.containsAttribute("conteudo")) {
            model.addAttribute("conteudo", new ConteudoDTO());
        }
        List<ConteudoDTO> conteudos = conteudoService.buscarPorTurma(Long.parseLong(id), q, tipo);
        model.addAttribute("conteudos", conteudos);
        adicionarInformacoesMidia(model, conteudos);

        // preparar progresso do aluno consulta a identificacao apenas para aluno com email na sessao
        if ("aluno".equals(session.getAttribute("tipoUsuario")) && session.getAttribute("email2FA") != null) {
            String idAluno = alunoRepository.buscarPorIDAluno(session.getAttribute("email2FA").toString());
            // listar conclusoes do aluno carrega o progresso quando o codigo do aluno foi encontrado
            if (idAluno != null && !idAluno.isBlank()) {
                model.addAttribute("conteudosConcluidos",
                        conteudoService.listarConcluidosDoAluno(Long.parseLong(idAluno), Long.parseLong(id)));
            }
        }
        model.addAttribute("prazosEncerrados",
                conteudoService.listarPrazosEncerrados(conteudos.stream().map(ConteudoDTO::getId).toList()));
        model.addAttribute("termoBusca", q == null ? "" : q.trim());
        model.addAttribute("tipoFiltro", tipo == null ? "" : tipo);

        Map<Long, ArquivoDTO> arquivosPorConteudo = new HashMap<>();
        for (ConteudoDTO conteudo : conteudos) {
            ArquivoDTO arquivo = arquivoService.buscarMaisRecentePorConteudo(conteudo.getId());
            // associar material da turma inclui o arquivo encontrado na lista por conteudo
            if (arquivo != null) {
                arquivosPorConteudo.put(conteudo.getId(), arquivo);
            }
        }
        model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);
        return "Geral/ambienteTurma";
    }

    // buscar pessoas para turma este metodo confere o professor e procura participantes pelo texto informado
    @GetMapping("/turmas/{id}/sugestoes-pessoas")
    @ResponseBody
    public List<TurmaDTO> sugestoesPessoas(@PathVariable String id,
                                           @RequestParam(name = "q", defaultValue = "") String termo,
                                           HttpSession session) throws SQLException {
        // proteger sugestoes de pessoas exige professor conectado com email identificado
        if (session.getAttribute("usuarioLogado") == null
                || session.getAttribute("email2FA") == null
                || session.getAttribute("tipoUsuario") == null
                || !"professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        TurmaDTO turma = turmaRepository.buscarTurmaPorId(id);
        List<TurmaDTO> participantes = turmaRepository.buscarParticipantesDaTurma(id);
        // autorizar busca de participantes recusa professores sem permissao para gerenciar a turma
        if (!professorPodeGerenciarParticipantes(turma, participantes,
                session.getAttribute("email2FA").toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        String consulta = termo.trim();
        // limitar sugestoes vazias exige pelo menos duas letras para pesquisar pessoas
        if (consulta.length() < 2) {
            return List.of();
        }

        return turmaRepository.buscarSugestoesPessoasDaTurma(consulta, id);
    }

    // registrar uso da turma este metodo guarda a alteracao para o email conectado
    private void registrarAlteracao(HttpSession session, Long idTurma) {
        Object email = session.getAttribute("email2FA");
        // registrar mudanca da turma grava a alteracao apenas quando a sessao informa o email
        if (email != null) {
            turmaRepository.registrarAlteracao(email.toString(), idTurma);
        }
    }

    // verificar participacao na turma este metodo aceita alunos e professores vinculados e registra tentativas negadas
    private boolean participaDaTurma(HttpSession session, Long idTurma, String operacao) throws SQLException {
        Object tipo = session.getAttribute("tipoUsuario");
        Object email = session.getAttribute("email2FA");
        String idUsuario = null;

        if (idTurma != null && email != null) {
            if ("aluno".equals(tipo)) {
                idUsuario = alunoRepository.buscarPorIDAluno(email.toString());
            } else if ("professor".equals(tipo)) {
                idUsuario = professorRepository.buscarPorIDProfessor(email.toString());
            }
        }

        boolean participa = idUsuario != null && !idUsuario.isBlank()
                && turmaRepository.buscarTurmasDoUsuario(tipo.toString(), idUsuario).stream()
                .anyMatch(turma -> idTurma.equals(turma.getId()));

        if (!participa) {
            logger.warn("[{}] acesso negado: usuário não participa da turma {}.", operacao, idTurma);
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), operacao, null,
                            MessageFormatter.arrayFormat("[{}] acesso negado: usuário não participa da turma {}.", new Object[]{operacao, idTurma}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
        }

        return participa;
    }

    // verificar professor da turma este metodo aceita o responsavel ou um professor participante
    private boolean professorPodeGerenciarParticipantes(TurmaDTO turma,
                                                        List<TurmaDTO> participantes,
                                                        String emailProfessor) throws SQLException {
        // conferir dados do gerente recusa a permissao quando falta turma ou email do professor
        if (turma == null || emailProfessor == null) return false;

        String idProfessor = professorRepository.buscarPorIDProfessor(emailProfessor);
        // reconhecer dono da turma libera o gerenciamento quando o professor e o proprietario
        if (turma.getIdProprietario() != null
                && turma.getIdProprietario().toString().equals(idProfessor)) {
            return true;
        }

        return participantes.stream()
                .anyMatch(participante -> participante.getEmailPessoa() != null
                        && participante.getEmailPessoa().equalsIgnoreCase(emailProfessor)
                        && ("professor".equalsIgnoreCase(participante.getTipoUsuario())
                        || "professor admin".equalsIgnoreCase(participante.getTipoUsuario())));
    }

    // editar turma pelo responsavel este metodo valida a permissao e atualiza nome e descricao
    @PostMapping("/turmas/{id}/editar")
    public String editarTurma(@PathVariable String id,
                              @Valid @ModelAttribute("turmaEdicao") TurmaDTO turmaEdicao,
                              BindingResult result,
                              HttpSession session,
                              Model model,
                              TurmaDTO turmaDTO,
                              RedirectAttributes redirectAttributes) throws SQLException {
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        // restringir edicao de turma recusa perfis diferentes de professor
        if (session.getAttribute("tipoUsuario") == null
                || !"professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas o professor responsável pode editar esta turma.");
            return "redirect:/turmas/" + id;
        }

        Object emailProfessor = session.getAttribute("email2FA");
        // identificar editor da turma retorna ao login quando falta o email do professor
        if (emailProfessor == null) {
            return "redirect:/login";
        }

        String idProfessor = professorRepository.buscarPorIDProfessor(emailProfessor.toString());
        TurmaDTO turmaAtual = turmaRepository.buscarTurmaPorId(id);
        // localizar turma para editar retorna a listagem quando a turma nao existe
        if (turmaAtual == null) {
            return "redirect:/turmas";
        }

        boolean ehResponsavel = turmaAtual.getIdProprietario() != null
                && turmaAtual.getIdProprietario().toString().equals(idProfessor);
        // autorizar edicao da turma exige que o professor seja o responsavel
        if (!ehResponsavel) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas o professor responsável pode editar esta turma.");
            return "redirect:/turmas/" + id;
        }

        turmaEdicao.setId(turmaAtual.getId());
        // validar edicao de turma reabre o formulario com os dados e os erros encontrados
        if (result.hasErrors()) {
            model.addAttribute("turmaEdicao", turmaEdicao);
            model.addAttribute("abrirModalEditarTurma", true);
            return ambienteTurma(String.valueOf(turmaDTO.getId()), null, null, model, session, redirectAttributes);
        }

        boolean atualizada = turmaRepository.atualizarTurmaDoProprietario(
                id,
                idProfessor,
                turmaEdicao.getNomeTurma().trim(),
                turmaEdicao.getDescricao()
        );
        // registrar turma atualizada guarda a alteracao somente quando a atualizacao foi realizada
        if (atualizada) {
            registrarAlteracao(session, Long.valueOf(id));
        }

        // informar falha na edicao da turma retorna ao ambiente quando os dados nao foram atualizados
        if (!atualizada) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível atualizar os dados da turma.");
            return "redirect:/turmas/" + id;
        }

        redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma atualizada com sucesso.");
        return "redirect:/turmas/" + id;
    }

    // cadastrar conteudo na turma este metodo valida o formulario do professor e envia o material
    @PostMapping("/turmas/{id}/conteudos")
    public String criarConteudo(@PathVariable Long id,
                                @Valid @ModelAttribute("conteudo") ConteudoDTO conteudoDTO,
                                BindingResult result,
                                @RequestParam(value = "arquivo", required = false) MultipartFile arquivo,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) throws SQLException {

        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        // restringir cadastro de conteudo recusa usuarios que nao possuem perfil de professor
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            logger.warn("[criarConteudo] aluno tentou cadastrar conteúdo na turma {}.", id);
            // registro de avisos confere se esta habilitado ao cadastrar conteudo na turma
            if (logger.isWarnEnabled()) {
                // gravar historico ao cadastrar conteudo na turma tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "criarConteudo", null,
                            MessageFormatter.arrayFormat("[criarConteudo] aluno tentou cadastrar conteúdo na turma {}.", new Object[]{id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas/" + id;
        }
        // exigir participacao recusa o cadastro de conteudo em turmas das quais o professor nao participa
        if (!participaDaTurma(session, id, "criarConteudo")) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Você não participa desta turma.");
            return "redirect:/turmas";
        }
        // corrigir cadastro de conteudo reabre a janela e preserva o ambiente quando existem campos invalidos
        if (result.hasErrors()) {
            model.addAttribute("abrirModalAdicionarConteudo", true);
            model.addAttribute("idTurma", String.valueOf(id));
            model.addAttribute("turma", turmaRepository.buscarTurmaPorId(String.valueOf(id)));
            carregarSolicitacoesDominio(model, session);

            List<ConteudoDTO> conteudos = conteudoService.listarPorTurma(id);
            model.addAttribute("conteudos", conteudos);
            adicionarInformacoesMidia(model, conteudos);

            Map<Long, ArquivoDTO> arquivosPorConteudo = new HashMap<>();
            for (ConteudoDTO conteudo : conteudos) {
                ArquivoDTO arquivoExistente = arquivoService.buscarMaisRecentePorConteudo(conteudo.getId());
                // associar arquivo encontrado guarda o material do conteudo quando existe arquivo vinculado
                if (arquivoExistente != null) {
                    arquivosPorConteudo.put(conteudo.getId(), arquivoExistente);
                }
            }
            model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);

            return "Geral/ambienteTurma";
        }
        String idProfessor = professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString());

        conteudoDTO.setIdTurma(id);
        conteudoDTO.setIdProfessor(Long.parseLong(idProfessor));

        // criar conteudo da turma tenta salvar o material e reabre o formulario em caso de dados recusados
        try {
            conteudoService.criar(conteudoDTO, arquivo);
            registrarAlteracao(session, id);
        } catch (IllegalArgumentException e) {
            model.addAttribute("mensagemErro", e.getMessage());
            model.addAttribute("abrirModalAdicionarConteudo", true);
            model.addAttribute("idTurma", String.valueOf(id));
            model.addAttribute("turma", turmaRepository.buscarTurmaPorId(String.valueOf(id)));
            carregarSolicitacoesDominio(model, session);

            List<ConteudoDTO> conteudos = conteudoService.listarPorTurma(id);
            model.addAttribute("conteudos", conteudos);
            adicionarInformacoesMidia(model, conteudos);

            Map<Long, ArquivoDTO> arquivosPorConteudo = new HashMap<>();
            for (ConteudoDTO conteudo : conteudos) {
                ArquivoDTO arquivoExistente = arquivoService.buscarMaisRecentePorConteudo(conteudo.getId());
                // associar arquivo encontrado guarda o material do conteudo quando existe arquivo vinculado
                if (arquivoExistente != null) {
                    arquivosPorConteudo.put(conteudo.getId(), arquivoExistente);
                }
            }
            model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);

            return "Geral/ambienteTurma";
        }

        return "redirect:/turmas/" + id;
    }

    // cadastrar turma pelo professor este metodo grava a turma e vincula seu responsavel
    @PostMapping("/criarTurma")
    public String criarTurma(@Valid @ModelAttribute("turma") TurmaDTO turmaDTO, BindingResult result, HttpSession session, Model model, RedirectAttributes redirectAttributes) throws SQLException {

        // exigir professor conectado recusa a criacao de turma sem login completo ou por outro perfil
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/turmas";
        }

        // validar nova turma reabre a janela de criacao quando o formulario possui erros
        if (result.hasErrors()) {
            model.addAttribute("abrirModalCriarTurma", true);
            return "Geral/turmas";
        }

        // impedir criacao por aluno retorna ao inicio quando um aluno tenta criar turma
        if (session.getAttribute("tipoUsuario").equals("aluno")) {
            logger.warn("Criação de turma recusada: usuário é aluno.");
            // registro de avisos confere se esta habilitado ao cadastrar turma pelo professor
            if (logger.isWarnEnabled()) {
                // gravar historico ao cadastrar turma pelo professor tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "criarTurma", null,
                            "Criação de turma recusada: usuário é aluno.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Geral/home";
        }

        String idProfessor = professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString());
        if (idProfessor == null || idProfessor.isBlank()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível identificar o professor.");
            return "redirect:/turmas";
        }


        professorRepository.InsertTurmasIntoBD(turmaDTO.getNomeTurma(), turmaDTO.getDescricao(), idProfessor);


        String idTurma = turmaRepository.buscarUltimaTurmaPorProfessor(idProfessor);
        professorRepository.InsertProfessor_TurmaIntoBD(idProfessor, idTurma);
        registrarAlteracao(session, Long.valueOf(idTurma));
        logger.info("Turma cadastrada. turmaId={} professorId={}", idTurma, idProfessor);
        // registro de informacoes confere se esta habilitado ao cadastrar turma pelo professor
        if (logger.isInfoEnabled()) {
            // gravar historico ao cadastrar turma pelo professor tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", turmasController.class.getName(), "criarTurma", null,
                        MessageFormatter.arrayFormat("Turma cadastrada. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma cadastrada com sucesso.");
        return "redirect:/turmas";
    }

    // delete conteudo da turma este metodo solicita a exclusao apos conferir o perfil de professor
    @PostMapping("/turmas/{id}/conteudos/{idConteudo}/excluir")
    public String excluirConteudo(@PathVariable Long id,
                                  @PathVariable Long idConteudo,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) throws SQLException {

        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        // proteger delete conteudo recusa a exclusao quando o usuario nao e professor
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            logger.warn("[excluirConteudo] aluno tentou excluir conteúdo {} da turma {}.", idConteudo, id);
            // registro de avisos confere se esta habilitado ao delete conteudo da turma
            if (logger.isWarnEnabled()) {
                // gravar historico ao delete conteudo da turma tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "excluirConteudo", null,
                            MessageFormatter.arrayFormat("[excluirConteudo] aluno tentou excluir conteúdo {} da turma {}.", new Object[]{idConteudo, id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas/" + id;
        }

        if (!participaDaTurma(session, id, "excluirConteudo")) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Você não participa desta turma.");
            return "redirect:/turmas";
        }
        if (!conteudoService.pertenceATurma(idConteudo, id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Este conteúdo não pertence a esta turma.");
            return "redirect:/turmas/" + id;
        }

        conteudoService.excluir(idConteudo);
        registrarAlteracao(session, id);

        return "redirect:/turmas/" + id;
    }

    // reabrir conteudo pela turma este metodo permite ao professor disponibilizar o material novamente
    @PostMapping("/turmas/{id}/conteudos/{idConteudo}/re-disponibilizar")
    public String reDisponibilizarConteudo(@PathVariable Long id,
                                           @PathVariable Long idConteudo,
                                           HttpSession session,
                                           RedirectAttributes redirectAttributes) throws SQLException {

        // exigir sessao verificada retorna ao login quando falta usuario ou email da verificacao
        if (session.getAttribute("usuarioLogado") == null || session.getAttribute("email2FA") == null) {
            return "redirect:/login";
        }

        // restringir reabertura do conteudo permite a acao apenas pelo professor
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/turmas/" + id;
        }

        String idProfessor = professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString());
        // reabrir conteudo identificado confirma a mudanca quando encontra o professor e o material pode ser reaberto
        if (idProfessor != null && !idProfessor.isBlank() && conteudoService.reabrir(idConteudo, Long.parseLong(idProfessor), id)) {
            registrarAlteracao(session, id);
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Conteúdo disponível novamente para os alunos concluírem.");
        } else {
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível re-disponibilizar este conteúdo.");
        }

        return "redirect:/turmas/" + id;
    }

    // marcar conclusao do conteudo este metodo confere a participacao do aluno e atualiza seu progresso
    @PostMapping("/turmas/{id}/conteudos/{idConteudo}/concluir")
    public String concluirConteudo(@PathVariable Long id,
                                   @PathVariable Long idConteudo,
                                   @RequestParam(defaultValue = "true") boolean concluido,
                                   @RequestParam(required = false) String origem,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) throws SQLException {

        boolean voltarParaEventos = "eventos".equals(origem);

        // exigir sessao verificada retorna ao login quando falta usuario ou email da verificacao
        if (session.getAttribute("usuarioLogado") == null || session.getAttribute("email2FA") == null) {
            return "redirect:/login";
        }

        // restringir conclusao de conteudo permite marcar o progresso apenas pelo aluno
        if (!"aluno".equals(session.getAttribute("tipoUsuario"))) {
            logger.warn("[concluirConteudo] usuário que não é aluno tentou marcar conteúdo {} da turma {}.", idConteudo, id);
            // registro de avisos confere se esta habilitado ao marcar conclusao do conteudo
            if (logger.isWarnEnabled()) {
                // gravar historico ao marcar conclusao do conteudo tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "concluirConteudo", null,
                            MessageFormatter.arrayFormat("[concluirConteudo] usuário que não é aluno tentou marcar conteúdo {} da turma {}.", new Object[]{idConteudo, id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas/" + id;
        }

        String idAluno = alunoRepository.buscarPorIDAluno(session.getAttribute("email2FA").toString());
        boolean alunoDaTurma = idAluno != null && !idAluno.isBlank()
                && turmaRepository.buscarTurmasDoUsuario("aluno", idAluno).stream()
                .anyMatch(turma -> id.equals(turma.getId()));

        // conferir vinculo do aluno impede alterar progresso em turma da qual ele nao participa
        if (!alunoDaTurma) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Você não participa desta turma.");
            return "redirect:/turmas";
        }

        // alterar conclusao da turma atualiza o progresso e apresenta o motivo de uma alteracao recusada
        try {
            conteudoService.alterarConclusao(Long.parseLong(idAluno), id, idConteudo, concluido);
            registrarAlteracao(session, id);
            // avisar conteudo pendente informa a reabertura quando o retorno sera para os eventos
            if (voltarParaEventos && !concluido) {
                redirectAttributes.addFlashAttribute("mensagem", "O conteúdo voltou a ficar pendente.");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(voltarParaEventos ? "erro" : "mensagemErro", e.getMessage());
        }

        return voltarParaEventos ? "redirect:/eventos" : "redirect:/turmas/" + id;
    }
    // editar conteudo pela turma este metodo valida o formulario e permite substituir o arquivo
    @PostMapping("/turmas/{id}/conteudos/{idConteudo}/editar")
    public String editarConteudo(@PathVariable Long id,
                                 @PathVariable Long idConteudo,
                                 @Valid @ModelAttribute("conteudoEditado") ConteudoDTO conteudoDTO,
                                 BindingResult result,
                                 @RequestParam(value = "arquivo", required = false) MultipartFile arquivo,
                                 HttpSession session,
                                 Model model,
                                 RedirectAttributes redirectAttributes) throws SQLException {

        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        // restringir edicao de conteudo recusa usuarios que nao possuem perfil de professor
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            logger.warn("[editarConteudo] aluno tentou editar conteúdo {} da turma {}.", idConteudo, id);
            // registro de avisos confere se esta habilitado ao editar conteudo pela turma
            if (logger.isWarnEnabled()) {
                // gravar historico ao editar conteudo pela turma tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "editarConteudo", null,
                            MessageFormatter.arrayFormat("[editarConteudo] aluno tentou editar conteúdo {} da turma {}.", new Object[]{idConteudo, id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas/" + id;
        }

        // exigir participacao recusa a edicao em turmas alheias ou de conteudos de outra turma
        if (!participaDaTurma(session, id, "editarConteudo")) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Você não participa desta turma.");
            return "redirect:/turmas";
        }
        if (!conteudoService.pertenceATurma(idConteudo, id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Este conteúdo não pertence a esta turma.");
            return "redirect:/turmas/" + id;
        }

        // corrigir edicao de conteudo reabre a janela e carrega os materiais quando o formulario tem erros
        if (result.hasErrors()) {
            model.addAttribute("abrirModalEditarId", idConteudo);
            model.addAttribute("erroEditar", result.getAllErrors().get(0).getDefaultMessage());
            model.addAttribute("conteudo", new ConteudoDTO());
            model.addAttribute("idTurma", String.valueOf(id));
            model.addAttribute("turma", turmaRepository.buscarTurmaPorId(String.valueOf(id)));
            carregarSolicitacoesDominio(model, session);

            List<ConteudoDTO> conteudos = conteudoService.listarPorTurma(id);
            model.addAttribute("conteudos", conteudos);
            adicionarInformacoesMidia(model, conteudos);

            Map<Long, ArquivoDTO> arquivosPorConteudo = new HashMap<>();
            for (ConteudoDTO conteudo : conteudos) {
                ArquivoDTO arquivoExistente = arquivoService.buscarMaisRecentePorConteudo(conteudo.getId());
                // associar arquivo encontrado guarda o material do conteudo quando existe arquivo vinculado
                if (arquivoExistente != null) {
                    arquivosPorConteudo.put(conteudo.getId(), arquivoExistente);
                }
            }
            model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);

            return "Geral/ambienteTurma";
        }

        conteudoDTO.setId(idConteudo);

        // atualizar conteudo e arquivo salva as mudancas e reabre a edicao quando os dados sao recusados
        try {
            conteudoService.atualizar(conteudoDTO);
            registrarAlteracao(session, id);

            // salvar arquivo substituto envia o novo material apenas quando foi escolhido um arquivo nao vazio
            if (arquivo != null && !arquivo.isEmpty()) {
                arquivoService.salvar(arquivo, idConteudo);
            }
        } catch (IllegalArgumentException e) {
            model.addAttribute("abrirModalEditarId", idConteudo);
            model.addAttribute("erroEditar", e.getMessage());
            model.addAttribute("conteudo", new ConteudoDTO());
            model.addAttribute("idTurma", String.valueOf(id));
            model.addAttribute("turma", turmaRepository.buscarTurmaPorId(String.valueOf(id)));
            carregarSolicitacoesDominio(model, session);

            List<ConteudoDTO> conteudos = conteudoService.listarPorTurma(id);
            model.addAttribute("conteudos", conteudos);
            adicionarInformacoesMidia(model, conteudos);

            Map<Long, ArquivoDTO> arquivosPorConteudo = new HashMap<>();
            for (ConteudoDTO conteudo : conteudos) {
                ArquivoDTO arquivoExistente = arquivoService.buscarMaisRecentePorConteudo(conteudo.getId());
                // associar arquivo encontrado guarda o material do conteudo quando existe arquivo vinculado
                if (arquivoExistente != null) {
                    arquivosPorConteudo.put(conteudo.getId(), arquivoExistente);
                }
            }

            model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);

            return "Geral/ambienteTurma";
        }

        return "redirect:/turmas/" + id;
    }

    // montar miniaturas dos materiais este metodo identifica livros e videos pelos enderecos dos conteudos
    private void adicionarInformacoesMidia(Model model, List<ConteudoDTO> conteudos) {
        Map<Long, String> miniaturasPorConteudo = new HashMap<>();
        Map<Long, String> tiposMidiaPorConteudo = new HashMap<>();
        Map<Long, String> videoIdsPorConteudo = new HashMap<>();
        Map<Long, String> livroIdsPorConteudo = new HashMap<>();

        for (ConteudoDTO conteudo : conteudos) {
            String url = conteudo.getUrl();
            // ignorar conteudo sem endereco passa ao proximo item quando nao existe link de midia
            if (url == null || url.isBlank()) {
                continue;
            }

            // identificar midia pelo endereco interpreta o link e registra enderecos invalidos sem interromper a lista
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                // ignorar endereco sem dominio passa ao proximo conteudo quando nao encontra o servidor do link
                if (host == null) {
                    continue;
                }

                host = host.toLowerCase(Locale.ROOT);
                String thumbnail = null;
                String tipoMidia = null;

                // identificar video externo procura o codigo quando o dominio pertence ao youtube
                if (host.equals("youtu.be") || host.endsWith(".youtube.com") || host.equals("youtube.com")) {
                    String videoId = extrairIdVideo(uri);
                    // guardar video identificado monta a miniatura e associa o codigo ao conteudo
                    if (videoId != null) {
                        thumbnail = "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
                        tipoMidia = "Vídeo do YouTube";
                        videoIdsPorConteudo.put(conteudo.getId(), videoId);
                    }
                }
                // identificar livro externo procura o codigo quando o endereco pertence ao catalogo de livros
                else if (host.equals("books.google.com") || host.equals("play.google.com")) {
                    String idLivro = extrairParametro(uri.getRawQuery(), "id");
                    // procurar livro no caminho usa o final do endereco quando o parametro de identificacao esta ausente
                    if (idLivro == null && host.equals("books.google.com")) {
                        String[] partes = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
                        // conferir caminho do livro le o ultimo trecho somente quando o endereco possui partes
                        if (partes.length > 0) {
                            String ultimoSegmento = partes[partes.length - 1];
                            // validar trecho do livro aceita o final do caminho quando ele respeita o formato de codigo
                            if (ultimoSegmento.matches("[A-Za-z0-9_-]{1,128}")) {
                                idLivro = ultimoSegmento;
                            }
                        }
                    }
                    // guardar livro identificado monta a capa e associa o codigo preenchido ao conteudo
                    if (idLivro != null && !idLivro.isBlank()) {
                        thumbnail = "https://books.google.com/books/content?id="
                                + URLEncoder.encode(idLivro, StandardCharsets.UTF_8)
                                + "&printsec=frontcover&img=1&zoom=1";
                        tipoMidia = "Livro do Google Books";
                        livroIdsPorConteudo.put(conteudo.getId(), idLivro);
                    }
                }

                // incluir miniatura encontrada guarda imagem e tipo apenas quando a midia foi identificada
                if (thumbnail != null) {
                    miniaturasPorConteudo.put(conteudo.getId(), thumbnail);
                    tiposMidiaPorConteudo.put(conteudo.getId(), tipoMidia);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("URL inválida ao identificar a miniatura do conteúdo {}", conteudo.getId());
            }
        }

        model.addAttribute("miniaturasPorConteudo", miniaturasPorConteudo);
        model.addAttribute("tiposMidiaPorConteudo", tiposMidiaPorConteudo);
        model.addAttribute("videoIdsPorConteudo", videoIdsPorConteudo);
        model.addAttribute("livroIdsPorConteudo", livroIdsPorConteudo);
    }

    // identificar video pelo endereco este metodo extrai e confere o codigo do video
    private String extrairIdVideo(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String videoId = null;

        // interpretar link curto usa o caminho do endereco para os links curtos do youtube
        if (host.equals("youtu.be")) {
            String path = uri.getPath();
            // ler caminho do video extrai o primeiro trecho quando existe texto depois da barra
            if (path != null && path.length() > 1) {
                videoId = path.substring(1).split("/")[0];
            }
        } else {
            videoId = extrairParametro(uri.getRawQuery(), "v");
            // procurar video no caminho usa os trechos do endereco quando o parametro do video nao existe
            if (videoId == null) {
                String[] partes = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
                for (int i = 0; i < partes.length - 1; i++) {
                    // reconhecer formato de video extrai o codigo apos os caminhos de incorporacao video curto ou transmissao
                    if ("embed".equals(partes[i]) || "shorts".equals(partes[i]) || "live".equals(partes[i])) {
                        videoId = partes[i + 1];
                        break;
                    }
                }
            }
        }

        return videoId != null && videoId.matches("[A-Za-z0-9_-]{11}") ? videoId : null;
    }

    // ler parametro do endereco este metodo encontra e decodifica o valor solicitado
    private String extrairParametro(String query, String nome) {
        // ignorar parametros ausentes retorna vazio quando o endereco nao possui uma consulta preenchida
        if (query == null || query.isBlank()) {
            return null;
        }

        for (String parametro : query.split("&")) {
            String[] par = parametro.split("=", 2);
            // encontrar parametro do endereco devolve o valor decodificado quando o nome corresponde ao pedido
            if (par.length == 2 && nome.equals(URLDecoder.decode(par[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(par[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // adicionar participante na turma este metodo confere o professor e inclui a pessoa pelo email
    @PostMapping("/adicionarPessoas")
    public String adicionarPessoas(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session,
                                   Model model, RedirectAttributes redirectAttributes) throws SQLException {

        // autenticar adicao de pessoas exige login antes de alterar participantes
        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[adicionarPessoas] usuário não autenticado; redirecionando para login.");
            // registro de avisos confere se esta habilitado ao adicionar participante na turma
            if (logger.isWarnEnabled()) {
                // gravar historico ao adicionar participante na turma tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "adicionarPessoas", null,
                            "[adicionarPessoas] usuário não autenticado; redirecionando para login.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        // exigir turma para adicionar retorna a lista quando falta o codigo da turma
        if (turmaDTO.getId() == null) {
            return "redirect:/turmas";
        }

        // restringir adicao de pessoas recusa sessoes sem perfil de professor
        if (session.getAttribute("tipoUsuario") == null
                || !"professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            return "Geral/home";
        }

        String idTurma = String.valueOf(turmaDTO.getId());
        Object emailProfessor = session.getAttribute("email2FA");
        // exigir email para adicionar retorna ao login quando o professor nao esta identificado
        if (emailProfessor == null) return "redirect:/login";

        TurmaDTO turma = turmaRepository.buscarTurmaPorId(idTurma);
        List<TurmaDTO> participantes = turmaRepository.buscarParticipantesDaTurma(idTurma);
        // autorizar adicao de pessoas exige um professor com permissao para gerenciar a turma
        if (!professorPodeGerenciarParticipantes(turma, participantes, emailProfessor.toString())) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas professores vinculados à turma podem adicionar pessoas.");
            return "redirect:/turmas/" + idTurma;
        }

        // exigir email do participante retorna ao ambiente quando nenhuma pessoa foi informada
        if (turmaDTO.getEmailPessoa() == null || turmaDTO.getEmailPessoa().isBlank()) {
            logger.warn("[adicionarPessoas] e-mail vazio para turma {}.", turmaDTO.getId());
            // registro de avisos confere se esta habilitado ao adicionar participante na turma
            if (logger.isWarnEnabled()) {
                // gravar historico ao adicionar participante na turma tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "adicionarPessoas", null,
                            MessageFormatter.arrayFormat("[adicionarPessoas] e-mail vazio para turma {}.", new Object[]{turmaDTO.getId()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return ambienteTurma(String.valueOf(turmaDTO.getId()), null, null, model, session, redirectAttributes);
        }

        boolean pessoaAdicionada = turmaRepository.inserirPessoaTurma(turmaDTO.getEmailPessoa().trim(), idTurma);

        // confirmar pessoa adicionada registra a alteracao e distingue sucesso de falha no vinculo
        if (pessoaAdicionada) {
            registrarAlteracao(session, Long.valueOf(idTurma));
            model.addAttribute("mensagemSucesso", "Pessoa adicionada com sucesso.");
        } else {
            model.addAttribute("mensagemErro", "Não foi possível adicionar a pessoa informada.");
        }

        model.addAttribute("abrirModalParticipantes", true);
        return ambienteTurma(String.valueOf(turmaDTO.getId()), null, null, model, session, redirectAttributes);
    }

    // delete turma pelo responsavel este metodo solicita a exclusao usando a identidade do professor
    @PostMapping("/excluirTurma")
    public String excluirTurma(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session, Model model, RedirectAttributes redirectAttributes) throws SQLException {

        // autenticar delete turma exige login antes de excluir uma turma
        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[excluirTurma] usuário não autenticado; redirecionando para login.");
            // registro de avisos confere se esta habilitado ao delete turma pelo responsavel
            if (logger.isWarnEnabled()) {
                // gravar historico ao delete turma pelo responsavel tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "excluirTurma", null,
                            "[excluirTurma] usuário não autenticado; redirecionando para login.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        // restringir delete turma recusa usuarios sem perfil de professor
        if (session.getAttribute("tipoUsuario") == null || !"professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            logger.warn("[excluirTurma] usuário tentou excluir turma sem permissão.");
            // registro de avisos confere se esta habilitado ao delete turma pelo responsavel
            if (logger.isWarnEnabled()) {
                // gravar historico ao delete turma pelo responsavel tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "excluirTurma", null,
                            "[excluirTurma] usuário tentou excluir turma sem permissão.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Geral/home";
        }

        String idProfessor = professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString());
        turmaRepository.excluirTurma(String.valueOf(turmaDTO.getId()), idProfessor);
        logger.info("Turma excluída. turmaId={} professorId={}", turmaDTO.getId(), idProfessor);
        // registro de informacoes confere se esta habilitado ao delete turma pelo responsavel
        if (logger.isInfoEnabled()) {
            // gravar historico ao delete turma pelo responsavel tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", turmasController.class.getName(), "excluirTurma", null,
                        MessageFormatter.arrayFormat("Turma excluída. turmaId={} professorId={}", new Object[]{turmaDTO.getId(), idProfessor}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma excluída com sucesso.");
        return "redirect:/turmas";
    }

    // delete participante da turma este metodo confere a permissao e impede a remocao do responsavel
    @PostMapping("/removerPessoas")
    public String removerPessoas(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session,
                                 RedirectAttributes redirectAttributes) throws SQLException {

        // autenticar remocao de pessoas exige login antes de remover participantes
        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[removerPessoas] usuário não autenticado; redirecionando para login.");
            // registro de avisos confere se esta habilitado ao delete participante da turma
            if (logger.isWarnEnabled()) {
                // gravar historico ao delete participante da turma tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "removerPessoas", null,
                            "[removerPessoas] usuário não autenticado; redirecionando para login.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        // exigir turma para remover retorna a lista quando falta o codigo da turma
        if (turmaDTO.getId() == null) {
            return "redirect:/turmas";
        }

        // impedir remocao por aluno recusa o acesso de alunos a exclusao de participantes
        if (session.getAttribute("tipoUsuario") != null && session.getAttribute("tipoUsuario").equals("aluno")) {
            logger.warn("[removerPessoas] aluno tentou remover pessoas da turma.");
            // registro de avisos confere se esta habilitado ao delete participante da turma
            if (logger.isWarnEnabled()) {
                // gravar historico ao delete participante da turma tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "removerPessoas", null,
                            "[removerPessoas] aluno tentou remover pessoas da turma.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Geral/home";
        }

        // restringir remocao de participantes exige perfil de professor antes de conferir os vinculos
        if (session.getAttribute("tipoUsuario") == null
                || !"professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas professores vinculados à turma podem remover participantes.");
            return "redirect:/turmas/" + turmaDTO.getId();
        }

        Object emailProfessor = session.getAttribute("email2FA");
        // identificar professor da remocao retorna ao login quando falta o email da sessao
        if (emailProfessor == null) {
            return "redirect:/login";
        }

        String idTurma = String.valueOf(turmaDTO.getId());
        TurmaDTO turma = turmaRepository.buscarTurmaPorId(idTurma);
        List<TurmaDTO> participantes = turmaRepository.buscarParticipantesDaTurma(idTurma);
        // autorizar remocao de participantes exige permissao para gerenciar a turma selecionada
        if (!professorPodeGerenciarParticipantes(turma, participantes, emailProfessor.toString())) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas professores vinculados à turma podem remover participantes.");
            return "redirect:/turmas/" + idTurma;
        }

        // exigir participante valido reabre a lista quando o email da pessoa esta ausente
        if (turmaDTO.getEmailPessoa() == null || turmaDTO.getEmailPessoa().isBlank()) {
            logger.warn("[removerPessoas] e-mail vazio para turma {}.", turmaDTO.getId());
            // registro de avisos confere se esta habilitado ao delete participante da turma
            if (logger.isWarnEnabled()) {
                // gravar historico ao delete participante da turma tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "removerPessoas", null,
                            MessageFormatter.arrayFormat("[removerPessoas] e-mail vazio para turma {}.", new Object[]{turmaDTO.getId()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemErro", "Selecione um participante válido.");
            redirectAttributes.addFlashAttribute("abrirModalParticipantes", true);
            return "redirect:/turmas/" + idTurma;
        }

        String emailPessoa = turmaDTO.getEmailPessoa().trim();
        boolean professorAdmin = participantes.stream().anyMatch(participante ->
                participante.getEmailPessoa() != null
                        && participante.getEmailPessoa().equalsIgnoreCase(emailPessoa)
                        && "Professor Admin".equalsIgnoreCase(participante.getTipoUsuario()));

        // proteger responsavel da turma impede a remocao do professor administrador
        if (professorAdmin) {
            redirectAttributes.addFlashAttribute(
                    "mensagemErro",
                    "O professor administrador da turma não pode ser removido."
            );
            redirectAttributes.addFlashAttribute("abrirModalParticipantes", true);
            return "redirect:/turmas/" + idTurma;
        }

        boolean removido = turmaRepository.revomerPessoaTurma(emailPessoa, idTurma);

        // confirmar remocao de participante mostra o resultado e registra a alteracao quando o vinculo foi removido
        if (!removido) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover este participante.");
        } else {
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Participante removido com sucesso.");
            registrarAlteracao(session, Long.valueOf(idTurma));
        }

        redirectAttributes.addFlashAttribute("abrirModalParticipantes", true);
        return "redirect:/turmas/" + idTurma;
    }

    // listar pedidos de dominio na turma este metodo carrega as solicitacoes do professor conectado
    private void carregarSolicitacoesDominio(Model model, HttpSession session) {
        // limitar consulta de dominios encerra a busca para perfis diferentes de professor
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return;
        }

        // carregar pedidos de dominio consulta as solicitacoes do professor e registra falhas
        try {
            String idProfessor = professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString());
            model.addAttribute("solicitacoesDominio",
                    dominioService.listarPorProfessor(Long.parseLong(idProfessor)));
        } catch (Exception e) {
            logger.error("Erro ao carregar solicitações de domínio do professor.", e);
        }
    }

    // tratar arquivo grande na turma este metodo informa o limite de envio e retorna ao ambiente
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String tratarArquivoMuitoGrande(
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        String uri = request.getRequestURI();
        String id = uri.replaceAll(".*?/turmas/(\\d+).*", "$1");

        redirectAttributes.addFlashAttribute(
                "mensagemErro",
                "O arquivo enviado é muito grande. O limite é de 50 MB."
        );

        return "redirect:/turmas/" + id;
    }


}
