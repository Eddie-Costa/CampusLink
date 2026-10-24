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

    @GetMapping("/turmas")
    public String Turmas(Model model, HttpSession session) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        model.addAttribute("turma", new TurmaDTO());
        List<TurmaDTO> turmas = List.of();

        //recuperar turmas que o usuario esta
        String tipoUsuario = session.getAttribute("tipoUsuario").toString();
        if ("aluno".equalsIgnoreCase(tipoUsuario)) {
            turmas = turmaRepository.buscarTurmasDoUsuario("aluno", alunoRepository.buscarPorIDAluno(session.getAttribute("email2FA").toString()));
        } else if ("professor".equalsIgnoreCase(tipoUsuario)) {
            turmas = turmaRepository.buscarTurmasDoUsuario("professor", professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString()));
        }

        model.addAttribute("turmas", turmas);

        return "Geral/turmas";
    }

    @GetMapping("/turmas/{id}")
    public String ambienteTurma(@PathVariable String id,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String tipo,
                                Model model, HttpSession session) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        model.addAttribute("idTurma", id);
        TurmaDTO turma = turmaRepository.buscarTurmaPorId(id);
        if (turma == null) {
            return "redirect:/turmas";
        }

        boolean isTurmaAdmin = false;
        if (session.getAttribute("tipoUsuario") != null && "professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            String idProfessor = professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString());
            isTurmaAdmin = turma.getIdProprietario() != null && turma.getIdProprietario().toString().equals(idProfessor);

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
        if (!model.containsAttribute("conteudo")) {
            model.addAttribute("conteudo", new ConteudoDTO());
        }
        List<ConteudoDTO> conteudos = conteudoService.buscarPorTurma(Long.parseLong(id), q, tipo);
        model.addAttribute("conteudos", conteudos);
        adicionarInformacoesMidia(model, conteudos);

        if ("aluno".equals(session.getAttribute("tipoUsuario")) && session.getAttribute("email2FA") != null) {
            String idAluno = alunoRepository.buscarPorIDAluno(session.getAttribute("email2FA").toString());
            if (idAluno != null && !idAluno.isBlank()) {
                model.addAttribute("conteudosConcluidos",
                        conteudoService.listarConcluidosDoAluno(Long.parseLong(idAluno), Long.parseLong(id)));
            }
        }
        model.addAttribute("termoBusca", q == null ? "" : q.trim());
        model.addAttribute("tipoFiltro", tipo == null ? "" : tipo);

        Map<Long, ArquivoDTO> arquivosPorConteudo = new HashMap<>();
        for (ConteudoDTO conteudo : conteudos) {
            ArquivoDTO arquivo = arquivoService.buscarMaisRecentePorConteudo(conteudo.getId());
            if (arquivo != null) {
                arquivosPorConteudo.put(conteudo.getId(), arquivo);
            }
        }
        model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);
        return "Geral/ambienteTurma";
    }

    @GetMapping("/turmas/{id}/sugestoes-pessoas")
    @ResponseBody
    public List<TurmaDTO> sugestoesPessoas(@PathVariable String id,
                                           @RequestParam(name = "q", defaultValue = "") String termo,
                                           HttpSession session) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null
                || session.getAttribute("email2FA") == null
                || session.getAttribute("tipoUsuario") == null
                || !"professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        TurmaDTO turma = turmaRepository.buscarTurmaPorId(id);
        List<TurmaDTO> participantes = turmaRepository.buscarParticipantesDaTurma(id);
        if (!professorPodeGerenciarParticipantes(turma, participantes,
                session.getAttribute("email2FA").toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        String consulta = termo.trim();
        if (consulta.length() < 2) {
            return List.of();
        }

        return turmaRepository.buscarSugestoesPessoasDaTurma(consulta, id);
    }

    private boolean professorPodeGerenciarParticipantes(TurmaDTO turma,
                                                        List<TurmaDTO> participantes,
                                                        String emailProfessor) throws SQLException {
        if (turma == null || emailProfessor == null) return false;

        String idProfessor = professorRepository.buscarPorIDProfessor(emailProfessor);
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

    @PostMapping("/turmas/{id}/editar")
    public String editarTurma(@PathVariable String id,
                              @Valid @ModelAttribute("turmaEdicao") TurmaDTO turmaEdicao,
                              BindingResult result,
                              HttpSession session,
                              Model model,
                              TurmaDTO turmaDTO,
                              RedirectAttributes redirectAttributes) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        if (session.getAttribute("tipoUsuario") == null
                || !"professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas o professor responsável pode editar esta turma.");
            return "redirect:/turmas/" + id;
        }

        Object emailProfessor = session.getAttribute("email2FA");
        if (emailProfessor == null) {
            return "redirect:/login";
        }

        String idProfessor = professorRepository.buscarPorIDProfessor(emailProfessor.toString());
        TurmaDTO turmaAtual = turmaRepository.buscarTurmaPorId(id);
        if (turmaAtual == null) {
            return "redirect:/turmas";
        }

        boolean ehResponsavel = turmaAtual.getIdProprietario() != null
                && turmaAtual.getIdProprietario().toString().equals(idProfessor);
        if (!ehResponsavel) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas o professor responsável pode editar esta turma.");
            return "redirect:/turmas/" + id;
        }

        turmaEdicao.setId(turmaAtual.getId());
        if (result.hasErrors()) {
            model.addAttribute("turmaEdicao", turmaEdicao);
            model.addAttribute("abrirModalEditarTurma", true);
            return ambienteTurma(String.valueOf(turmaDTO.getId()), null, null, model, session);
        }

        boolean atualizada = turmaRepository.atualizarTurmaDoProprietario(
                id,
                idProfessor,
                turmaEdicao.getNomeTurma().trim(),
                turmaEdicao.getDescricao()
        );

        if (!atualizada) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível atualizar os dados da turma.");
            return "redirect:/turmas/" + id;
        }

        redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma atualizada com sucesso.");
        return "redirect:/turmas/" + id;
    }

    @PostMapping("/turmas/{id}/conteudos")
    public String criarConteudo(@PathVariable Long id,
                                @Valid @ModelAttribute("conteudo") ConteudoDTO conteudoDTO,
                                BindingResult result,
                                @RequestParam(value = "arquivo", required = false) MultipartFile arquivo,
                                HttpSession session,
                                Model model) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            logger.warn("[criarConteudo] aluno tentou cadastrar conteúdo na turma {}.", id);
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "criarConteudo", null,
                            MessageFormatter.arrayFormat("[criarConteudo] aluno tentou cadastrar conteúdo na turma {}.", new Object[]{id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas/" + id;
        }
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

        try {
            conteudoService.criar(conteudoDTO, arquivo);
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
                if (arquivoExistente != null) {
                    arquivosPorConteudo.put(conteudo.getId(), arquivoExistente);
                }
            }
            model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);

            return "Geral/ambienteTurma";
        }

        return "redirect:/turmas/" + id;
    }

    @PostMapping("/criarTurma")
    public String criarTurma(@Valid @ModelAttribute("turma") TurmaDTO turmaDTO, BindingResult result, HttpSession session, Model model, RedirectAttributes redirectAttributes) throws SQLException {

        if (result.hasErrors()) {
            model.addAttribute("abrirModalCriarTurma", true);
            return "Geral/turmas";
        }

        if (session.getAttribute("tipoUsuario").equals("aluno")) {
            logger.warn("Criação de turma recusada: usuário é aluno.");
            if (logger.isWarnEnabled()) {
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

        //Insere turma no banco de dados
        professorRepository.InsertTurmasIntoBD(turmaDTO.getNomeTurma(), turmaDTO.getDescricao(), idProfessor);

        //Insere x_turma no banco de dados
        String idTurma = turmaRepository.buscarUltimaTurmaPorProfessor(idProfessor);
        professorRepository.InsertProfessor_TurmaIntoBD(idProfessor, idTurma);
        logger.info("Turma cadastrada. turmaId={} professorId={}", idTurma, idProfessor);
        if (logger.isInfoEnabled()) {
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

    @PostMapping("/turmas/{id}/conteudos/{idConteudo}/excluir")
    public String excluirConteudo(@PathVariable Long id,
                                  @PathVariable Long idConteudo,
                                  HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            logger.warn("[excluirConteudo] aluno tentou excluir conteúdo {} da turma {}.", idConteudo, id);
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "excluirConteudo", null,
                            MessageFormatter.arrayFormat("[excluirConteudo] aluno tentou excluir conteúdo {} da turma {}.", new Object[]{idConteudo, id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas/" + id;
        }

        conteudoService.excluir(idConteudo);

        return "redirect:/turmas/" + id;
    }

    @PostMapping("/turmas/{id}/conteudos/{idConteudo}/concluir")
    public String concluirConteudo(@PathVariable Long id,
                                   @PathVariable Long idConteudo,
                                   @RequestParam(defaultValue = "true") boolean concluido,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null || session.getAttribute("email2FA") == null) {
            return "redirect:/login";
        }

        if (!"aluno".equals(session.getAttribute("tipoUsuario"))) {
            logger.warn("[concluirConteudo] usuário que não é aluno tentou marcar conteúdo {} da turma {}.", idConteudo, id);
            if (logger.isWarnEnabled()) {
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

        if (!alunoDaTurma) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Você não participa desta turma.");
            return "redirect:/turmas";
        }

        try {
            conteudoService.alterarConclusao(Long.parseLong(idAluno), id, idConteudo, concluido);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
        }

        return "redirect:/turmas/" + id;
    }
    @PostMapping("/turmas/{id}/conteudos/{idConteudo}/editar")
    public String editarConteudo(@PathVariable Long id,
                                 @PathVariable Long idConteudo,
                                 @Valid @ModelAttribute("conteudoEditado") ConteudoDTO conteudoDTO,
                                 BindingResult result,
                                 @RequestParam(value = "arquivo", required = false) MultipartFile arquivo,
                                 HttpSession session,
                                 Model model) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            logger.warn("[editarConteudo] aluno tentou editar conteúdo {} da turma {}.", idConteudo, id);
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "editarConteudo", null,
                            MessageFormatter.arrayFormat("[editarConteudo] aluno tentou editar conteúdo {} da turma {}.", new Object[]{idConteudo, id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas/" + id;
        }

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
                if (arquivoExistente != null) {
                    arquivosPorConteudo.put(conteudo.getId(), arquivoExistente);
                }
            }
            model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);

            return "Geral/ambienteTurma";
        }

        conteudoDTO.setId(idConteudo);

        try {
            conteudoService.atualizar(conteudoDTO);

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
                if (arquivoExistente != null) {
                    arquivosPorConteudo.put(conteudo.getId(), arquivoExistente);
                }
            }

            model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);

            return "Geral/ambienteTurma";
        }

        return "redirect:/turmas/" + id;
    }

    private void adicionarInformacoesMidia(Model model, List<ConteudoDTO> conteudos) {
        Map<Long, String> miniaturasPorConteudo = new HashMap<>();
        Map<Long, String> tiposMidiaPorConteudo = new HashMap<>();
        Map<Long, String> videoIdsPorConteudo = new HashMap<>();
        Map<Long, String> livroIdsPorConteudo = new HashMap<>();

        for (ConteudoDTO conteudo : conteudos) {
            String url = conteudo.getUrl();
            if (url == null || url.isBlank()) {
                continue;
            }

            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                if (host == null) {
                    continue;
                }

                host = host.toLowerCase(Locale.ROOT);
                String thumbnail = null;
                String tipoMidia = null;

                if (host.equals("youtu.be") || host.endsWith(".youtube.com") || host.equals("youtube.com")) {
                    String videoId = extrairIdVideo(uri);
                    if (videoId != null) {
                        thumbnail = "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
                        tipoMidia = "Vídeo do YouTube";
                        videoIdsPorConteudo.put(conteudo.getId(), videoId);
                    }
                } else if (host.equals("books.google.com") || host.equals("play.google.com")) {
                    String idLivro = extrairParametro(uri.getRawQuery(), "id");
                    if (idLivro == null && host.equals("books.google.com")) {
                        String[] partes = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
                        if (partes.length > 0) {
                            String ultimoSegmento = partes[partes.length - 1];
                            if (ultimoSegmento.matches("[A-Za-z0-9_-]{1,128}")) {
                                idLivro = ultimoSegmento;
                            }
                        }
                    }
                    if (idLivro != null && !idLivro.isBlank()) {
                        thumbnail = "https://books.google.com/books/content?id="
                                + URLEncoder.encode(idLivro, StandardCharsets.UTF_8)
                                + "&printsec=frontcover&img=1&zoom=1";
                        tipoMidia = "Livro do Google Books";
                        livroIdsPorConteudo.put(conteudo.getId(), idLivro);
                    }
                }

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

    private String extrairIdVideo(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String videoId = null;

        if (host.equals("youtu.be")) {
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                videoId = path.substring(1).split("/")[0];
            }
        } else {
            videoId = extrairParametro(uri.getRawQuery(), "v");
            if (videoId == null) {
                String[] partes = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
                for (int i = 0; i < partes.length - 1; i++) {
                    if ("embed".equals(partes[i]) || "shorts".equals(partes[i]) || "live".equals(partes[i])) {
                        videoId = partes[i + 1];
                        break;
                    }
                }
            }
        }

        return videoId != null && videoId.matches("[A-Za-z0-9_-]{11}") ? videoId : null;
    }

    private String extrairParametro(String query, String nome) {
        if (query == null || query.isBlank()) {
            return null;
        }

        for (String parametro : query.split("&")) {
            String[] par = parametro.split("=", 2);
            if (par.length == 2 && nome.equals(URLDecoder.decode(par[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(par[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @PostMapping("/adicionarPessoas")
    public String adicionarPessoas(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session,
                                   Model model, RedirectAttributes redirectAttributes) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[adicionarPessoas] usuário não autenticado; redirecionando para login.");
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "adicionarPessoas", null,
                            "[adicionarPessoas] usuário não autenticado; redirecionando para login.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        if (turmaDTO.getId() == null) {
            return "redirect:/turmas";
        }

        if (session.getAttribute("tipoUsuario") == null
                || !"professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            return "Geral/home";
        }

        String idTurma = String.valueOf(turmaDTO.getId());
        Object emailProfessor = session.getAttribute("email2FA");
        if (emailProfessor == null) return "redirect:/login";

        TurmaDTO turma = turmaRepository.buscarTurmaPorId(idTurma);
        List<TurmaDTO> participantes = turmaRepository.buscarParticipantesDaTurma(idTurma);
        if (!professorPodeGerenciarParticipantes(turma, participantes, emailProfessor.toString())) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas professores vinculados à turma podem adicionar pessoas.");
            return "redirect:/turmas/" + idTurma;
        }

        if (turmaDTO.getEmailPessoa() == null || turmaDTO.getEmailPessoa().isBlank()) {
            logger.warn("[adicionarPessoas] e-mail vazio para turma {}.", turmaDTO.getId());
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "adicionarPessoas", null,
                            MessageFormatter.arrayFormat("[adicionarPessoas] e-mail vazio para turma {}.", new Object[]{turmaDTO.getId()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return ambienteTurma(String.valueOf(turmaDTO.getId()), null, null, model, session);
        }

        boolean pessoaAdicionada = turmaRepository.inserirPessoaTurma(turmaDTO.getEmailPessoa().trim(), idTurma);

        if (pessoaAdicionada) {
            model.addAttribute("mensagemSucesso", "Pessoa adicionada com sucesso.");
        } else {
            model.addAttribute("mensagemErro", "Não foi possível adicionar a pessoa informada.");
        }

        model.addAttribute("abrirModalParticipantes", true);
        return ambienteTurma(String.valueOf(turmaDTO.getId()), null, null, model, session);
    }

    @PostMapping("/excluirTurma")
    public String excluirTurma(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session, Model model, RedirectAttributes redirectAttributes) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[excluirTurma] usuário não autenticado; redirecionando para login.");
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "excluirTurma", null,
                            "[excluirTurma] usuário não autenticado; redirecionando para login.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        if (session.getAttribute("tipoUsuario") == null || !"professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            logger.warn("[excluirTurma] usuário tentou excluir turma sem permissão.");
            if (logger.isWarnEnabled()) {
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
        if (logger.isInfoEnabled()) {
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

    @PostMapping("/removerPessoas")
    public String removerPessoas(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session,
                                 RedirectAttributes redirectAttributes) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[removerPessoas] usuário não autenticado; redirecionando para login.");
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "removerPessoas", null,
                            "[removerPessoas] usuário não autenticado; redirecionando para login.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        if (turmaDTO.getId() == null) {
            return "redirect:/turmas";
        }

        if (session.getAttribute("tipoUsuario") != null && session.getAttribute("tipoUsuario").equals("aluno")) {
            logger.warn("[removerPessoas] aluno tentou remover pessoas da turma.");
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "removerPessoas", null,
                            "[removerPessoas] aluno tentou remover pessoas da turma.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Geral/home";
        }

        if (session.getAttribute("tipoUsuario") == null
                || !"professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas professores vinculados à turma podem remover participantes.");
            return "redirect:/turmas/" + turmaDTO.getId();
        }

        Object emailProfessor = session.getAttribute("email2FA");
        if (emailProfessor == null) {
            return "redirect:/login";
        }

        String idTurma = String.valueOf(turmaDTO.getId());
        TurmaDTO turma = turmaRepository.buscarTurmaPorId(idTurma);
        List<TurmaDTO> participantes = turmaRepository.buscarParticipantesDaTurma(idTurma);
        if (!professorPodeGerenciarParticipantes(turma, participantes, emailProfessor.toString())) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas professores vinculados à turma podem remover participantes.");
            return "redirect:/turmas/" + idTurma;
        }

        if (turmaDTO.getEmailPessoa() == null || turmaDTO.getEmailPessoa().isBlank()) {
            logger.warn("[removerPessoas] e-mail vazio para turma {}.", turmaDTO.getId());
            if (logger.isWarnEnabled()) {
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

        if (professorAdmin) {
            redirectAttributes.addFlashAttribute(
                    "mensagemErro",
                    "O professor administrador da turma não pode ser removido."
            );
            redirectAttributes.addFlashAttribute("abrirModalParticipantes", true);
            return "redirect:/turmas/" + idTurma;
        }

        boolean removido = turmaRepository.revomerPessoaTurma(emailPessoa, idTurma);

        if (!removido) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover este participante.");
        } else {
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Participante removido com sucesso.");
        }

        redirectAttributes.addFlashAttribute("abrirModalParticipantes", true);
        return "redirect:/turmas/" + idTurma;
    }

    private void carregarSolicitacoesDominio(Model model, HttpSession session) {
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return;
        }

        try {
            String idProfessor = professorRepository.buscarPorIDProfessor(session.getAttribute("email2FA").toString());
            model.addAttribute("solicitacoesDominio",
                    dominioService.listarPorProfessor(Long.parseLong(idProfessor)));
        } catch (Exception e) {
            logger.error("Erro ao carregar solicitações de domínio do professor.", e);
        }
    }

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
