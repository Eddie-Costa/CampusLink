package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.professorDAO;
import com.example.CampusLink.dto.TurmaDTO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.sql.SQLException;

@Controller
public class turmasController {

    @Autowired
    private professorDAO professorDAO;

    @GetMapping("/turmas")
    public String Turmas(Model model, HttpSession session){
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        model.addAttribute("turma", new TurmaDTO());

        return "Geral/turmas";
    }

    @PostMapping("/criarTurma")
    public String criarTurma(@Valid @ModelAttribute("turma") TurmaDTO turmaDTO, BindingResult result, HttpSession session, Model model) {

        if (result.hasErrors()) {
            model.addAttribute("abrirModalCriarTurma", true);
            return "Geral/turmas";
        }

        return "redirect:/turmas";
    }


}
