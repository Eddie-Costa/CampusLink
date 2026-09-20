package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.cadastrarAlunoDTO;
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
public class cadastrarAlunoController {

    @Value("${campuslink.twofa.enabled:true}")
    private boolean twoFactorEnabled;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

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
    public String VerificacaoRegistrar(@Valid @ModelAttribute("aluno") cadastrarAlunoDTO aluno, BindingResult result, org.springframework.ui.Model model, HttpSession session) {

        //Encriptador
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        if (result.hasErrors()) {
            logger.warn("Erro ao registrar o aluno com email:" + aluno.getEmail() + " Erro: " + result.getAllErrors());
            model.addAttribute("mensagemDeErro", "Dados inválidos. Verifique o formulário.");
            return "Usuarios/Aluno/cadastrarAluno";
        }

        session.removeAttribute("verificado");

        if (twoFactorEnabled) {
            String codigo = twoFactorService.gerarCodigo(aluno.getEmail().toLowerCase());
            emailService.enviarCodigo(aluno.getEmail().toLowerCase(), codigo);

            session.setAttribute("email2FA", aluno.getEmail().toLowerCase());
            session.setAttribute("redirect", "Cadastro");
            session.setAttribute("tipoUsuario", "aluno");
            session.setAttribute("Rgm", aluno.getRgm());
            session.setAttribute("Nome", aluno.getNome().toLowerCase());
            session.setAttribute("Email", aluno.getEmail().toLowerCase());
            session.setAttribute("Telefone", aluno.getTelefone());
            session.setAttribute("DataNasc", aluno.getDataNasc());
            session.setAttribute("Senha", encoder.encode(aluno.getSenha()));

            return "redirect:/verificarAluno";
        }

        return "Usuarios/Aluno/cadastrarAluno";
    }

    @GetMapping("/cadastrarAlunoVerificado")
    public String registrar(@ModelAttribute("aluno") cadastrarAlunoDTO aluno, BindingResult result, org.springframework.ui.Model model, HttpSession session) {

        if (!"true".equals(session.getAttribute("verificado"))) {
            return "redirect:/cadastrarAluno";
        }

        aluno.setRgm(session.getAttribute("Rgm").toString());
        aluno.setNome(session.getAttribute("Nome").toString());
        aluno.setEmail(session.getAttribute("Email").toString());
        aluno.setTelefone(session.getAttribute("Telefone").toString());
        aluno.setDataNasc(session.getAttribute("DataNasc").toString());
        aluno.setSenha(session.getAttribute("Senha").toString());

        session.removeAttribute("Rgm");
        session.removeAttribute("Nome");
        session.removeAttribute("Email");
        session.removeAttribute("Telefone");
        session.removeAttribute("DataNasc");
        session.removeAttribute("Senha");
        session.removeAttribute("verificado");

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
                    aluno.getSenha()
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