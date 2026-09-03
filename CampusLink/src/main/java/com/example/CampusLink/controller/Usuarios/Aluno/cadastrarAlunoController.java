package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.cadastrarAlunoDTO;
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
public class cadastrarAlunoController {


    private final usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(cadastrarAlunoController.class);
    
    public cadastrarAlunoController(usuarioDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    @GetMapping("/cadastrarAluno")
    public String CadastrarAluno(org.springframework.ui.Model model) {
        model.addAttribute("aluno", new cadastrarAlunoDTO());
        return "Usuarios/Aluno/cadastrarAluno";
    }

    @PostMapping("/cadastrarAluno")
    public String registrar(@Valid @ModelAttribute("aluno") cadastrarAlunoDTO aluno, BindingResult result, org.springframework.ui.Model model) {

        //Encriptador
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        if (result.hasErrors()) {
            logger.warn("Erro ao registrar o aluno com email:" + aluno.getEmail() + " Erro: " + result.getAllErrors());
            model.addAttribute("mensagemDeErro", "Dados inválidos. Verifique o formulário.");
            return "Usuarios/Aluno/cadastrarAluno";
        }

        List<String> erros = usuarioDAO.validarDadosDuplicados(
                "Aluno",
                aluno.getRgm(),
                aluno.getEmail().toLowerCase(),
                aluno.getTelefone()
        );

        //Verificar duplicidade antes de tentar inserir
        if (!erros.isEmpty()) {
            SQLErrorHandler.VerificarErro("aluno", erros, model);
            model.addAttribute("aluno", aluno);
            return "Usuarios/Aluno/cadastrarAluno";
        }

        try {
            //Inserção de dados no BD
            usuarioDAO.InsertCadastroUsuarioIntoBD(
                    "Aluno",
                    aluno.getRgm(),
                    aluno.getNome().toLowerCase(),
                    aluno.getEmail().toLowerCase(),
                    aluno.getTelefone(),
                    aluno.getDataNasc(),
                    encoder.encode(aluno.getSenha())
            );

            logger.info("Sucesso ao cadastrar novo aluno com email: " + aluno.getEmail());
            return "Usuarios/Aluno/loginAluno";

        } catch (SQLException e) {
            logger.error("Erro ao inserir aluno no banco com email: {}", aluno.getEmail(), e);

            model.addAttribute("aluno", aluno);
            return "Usuarios/Aluno/cadastrarAluno";
        }
    }
    
}
