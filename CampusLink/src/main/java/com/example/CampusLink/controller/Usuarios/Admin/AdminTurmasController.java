package com.example.CampusLink.controller.Usuarios.Admin;

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

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma cadastrada com sucesso!");

            return "redirect:/admin/turmas";

        } catch (SQLException e) {
            logger.error("erro ao cadastrar turma", e);

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
                carregarProfessores(model);
                model.addAttribute("mensagemErro", "Não foi possível atualizar a turma.");
                return "Usuarios/Admin/editarTurmaAdmin";
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Turma atualizada com sucesso!");

            return "redirect:/admin/turmas";

        } catch (SQLException e) {
            logger.error("erro ao atualizar turma", e);

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
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível adicionar o aluno à turma.");
                return "redirect:/admin/turmas/" + idTurma + "/alunos";
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Aluno adicionado à turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/alunos";

        } catch (SQLException e) {
            logger.error("erro ao adicionar aluno na turma", e);

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
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o aluno da turma.");
                return "redirect:/admin/turmas/" + idTurma + "/alunos";
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Aluno removido da turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/alunos";

        } catch (SQLException e) {
            logger.error("erro ao remover aluno da turma", e);

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
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível adicionar o professor à turma.");
                return "redirect:/admin/turmas/" + idTurma + "/professores";
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Professor adicionado à turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/professores";

        } catch (SQLException e) {
            logger.error("erro ao adicionar professor na turma", e);

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
                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "O professor responsável pela turma não pode ser removido. Altere o responsável primeiro."
                );

                return "redirect:/admin/turmas/" + idTurma + "/professores";
            }

            boolean removido = adminTurmaDAO.removerProfessorDaTurma(idTurma, idProfessor);

            if (!removido) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o professor da turma.");
                return "redirect:/admin/turmas/" + idTurma + "/professores";
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Professor removido da turma com sucesso!");

            return "redirect:/admin/turmas/" + idTurma + "/professores";

        } catch (SQLException e) {
            logger.error("erro ao remover professor da turma", e);

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