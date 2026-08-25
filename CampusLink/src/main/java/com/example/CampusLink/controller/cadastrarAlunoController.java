package com.example.CampusLink.controller;

import com.example.CampusLink.dao.alunoDAO;
import com.example.CampusLink.dto.cadastrarAlunoDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.sql.SQLException;



@Controller
public class cadastrarAlunoController {

    @Autowired
    private alunoDAO alunoDAO;

    private static final Logger logger = LoggerFactory.getLogger(cadastrarAlunoController.class);
    
    @GetMapping("/cadastrarAluno")
    public String CadastrarAluno(org.springframework.ui.Model model) {
        model.addAttribute("aluno", new cadastrarAlunoDTO());
        return "cadastrarAluno";
    }

    @PostMapping("/cadastrarAluno")
    public String registrar(@Valid @ModelAttribute("aluno") cadastrarAlunoDTO aluno, BindingResult result) throws SQLException {

        if (result.hasErrors()) {
            logger.warn("Erro ao registrar o aluno com email:" +aluno.getEmail()+ " Erro: " + result.getAllErrors());
            return "subscriptionPage";
        }

        //Encriptador
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        //Inserção de dados no BD
        alunoDAO.InsertCadastroAlunoIntoBD(aluno.getRgm(), aluno.getNome().toLowerCase(), aluno.getEmail().toLowerCase(), aluno.getTelefone(), aluno.getDataNasc(), encoder.encode(aluno.getSenha()));
        logger.info("Sucesso ao cadastrar novo aluno com email: " + aluno.getEmail());

        return "loginPage";
    }
    
}
