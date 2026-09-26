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
import org.slf4j.helpers.MessageFormatter;
import java.sql.SQLException;
import java.util.List;
import java.io.PrintWriter;
import java.io.StringWriter;

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
            logger.warn("Dados inválidos no cadastro do professor. erros={}", result.getFieldErrors().stream()
                    .map(erro -> "%s: %s".formatted(erro.getField(), erro.getDefaultMessage()))
                    .toList());
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", cadastrarProfessorController.class.getName(), "VerificacaoRegistrar", null,
                            MessageFormatter.arrayFormat("Dados inválidos no cadastro do professor. erros={}", new Object[]{result.getFieldErrors().stream()
                    .map(erro -> "%s: %s".formatted(erro.getField(), erro.getDefaultMessage()))
                    .toList()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            model.addAttribute("mensagemDeErro", "Dados inválidos. Verifique o formulário.");
            return "Usuarios/Professor/cadastrarProfessor";
        }

        session.removeAttribute("verificado");

        session.setAttribute("email2FA", professor.getEmail().toLowerCase());
        session.setAttribute("redirect", "Cadastro");
        session.setAttribute("tipoUsuario", "professor");
        session.setAttribute("Matricula", professor.getMatricula());
        session.setAttribute("Nome", professor.getNome().toLowerCase());
        session.setAttribute("Email", professor.getEmail().toLowerCase());
        session.setAttribute("Telefone", professor.getTelefone());
        session.setAttribute("DataNasc", professor.getDataNasc());
        session.setAttribute("Senha", encoder.encode(professor.getSenha()));

        if (twoFactorEnabled) {
            String codigo = twoFactorService.gerarCodigo(professor.getEmail().toLowerCase());
            emailService.enviarCodigo(professor.getEmail().toLowerCase(), codigo);

            return "redirect:/verificarProfessor";
        } else {
            session.setAttribute("verificado", "true");
            return "redirect:/cadastrarProfessorVerificado";
        }
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
            logger.warn("Cadastro recusado por dados duplicados. perfil=professor motivos={}", erros);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", cadastrarProfessorController.class.getName(), "registrar", null,
                            MessageFormatter.arrayFormat("Cadastro recusado por dados duplicados. perfil=professor motivos={}", new Object[]{erros}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
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

            logger.info("Sucesso ao cadastrar novo professor com email: {}", professor.getEmail());
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", cadastrarProfessorController.class.getName(), "registrar", null,
                            MessageFormatter.arrayFormat("Sucesso ao cadastrar novo professor com email: {}", new Object[]{professor.getEmail()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Usuarios/Professor/loginProfessor";

        } catch (SQLException e) {
            logger.error("Erro ao inserir professor no banco com email: {}", professor.getEmail(), e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", cadastrarProfessorController.class.getName(), "registrar", null,
                            MessageFormatter.arrayFormat("Erro ao inserir professor no banco com email: {}", new Object[]{professor.getEmail()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("professor", professor);
            return "Usuarios/Professor/cadastrarProfessor";
        }
    }
}
