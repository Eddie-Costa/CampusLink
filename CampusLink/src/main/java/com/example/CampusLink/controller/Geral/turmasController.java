package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.alunoDAO;
import com.example.CampusLink.dao.professorDAO;
import com.example.CampusLink.dao.turmaDAO;
import com.example.CampusLink.dto.TurmaDTO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.service.ConteudoService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.example.CampusLink.service.ArquivoService;
import java.sql.SQLException;
import java.util.List;
import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.dao.usuarioDAO;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.helpers.MessageFormatter;

import java.util.HashMap;
import java.util.Map;

@Controller
public class turmasController {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(turmasController.class);

    @Autowired
    private professorDAO professorDAO;

    @Autowired
    private alunoDAO alunoDAO;

    @Autowired
    private turmaDAO turmaDAO;

    @Autowired
    private ConteudoService conteudoService;

    @Autowired
    private ArquivoService arquivoService;

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
            turmas = turmaDAO.buscarTurmasDoUsuario("aluno", alunoDAO.buscarPorIDAluno(session.getAttribute("email2FA").toString()));
        } else if ("professor".equalsIgnoreCase(tipoUsuario)) {
            turmas = turmaDAO.buscarTurmasDoUsuario("professor", professorDAO.buscarPorIDProfessor(session.getAttribute("email2FA").toString()));
        }

        model.addAttribute("turmas", turmas);

        return "Geral/turmas";
    }

    @GetMapping("/turmas/{id}")
    public String ambienteTurma(@PathVariable String id, Model model, HttpSession session) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        model.addAttribute("idTurma", id);
        TurmaDTO turma = turmaDAO.buscarTurmaPorId(id);
        if (turma == null) {
            return "redirect:/turmas";
        }

        boolean isTurmaAdmin = false;
        if (session.getAttribute("tipoUsuario") != null && "professor".equalsIgnoreCase(session.getAttribute("tipoUsuario").toString())) {
            String idProfessor = professorDAO.buscarPorIDProfessor(session.getAttribute("email2FA").toString());
            isTurmaAdmin = turma.getIdProprietario() != null && turma.getIdProprietario().toString().equals(idProfessor);
        }

        List<TurmaDTO> participantesTurma = turmaDAO.buscarParticipantesDaTurma(id);

        model.addAttribute("turma", turma);
        model.addAttribute("participantesTurma", participantesTurma);
        model.addAttribute("isTurmaAdmin", isTurmaAdmin);
        if (!model.containsAttribute("conteudo")) {
            model.addAttribute("conteudo", new ConteudoDTO());
        }
        List<ConteudoDTO> conteudos = conteudoService.listarPorTurma(Long.parseLong(id));
        model.addAttribute("conteudos", conteudos);

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
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "criarConteudo", null,
                            MessageFormatter.arrayFormat("[criarConteudo] aluno tentou cadastrar conteúdo na turma {}.", new Object[]{id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas/" + id;
        }
        if (result.hasErrors()) {
            model.addAttribute("abrirModalAdicionarConteudo", true);
            return ambienteTurma(String.valueOf(id), model, session);
        }

        String idProfessor = professorDAO.buscarPorIDProfessor(session.getAttribute("email2FA").toString());

        conteudoDTO.setIdTurma(id);
        conteudoDTO.setIdProfessor(Long.parseLong(idProfessor));

        conteudoService.criar(conteudoDTO, arquivo);

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
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "criarTurma", null,
                            "Criação de turma recusada: usuário é aluno.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Geral/home";
        }

        String idProfessor = professorDAO.buscarPorIDProfessor(session.getAttribute("email2FA").toString());

        //Insere turma no banco de dados
        professorDAO.InsertTurmasIntoBD(turmaDTO.getNomeTurma(), turmaDTO.getDescricao(), idProfessor);

        //Insere x_turma no banco de dados
        String idTurma = turmaDAO.buscarUltimaTurmaPorProfessor(idProfessor);
        professorDAO.InsertProfessor_TurmaIntoBD(idProfessor, idTurma);
        logger.info("Turma cadastrada. turmaId={} professorId={}", idTurma, idProfessor);
        if (logger.isInfoEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "INFO", turmasController.class.getName(), "criarTurma", null,
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
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "excluirConteudo", null,
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
    @PostMapping("/turmas/{id}/conteudos/{idConteudo}/editar")
    public String editarConteudo(@PathVariable Long id,
                                 @PathVariable Long idConteudo,
                                 @Valid @ModelAttribute("conteudo") ConteudoDTO conteudoDTO,
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
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "editarConteudo", null,
                            MessageFormatter.arrayFormat("[editarConteudo] aluno tentou editar conteúdo {} da turma {}.", new Object[]{idConteudo, id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/turmas/" + id;
        }

        if (result.hasErrors()) {
            return ambienteTurma(String.valueOf(id), model, session);
        }

        conteudoDTO.setId(idConteudo);
        conteudoService.atualizar(conteudoDTO);

        if (arquivo != null && !arquivo.isEmpty()) {
            arquivoService.salvar(arquivo, idConteudo);
        }

        return "redirect:/turmas/" + id;
    }

    @PostMapping("/adicionarPessoas")
    public String adicionarPessoas(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session, Model model) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[adicionarPessoas] usuário não autenticado; redirecionando para login.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "adicionarPessoas", null,
                            "[adicionarPessoas] usuário não autenticado; redirecionando para login.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        if (session.getAttribute("tipoUsuario") != null && session.getAttribute("tipoUsuario").equals("aluno")) {
            logger.warn("[adicionarPessoas] aluno tentou adicionar pessoa à turma.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "adicionarPessoas", null,
                            "[adicionarPessoas] aluno tentou adicionar pessoa à turma.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Geral/home";
        }

        if (turmaDTO.getEmailPessoa() == null || turmaDTO.getEmailPessoa().isBlank()) {
            logger.warn("[adicionarPessoas] e-mail vazio para turma {}.", turmaDTO.getId());
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "adicionarPessoas", null,
                            MessageFormatter.arrayFormat("[adicionarPessoas] e-mail vazio para turma {}.", new Object[]{turmaDTO.getId()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return ambienteTurma(String.valueOf(turmaDTO.getId()), model, session);
        }

        boolean pessoaAdicionada = turmaDAO.inserirPessoaTurma(turmaDTO.getEmailPessoa().trim(), String.valueOf(turmaDTO.getId()));

        if (pessoaAdicionada) {
            model.addAttribute("mensagemSucesso", "Pessoa adicionada com sucesso.");
        } else {
            model.addAttribute("mensagemErro", "Não foi possível adicionar a pessoa informada.");
        }

        model.addAttribute("abrirModalParticipantes", true);
        return ambienteTurma(String.valueOf(turmaDTO.getId()), model, session);
    }

    @PostMapping("/excluirTurma")
    public String excluirTurma(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session, Model model, RedirectAttributes redirectAttributes) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[excluirTurma] usuário não autenticado; redirecionando para login.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "excluirTurma", null,
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
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "excluirTurma", null,
                            "[excluirTurma] usuário tentou excluir turma sem permissão.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Geral/home";
        }

        String idProfessor = professorDAO.buscarPorIDProfessor(session.getAttribute("email2FA").toString());
        turmaDAO.excluirTurma(String.valueOf(turmaDTO.getId()), idProfessor);
        logger.info("Turma excluída. turmaId={} professorId={}", turmaDTO.getId(), idProfessor);
        if (logger.isInfoEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "INFO", turmasController.class.getName(), "excluirTurma", null,
                        MessageFormatter.arrayFormat("Turma excluída. turmaId={} professorId={}", new Object[]{turmaDTO.getId(), idProfessor}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma excluída com sucesso.");
        return "redirect:/turmas";
    }

    @PostMapping("/removerPessoas")
    public String removerPessoas(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session, Model model) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[removerPessoas] usuário não autenticado; redirecionando para login.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "removerPessoas", null,
                            "[removerPessoas] usuário não autenticado; redirecionando para login.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        if (session.getAttribute("tipoUsuario") != null && session.getAttribute("tipoUsuario").equals("aluno")) {
            logger.warn("[removerPessoas] aluno tentou remover pessoas da turma.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "removerPessoas", null,
                            "[removerPessoas] aluno tentou remover pessoas da turma.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Geral/home";
        }

        if (turmaDTO.getEmailPessoa() == null || turmaDTO.getEmailPessoa().isBlank()) {
            logger.warn("[removerPessoas] e-mail vazio para turma {}.", turmaDTO.getId());
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", turmasController.class.getName(), "removerPessoas", null,
                            MessageFormatter.arrayFormat("[removerPessoas] e-mail vazio para turma {}.", new Object[]{turmaDTO.getId()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return ambienteTurma(String.valueOf(turmaDTO.getId()), model, session);
        }

        boolean removido = turmaDAO.revomerPessoaTurma(turmaDTO.getEmailPessoa().trim(), String.valueOf(turmaDTO.getId()));

        if (!removido) {
            model.addAttribute("mensagemErro", "Não foi possível remover a pessoa informada. Verifique se ela está cadastrada na turma ou se é o professor administrador.");
        } else {
            model.addAttribute("mensagemSucesso", "Pessoa removida com sucesso.");
        }

        model.addAttribute("abrirModalParticipantes", true);
        return ambienteTurma(String.valueOf(turmaDTO.getId()), model, session);
    }


}
