package com.example.CampusLink.controller;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.cadastrarProfessorDTO;
import com.example.CampusLink.exception.SQLErrorHandler;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import java.sql.SQLException;
import java.util.List;

@Controller
public class cadastrarProfessorController {

    private final usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(cadastrarProfessorController.class);

    public cadastrarProfessorController(usuarioDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    @GetMapping("/cadastrarProfessor")
    public String CadastrarProfessor(org.springframework.ui.Model model) {
        model.addAttribute("professor", new cadastrarProfessorDTO());
        return "cadastrarProfessor";
    }

    @PostMapping("/cadastrarProfessor")
    public String registrar(@Valid @ModelAttribute("professor") cadastrarProfessorDTO professor, BindingResult result, org.springframework.ui.Model model) {

        //Encriptador
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        if (result.hasErrors()) {
            logger.warn("Erro ao registrar o professor com email:" + professor.getEmail() + " Erro: " + result.getAllErrors());
            model.addAttribute("mensagemDeErro", "Dados inválidos. Verifique o formulário.");
            return "cadastrarProfessor";
        }

        List<String> erros = usuarioDAO.validarDadosDuplicados(
                "Professor",
                professor.getMatricula(),
                professor.getEmail().toLowerCase(),
                professor.getTelefone()
        );

        //Verificar duplicidade antes de tentar inserir
        if (!erros.isEmpty()) {
            SQLErrorHandler.VerificarErro("professor", erros, model);
            model.addAttribute("professor", professor);
            return "cadastrarProfessor";
        }

        try {
            //Inserção de dados no BD
            usuarioDAO.InsertCadastroUsuarioIntoBD(
                    "Professor",
                    professor.getMatricula(),
                    professor.getNome().toLowerCase(),
                    professor.getEmail().toLowerCase(),
                    professor.getTelefone(),
                    professor.getDataNasc(),
                    encoder.encode(professor.getSenha())
            );

            logger.info("Sucesso ao cadastrar novo professor com email: " + professor.getEmail());
            return "loginPage";

        } catch (SQLException e) {
            logger.error("Erro ao inserir professor no banco com email: {}", professor.getEmail(), e);

            model.addAttribute("professor", professor);
            return "cadastrarProfessor";
        }
    }
}
