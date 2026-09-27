package com.example.CampusLink.controller.Usuarios.Admin;

import org.slf4j.helpers.MessageFormatter;
import java.io.StringWriter;
import java.io.PrintWriter;
import com.example.CampusLink.dao.usuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.CampusLink.dao.AdminTurmaDAO;
import com.example.CampusLink.dto.Admin.cadastrarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.editarTurmaAdminDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Admin.turmaAdminDTO;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Controller
public class AdminTurmasController {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(AdminTurmasController.class);

    private static final String URL_LOGIN_ADMIN = "/gestao/8f3c1d7a-2b94-4e61-a5c8-7d2f9b4a6e31";

    private final AdminTurmaDAO adminTurmaDAO;

    public AdminTurmasController(AdminTurmaDAO adminTurmaDAO) {
        this.adminTurmaDAO = adminTurmaDAO;
    }

    @GetMapping("/admin/turmas")
    public String listarTurmas(HttpSession session, Model model) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            List<turmaAdminDTO> turmas = adminTurmaDAO.listarTurmas();
            model.addAttribute("turmas", turmas);

        } catch (SQLException e) {
            logger.error("erro ao carregar turmas para o administrador", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "listarTurmas", null,
                            "erro ao carregar turmas para o administrador", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("mensagemErro", "Não foi possível carregar as turmas.");
            model.addAttribute("turmas", new ArrayList<turmaAdminDTO>());
        }

        return "Usuarios/Admin/turmasAdmin";
    }

    @GetMapping("/admin/turmas/cadastrar")
    public String exibirCadastroTurma(HttpSession session, Model model) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            List<usuarioAdminDTO> professores = adminTurmaDAO.listarProfessoresAtivos();

            model.addAttribute("turma", new cadastrarTurmaAdminDTO());
            model.addAttribute("professores", professores);

        } catch (SQLException e) {
            logger.error("erro ao carregar professores para cadastro de turma", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "exibirCadastroTurma", null,
                            "erro ao carregar professores para cadastro de turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("turma", new cadastrarTurmaAdminDTO());
            model.addAttribute("professores", new ArrayList<usuarioAdminDTO>());
            model.addAttribute("mensagemErro", "Não foi possível carregar os professores.");
        }

        return "Usuarios/Admin/cadastrarTurmaAdmin";
    }

    @PostMapping("/admin/turmas/cadastrar")
    public String cadastrarTurma(
            @Valid @ModelAttribute("turma") cadastrarTurmaAdminDTO turma,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        if (bindingResult.hasErrors()) {
            carregarProfessores(model);
            return "Usuarios/Admin/cadastrarTurmaAdmin";
        }

        try {
            adminTurmaDAO.cadastrarTurma(turma);
            logger.info("Turma cadastrada pelo administrador. nome={} professorId={}", turma.getNomeTurma(), turma.getIdProprietario());
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "cadastrarTurma", null,
                            MessageFormatter.arrayFormat("Turma cadastrada pelo administrador. nome={} professorId={}", new Object[]{turma.getNomeTurma(), turma.getIdProprietario()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma cadastrada com sucesso!");

            return "redirect:/admin/turmas";

        } catch (SQLException e) {
            logger.error("erro ao cadastrar turma", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "cadastrarTurma", null,
                            "erro ao cadastrar turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            carregarProfessores(model);
            model.addAttribute("mensagemErro", "Não foi possível cadastrar a turma.");

            return "Usuarios/Admin/cadastrarTurmaAdmin";
        }
    }

    @GetMapping("/admin/turmas/{idTurma}/editar")
    public String exibirEdicaoTurma(
            @PathVariable Long idTurma,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            editarTurmaAdminDTO turma = adminTurmaDAO.buscarTurmaPorId(idTurma);

            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            model.addAttribute("turma", turma);
            model.addAttribute("professores", adminTurmaDAO.listarProfessoresAtivos());

            return "Usuarios/Admin/editarTurmaAdmin";

        } catch (SQLException e) {
            logger.error("erro ao carregar turma para edição", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "exibirEdicaoTurma", null,
                            "erro ao carregar turma para edição", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível carregar a turma.");

            return "redirect:/admin/turmas";
        }
    }

    @PostMapping("/admin/turmas/{idTurma}/editar")
    public String editarTurma(
            @PathVariable Long idTurma,
            @Valid @ModelAttribute("turma") editarTurmaAdminDTO turma,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        turma.setId(idTurma);

        if (bindingResult.hasErrors()) {
            carregarProfessores(model);
            return "Usuarios/Admin/editarTurmaAdmin";
        }

        try {
            editarTurmaAdminDTO turmaExistente = adminTurmaDAO.buscarTurmaPorId(idTurma);

            if (turmaExistente == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            boolean atualizado = adminTurmaDAO.atualizarTurma(turma);

            if (!atualizado) {
                logger.warn("Atualização de turma não realizada. turmaId={}", idTurma);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "editarTurma", null,
                                MessageFormatter.arrayFormat("Atualização de turma não realizada. turmaId={}", new Object[]{idTurma}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                carregarProfessores(model);
                model.addAttribute("mensagemErro", "Não foi possível atualizar a turma.");
                return "Usuarios/Admin/editarTurmaAdmin";
            }

            logger.info("Turma atualizada pelo administrador. turmaId={} professorId={}", idTurma, turma.getIdProprietario());
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "editarTurma", null,
                            MessageFormatter.arrayFormat("Turma atualizada pelo administrador. turmaId={} professorId={}", new Object[]{idTurma, turma.getIdProprietario()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma atualizada com sucesso!");

            return "redirect:/admin/turmas";

        } catch (SQLException e) {
            logger.error("erro ao atualizar turma", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "editarTurma", null,
                            "erro ao atualizar turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            carregarProfessores(model);
            model.addAttribute("mensagemErro", "Não foi possível atualizar a turma.");

            return "Usuarios/Admin/editarTurmaAdmin";
        }
    }

    @GetMapping("/admin/turmas/{idTurma}/alunos")
    public String listarAlunosDaTurma(
            @PathVariable Long idTurma,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            editarTurmaAdminDTO turma = adminTurmaDAO.buscarTurmaPorId(idTurma);

            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            List<usuarioAdminDTO> alunos = adminTurmaDAO.listarAlunosDaTurma(idTurma);
            List<usuarioAdminDTO> alunosDisponiveis = adminTurmaDAO.listarAlunosDisponiveis(idTurma);

            model.addAttribute("turma", turma);
            model.addAttribute("alunos", alunos);
            model.addAttribute("alunosDisponiveis", alunosDisponiveis);

            return "Usuarios/Admin/alunosTurmaAdmin";

        } catch (SQLException e) {
            logger.error("erro ao carregar alunos da turma", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "listarAlunosDaTurma", null,
                            "erro ao carregar alunos da turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível carregar os alunos da turma.");

            return "redirect:/admin/turmas";
        }
    }

    @PostMapping("/admin/turmas/{idTurma}/alunos/adicionar")
    public String adicionarAlunoNaTurma(
            @PathVariable Long idTurma,
            @RequestParam("idAluno") Long idAluno,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            editarTurmaAdminDTO turma = adminTurmaDAO.buscarTurmaPorId(idTurma);

            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            boolean adicionado = adminTurmaDAO.adicionarAlunoNaTurma(idTurma, idAluno);

            if (!adicionado) {
                logger.warn("Inclusão de aluno não realizada. turmaId={} alunoId={}", idTurma, idAluno);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "adicionarAlunoNaTurma", null,
                                MessageFormatter.arrayFormat("Inclusão de aluno não realizada. turmaId={} alunoId={}", new Object[]{idTurma, idAluno}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível adicionar o aluno à turma.");
                return "redirect:/admin/turmas/" + idTurma + "/alunos";
            }

            logger.info("Aluno adicionado à turma pelo administrador. turmaId={} alunoId={}", idTurma, idAluno);
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "adicionarAlunoNaTurma", null,
                            MessageFormatter.arrayFormat("Aluno adicionado à turma pelo administrador. turmaId={} alunoId={}", new Object[]{idTurma, idAluno}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Aluno adicionado à turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/alunos";

        } catch (SQLException e) {
            logger.error("erro ao adicionar aluno na turma", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "adicionarAlunoNaTurma", null,
                            "erro ao adicionar aluno na turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível adicionar o aluno à turma.");

            return "redirect:/admin/turmas/" + idTurma + "/alunos";
        }
    }

    @PostMapping("/admin/turmas/{idTurma}/alunos/{idAluno}/remover")
    public String removerAlunoDaTurma(
            @PathVariable Long idTurma,
            @PathVariable Long idAluno,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            editarTurmaAdminDTO turma = adminTurmaDAO.buscarTurmaPorId(idTurma);

            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            boolean removido = adminTurmaDAO.removerAlunoDaTurma(idTurma, idAluno);

            if (!removido) {
                logger.warn("Remoção de aluno não realizada. turmaId={} alunoId={}", idTurma, idAluno);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "removerAlunoDaTurma", null,
                                MessageFormatter.arrayFormat("Remoção de aluno não realizada. turmaId={} alunoId={}", new Object[]{idTurma, idAluno}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o aluno da turma.");
                return "redirect:/admin/turmas/" + idTurma + "/alunos";
            }

            logger.info("Aluno removido da turma pelo administrador. turmaId={} alunoId={}", idTurma, idAluno);
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "removerAlunoDaTurma", null,
                            MessageFormatter.arrayFormat("Aluno removido da turma pelo administrador. turmaId={} alunoId={}", new Object[]{idTurma, idAluno}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Aluno removido da turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/alunos";

        } catch (SQLException e) {
            logger.error("erro ao remover aluno da turma", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "removerAlunoDaTurma", null,
                            "erro ao remover aluno da turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o aluno da turma.");

            return "redirect:/admin/turmas/" + idTurma + "/alunos";
        }
    }

    @GetMapping("/admin/turmas/{idTurma}/professores")
    public String listarProfessoresDaTurma(
            @PathVariable Long idTurma,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            editarTurmaAdminDTO turma = adminTurmaDAO.buscarTurmaPorId(idTurma);

            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            List<usuarioAdminDTO> professores = adminTurmaDAO.listarProfessoresDaTurma(idTurma);
            List<usuarioAdminDTO> professoresDisponiveis = adminTurmaDAO.listarProfessoresDisponiveis(idTurma);

            model.addAttribute("turma", turma);
            model.addAttribute("professores", professores);
            model.addAttribute("professoresDisponiveis", professoresDisponiveis);

            return "Usuarios/Admin/professoresTurmaAdmin";

        } catch (SQLException e) {
            logger.error("erro ao carregar professores da turma", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "listarProfessoresDaTurma", null,
                            "erro ao carregar professores da turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível carregar os professores da turma.");

            return "redirect:/admin/turmas";
        }
    }

    @PostMapping("/admin/turmas/{idTurma}/professores/adicionar")
    public String adicionarProfessorNaTurma(
            @PathVariable Long idTurma,
            @RequestParam("idProfessor") Long idProfessor,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            editarTurmaAdminDTO turma = adminTurmaDAO.buscarTurmaPorId(idTurma);

            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            boolean adicionado = adminTurmaDAO.adicionarProfessorNaTurma(idTurma, idProfessor);

            if (!adicionado) {
                logger.warn("Inclusão de professor não realizada. turmaId={} professorId={}", idTurma, idProfessor);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "adicionarProfessorNaTurma", null,
                                MessageFormatter.arrayFormat("Inclusão de professor não realizada. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível adicionar o professor à turma.");
                return "redirect:/admin/turmas/" + idTurma + "/professores";
            }

            logger.info("Professor adicionado à turma pelo administrador. turmaId={} professorId={}", idTurma, idProfessor);
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "adicionarProfessorNaTurma", null,
                            MessageFormatter.arrayFormat("Professor adicionado à turma pelo administrador. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Professor adicionado à turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/professores";

        } catch (SQLException e) {
            logger.error("erro ao adicionar professor na turma", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "adicionarProfessorNaTurma", null,
                            "erro ao adicionar professor na turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível adicionar o professor à turma.");

            return "redirect:/admin/turmas/" + idTurma + "/professores";
        }
    }

    @PostMapping("/admin/turmas/{idTurma}/professores/{idProfessor}/remover")
    public String removerProfessorDaTurma(
            @PathVariable Long idTurma,
            @PathVariable Long idProfessor,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            editarTurmaAdminDTO turma = adminTurmaDAO.buscarTurmaPorId(idTurma);

            if (turma == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return "redirect:/admin/turmas";
            }

            // o professor responsavel pela turma nao pode ser removido
            if (turma.getIdProprietario() != null && turma.getIdProprietario().equals(idProfessor)) {
                logger.warn("Remoção recusada: professor responsável pela turma. turmaId={} professorId={}", idTurma, idProfessor);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "removerProfessorDaTurma", null,
                                MessageFormatter.arrayFormat("Remoção recusada: professor responsável pela turma. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "O professor responsável pela turma não pode ser removido. Altere o responsável primeiro."
                );

                return "redirect:/admin/turmas/" + idTurma + "/professores";
            }

            boolean removido = adminTurmaDAO.removerProfessorDaTurma(idTurma, idProfessor);

            if (!removido) {
                logger.warn("Remoção de professor não realizada. turmaId={} professorId={}", idTurma, idProfessor);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", AdminTurmasController.class.getName(), "removerProfessorDaTurma", null,
                                MessageFormatter.arrayFormat("Remoção de professor não realizada. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o professor da turma.");
                return "redirect:/admin/turmas/" + idTurma + "/professores";
            }

            logger.info("Professor removido da turma pelo administrador. turmaId={} professorId={}", idTurma, idProfessor);
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", AdminTurmasController.class.getName(), "removerProfessorDaTurma", null,
                            MessageFormatter.arrayFormat("Professor removido da turma pelo administrador. turmaId={} professorId={}", new Object[]{idTurma, idProfessor}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Professor removido da turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/professores";

        } catch (SQLException e) {
            logger.error("erro ao remover professor da turma", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "removerProfessorDaTurma", null,
                            "erro ao remover professor da turma", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o professor da turma.");

            return "redirect:/admin/turmas/" + idTurma + "/professores";
        }
    }

    // carrega os professores ativos usados nos formularios
    private void carregarProfessores(Model model) {

        try {
            model.addAttribute("professores", adminTurmaDAO.listarProfessoresAtivos());

        } catch (SQLException e) {
            logger.error("erro ao carregar professores", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminTurmasController.class.getName(), "carregarProfessores", null,
                            "erro ao carregar professores", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("professores", new ArrayList<usuarioAdminDTO>());
        }
    }

    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        if (!(usuarioLogado instanceof loginAdminDTO)) {
            return false;
        }

        if (!(emailSessao instanceof String)) {
            return false;
        }

        loginAdminDTO admin = (loginAdminDTO) usuarioLogado;

        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        return admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}
