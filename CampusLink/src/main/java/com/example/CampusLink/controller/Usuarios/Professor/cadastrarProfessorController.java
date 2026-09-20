package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Professor.cadastrarProfessorDTO;
import com.example.CampusLink.exception.SQLErrorHandler;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${campuslink.twofa.enabled:true}")
    private boolean twoFactorEnabled;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    private final usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(cadastrarProfessorController.class);

    public cadastrarProfessorController(usuarioDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    @GetMapping("/cadastrarProfessor")
    public String CadastrarProfessor(org.springframework.ui.Model model) {
        model.addAttribute("professor", new cadastrarProfessorDTO());
        return "Usuarios/Professor/cadastrarProfessor";
    }

    @PostMapping("/cadastrarProfessor")
    public String VerificacaoRegistrar(@Valid @ModelAttribute("professor") cadastrarProfessorDTO professor, BindingResult result, org.springframework.ui.Model model, HttpSession session) {

        //Encriptador
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        if (result.hasErrors()) {
            logger.warn("Erro ao registrar o professor com email:" + professor.getEmail() + " Erro: " + result.getAllErrors());
            model.addAttribute("mensagemDeErro", "Dados inválidos. Verifique o formulário.");
            return "Usuarios/Professor/cadastrarProfessor";
        }

        session.removeAttribute("verificado");

        if (twoFactorEnabled) {
            String codigo = twoFactorService.gerarCodigo(professor.getEmail().toLowerCase());
            emailService.enviarCodigo(professor.getEmail().toLowerCase(), codigo);

            session.setAttribute("email2FA", professor.getEmail().toLowerCase());
            session.setAttribute("redirect", "Cadastro");
            session.setAttribute("tipoUsuario", "professor");
            session.setAttribute("Matricula", professor.getMatricula());
            session.setAttribute("Nome", professor.getNome().toLowerCase());
            session.setAttribute("Email", professor.getEmail().toLowerCase());
            session.setAttribute("Telefone", professor.getTelefone());
            session.setAttribute("DataNasc", professor.getDataNasc());
            session.setAttribute("Senha", encoder.encode(professor.getSenha()));

            return "redirect:/verificarProfessor";
        }

        return "Usuarios/Professor/cadastrarProfessor";
    }

    @GetMapping("/cadastrarProfessorVerificado")
    public String registrar(@Valid @ModelAttribute("professor") cadastrarProfessorDTO professor, BindingResult result, org.springframework.ui.Model model, HttpSession session) {

        if (!"true".equals(session.getAttribute("verificado"))) {
            return "redirect:/cadastrarProfessor";
        }

        professor.setMatricula(session.getAttribute("Matricula").toString());;
        professor.setNome(session.getAttribute("Nome").toString());
        professor.setEmail(session.getAttribute("Email").toString());
        professor.setTelefone(session.getAttribute("Telefone").toString());
        professor.setDataNasc(session.getAttribute("DataNasc").toString());
        professor.setSenha(session.getAttribute("Senha").toString());

        session.removeAttribute("Matricula");
        session.removeAttribute("Nome");
        session.removeAttribute("Email");
        session.removeAttribute("Telefone");
        session.removeAttribute("DataNasc");
        session.removeAttribute("Senha");
        session.removeAttribute("verificado");

        //Encriptador
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

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
            return "Usuarios/Professor/cadastrarProfessor";
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
                    professor.getSenha()
            );

            logger.info("Sucesso ao cadastrar novo professor com email: " + professor.getEmail());
            return "Usuarios/Professor/loginProfessor";

        } catch (SQLException e) {
            logger.error("Erro ao inserir professor no banco com email: {}", professor.getEmail(), e);

            model.addAttribute("professor", professor);
            return "Usuarios/Professor/cadastrarProfessor";
        }
    }
}