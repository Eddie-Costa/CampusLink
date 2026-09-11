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

import java.sql.SQLException;
import java.util.List;

@Controller
public class turmasController {

    private static final Logger logger = LoggerFactory.getLogger(turmasController.class);

    @Autowired
    private professorDAO professorDAO;

    @Autowired
    private alunoDAO alunoDAO;

    @Autowired
    private turmaDAO turmaDAO;

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

        model.addAttribute("turma", turma);
        return "Geral/ambienteTurma";
    }

    @PostMapping("/criarTurma")
    public String criarTurma(@Valid @ModelAttribute("turma") TurmaDTO turmaDTO, BindingResult result, HttpSession session, Model model) throws SQLException {

        if (result.hasErrors()) {
            model.addAttribute("abrirModalCriarTurma", true);
            return "Geral/turmas";
        }

        if (session.getAttribute("tipoUsuario").equals("aluno")) {
            return "Geral/home";
        }

        String idProfessor = professorDAO.buscarPorIDProfessor(session.getAttribute("email2FA").toString());

        //Insere turma no banco de dados
        professorDAO.InsertTurmasIntoBD(turmaDTO.getNomeTurma(), turmaDTO.getDescricao(), idProfessor);

        //Insere x_turma no banco de dados
        professorDAO.InsertProfessor_TurmaIntoBD(idProfessor, turmaDAO.buscarUltimaTurmaPorProfessor(idProfessor));

        return "redirect:/turmas";
    }

    @PostMapping("/adicionarPessoas")
    public String adicionarPessoas(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session, Model model) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[adicionarPessoas] usuário não autenticado; redirecionando para login.");
            return "redirect:/login";
        }

        if (session.getAttribute("tipoUsuario") != null && session.getAttribute("tipoUsuario").equals("aluno")) {
            logger.warn("[adicionarPessoas] aluno tentou adicionar pessoa à turma.");
            return "Geral/home";
        }

        if (turmaDTO.getEmailPessoa() == null || turmaDTO.getEmailPessoa().isBlank()) {
            logger.warn("[adicionarPessoas] e-mail vazio para turma {}.", turmaDTO.getId());
            model.addAttribute("idTurma", turmaDTO.getId());
            model.addAttribute("turma", turmaDAO.buscarTurmaPorId(String.valueOf(turmaDTO.getId())));
            return "Geral/ambienteTurma";
        }

        turmaDAO.inserirPessoaTurma(turmaDTO.getEmailPessoa().trim(), String.valueOf(turmaDTO.getId()));

        return "redirect:/turmas";
    }

    @PostMapping("/removerPessoas")
    public String removerPessoas(@ModelAttribute("turma") TurmaDTO turmaDTO, HttpSession session, Model model) throws SQLException {

        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("[removerPessoas] usuário não autenticado; redirecionando para login.");
            return "redirect:/login";
        }

        if (session.getAttribute("tipoUsuario") != null && session.getAttribute("tipoUsuario").equals("aluno")) {
            logger.warn("[removerPessoas] aluno tentou remover pessoas da turma.");
            return "Geral/home";
        }

        if (turmaDTO.getEmailPessoa() == null || turmaDTO.getEmailPessoa().isBlank()) {
            logger.warn("[removerPessoas] e-mail vazio para turma {}.", turmaDTO.getId());
            model.addAttribute("idTurma", turmaDTO.getId());
            model.addAttribute("turma", turmaDAO.buscarTurmaPorId(String.valueOf(turmaDTO.getId())));
            return "Geral/ambienteTurma";
        }

        turmaDAO.revomerPessoaTurma(turmaDTO.getEmailPessoa().trim(), String.valueOf(turmaDTO.getId()));

        return "redirect:/turmas";
    }


}
