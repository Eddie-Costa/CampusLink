// inscricao de professores organiza o cadastro publico desse perfil
package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.Professor.cadastrarProfessorDTO;
import com.example.CampusLink.exception.SQLErrorHandler;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.io.PrintWriter;
import java.io.StringWriter;

// cadastro de professor esta classe guarda os dados ate a verificacao e confere duplicidades antes de gravar
@Controller
public class cadastrarProfessorController {

    @Value("${campuslink.twofa.enabled:true}")
    private boolean twoFactorEnabled;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    private final UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(cadastrarProfessorController.class);

    // preparar cadastro de professor este construtor recebe as operacoes de cadastro de usuarios
    public cadastrarProfessorController(UsuarioRepository UsuarioRepository) {
        this.UsuarioRepository = UsuarioRepository;
    }

    // abrir cadastro de professor este metodo prepara o formulario para quem ainda nao entrou
    @GetMapping("/cadastrarProfessor")
    public String CadastrarProfessor(org.springframework.ui.Model model, HttpSession session) {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        model.addAttribute("professor", new cadastrarProfessorDTO());
        return "Usuarios/Professor/cadastrarProfessor";
    }

    // verificar cadastro de professor este metodo guarda os dados na sessao e inicia a confirmacao por email
    @PostMapping("/cadastrarProfessor")
    public String VerificacaoRegistrar(@Valid @ModelAttribute("professor") cadastrarProfessorDTO professor, BindingResult result, org.springframework.ui.Model model, HttpSession session) {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }


        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        // validar cadastro de professor reapresenta o formulario quando os dados possuem erros
        if (result.hasErrors()) {
            logger.warn("Dados inválidos no cadastro do professor. erros={}", result.getFieldErrors().stream()
                    .map(erro -> "%s: %s".formatted(erro.getField(), erro.getDefaultMessage()))
                    .toList());
            // registro de avisos confere se esta habilitado ao verificar cadastro de professor
            if (logger.isWarnEnabled()) {
                // gravar historico ao verificar cadastro de professor tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", cadastrarProfessorController.class.getName(), "VerificacaoRegistrar", null,
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

        // validar data de nascimento rejeita datas inexistentes antes de guardar os dados na sessao
        try {
            LocalDate.parse(professor.getDataNasc());
        } catch (DateTimeParseException e) {
            model.addAttribute("mensagemDeErro", "Informe uma data de nascimento válida.");
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

        // confirmar email do professor envia codigo quando habilitado ou segue para o cadastro verificado
        if (twoFactorEnabled) {
            String codigo = twoFactorService.gerarCodigo(professor.getEmail().toLowerCase());
            emailService.enviarCodigo(professor.getEmail().toLowerCase(), codigo, FinalidadeCodigoEmail.CADASTRO);

            return "redirect:/verificarProfessor";
        } else {
            session.setAttribute("verificado", "true");
            return "redirect:/cadastrarProfessorVerificado";
        }
    }

    // cadastrar professor verificado este metodo confere dados repetidos e grava o cadastro confirmado
    @GetMapping("/cadastrarProfessorVerificado")
    public String registrar(@Valid @ModelAttribute("professor") cadastrarProfessorDTO professor, BindingResult result, org.springframework.ui.Model model, HttpSession session) {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        // exigir professor verificado retorna ao cadastro quando o email ainda nao foi confirmado
        if (!"true".equals(session.getAttribute("verificado"))) {
            return "redirect:/cadastrarProfessor";
        }

        professor.setMatricula(session.getAttribute("Matricula").toString());
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


        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        List<String> erros = UsuarioRepository.validarDadosDuplicados(
                "Professor",
                professor.getMatricula(),
                professor.getEmail().toLowerCase(),
                professor.getTelefone()
        );


        // impedir cadastro duplicado do professor mostra os conflitos antes de salvar a conta
        if (!erros.isEmpty()) {
            logger.warn("Cadastro recusado por dados duplicados. perfil=professor motivos={}", erros);
            // registro de avisos confere se esta habilitado ao cadastrar professor verificado
            if (logger.isWarnEnabled()) {
                // gravar historico ao cadastrar professor verificado tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", cadastrarProfessorController.class.getName(), "registrar", null,
                            MessageFormatter.arrayFormat("Cadastro recusado por dados duplicados. perfil=professor motivos={}", new Object[]{erros}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            SQLErrorHandler.VerificarErro("professor", erros, model);
            model.addAttribute("professor", professor);
            return "Usuarios/Professor/cadastrarProfessor";
        }

        // salvar novo professor grava o cadastro e retorna ao formulario se o banco falhar
        try {

            UsuarioRepository.InsertCadastroUsuarioIntoBD(
                    "Professor",
                    professor.getMatricula(),
                    professor.getNome().toLowerCase(),
                    professor.getEmail().toLowerCase(),
                    professor.getTelefone(),
                    professor.getDataNasc(),
                    professor.getSenha()
            );

            logger.info("Sucesso ao cadastrar novo professor com email: {}", professor.getEmail());
            // registro de informacoes confere se esta habilitado ao cadastrar professor verificado
            if (logger.isInfoEnabled()) {
                // gravar historico ao cadastrar professor verificado tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", cadastrarProfessorController.class.getName(), "registrar", null,
                            MessageFormatter.arrayFormat("Sucesso ao cadastrar novo professor com email: {}", new Object[]{professor.getEmail()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Usuarios/Professor/loginProfessor";

        } catch (SQLException e) {
            logger.error("Erro ao inserir professor no banco com email: {}", professor.getEmail(), e);
            // registro de erros confere se esta habilitado ao cadastrar professor verificado
            if (logger.isErrorEnabled()) {
                // gravar historico ao cadastrar professor verificado tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", cadastrarProfessorController.class.getName(), "registrar", null,
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
