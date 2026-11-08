// inscricao de alunos organiza o cadastro publico desse perfil
package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.Aluno.cadastrarAlunoDTO;
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

// cadastro de aluno esta classe guarda os dados ate a verificacao e confere duplicidades antes de gravar
@Controller
public class cadastrarAlunoController {

    @Value("${campuslink.twofa.enabled:true}")
    private boolean twoFactorEnabled;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    private final UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(cadastrarAlunoController.class);

    // preparar cadastro de aluno este construtor recebe as operacoes de cadastro de usuarios
    public cadastrarAlunoController(UsuarioRepository UsuarioRepository) {
        this.UsuarioRepository = UsuarioRepository;
    }

    // abrir cadastro de aluno este metodo prepara o formulario para quem ainda nao entrou
    @GetMapping("/cadastrarAluno")
    public String CadastrarAluno(org.springframework.ui.Model model, HttpSession session) {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        model.addAttribute("aluno", new cadastrarAlunoDTO());
        return "Usuarios/Aluno/cadastrarAluno";
    }

    // verificar cadastro de aluno este metodo guarda os dados na sessao e inicia a confirmacao por email
    @PostMapping("/cadastrarAluno")
    public String VerificacaoRegistrar(@Valid @ModelAttribute("aluno") cadastrarAlunoDTO aluno, BindingResult result, org.springframework.ui.Model model, HttpSession session) {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }


        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        // validar cadastro de aluno mostra os erros e reapresenta os campos invalidos
        if (result.hasErrors()) {
            logger.warn("Dados inválidos no cadastro do aluno. erros={}", result.getFieldErrors().stream()
                    .map(erro -> "%s: %s".formatted(erro.getField(), erro.getDefaultMessage()))
                    .toList());
            // registro de avisos confere se esta habilitado ao verificar cadastro de aluno
            if (logger.isWarnEnabled()) {
                // gravar historico ao verificar cadastro de aluno tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", cadastrarAlunoController.class.getName(), "VerificacaoRegistrar", null,
                            MessageFormatter.arrayFormat("Dados inválidos no cadastro do aluno. erros={}", new Object[]{result.getFieldErrors().stream()
                    .map(erro -> "%s: %s".formatted(erro.getField(), erro.getDefaultMessage()))
                    .toList()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            model.addAttribute("mensagemDeErro", "Dados inválidos. Verifique o formulário.");
            return "Usuarios/Aluno/cadastrarAluno";
        }

        // validar data de nascimento rejeita datas inexistentes antes de guardar os dados na sessao
        try {
            LocalDate.parse(aluno.getDataNasc());
        } catch (DateTimeParseException e) {
            model.addAttribute("mensagemDeErro", "Informe uma data de nascimento válida.");
            return "Usuarios/Aluno/cadastrarAluno";
        }

        session.removeAttribute("verificado");

        session.setAttribute("email2FA", aluno.getEmail().toLowerCase());
        session.setAttribute("redirect", "Cadastro");
        session.setAttribute("tipoUsuario", "aluno");
        session.setAttribute("Rgm", aluno.getRgm());
        session.setAttribute("Nome", aluno.getNome().toLowerCase());
        session.setAttribute("Email", aluno.getEmail().toLowerCase());
        session.setAttribute("Telefone", aluno.getTelefone());
        session.setAttribute("DataNasc", aluno.getDataNasc());
        session.setAttribute("Senha", encoder.encode(aluno.getSenha()));

        // confirmar email do aluno envia o codigo quando habilitado ou segue diretamente ao cadastro verificado
        if (twoFactorEnabled) {
            String codigo = twoFactorService.gerarCodigo(aluno.getEmail().toLowerCase());
            emailService.enviarCodigo(aluno.getEmail().toLowerCase(), codigo, FinalidadeCodigoEmail.CADASTRO);

            return "redirect:/verificarAluno";
        } else {
            session.setAttribute("verificado", "true");
            return "redirect:/cadastrarAlunoVerificado";
        }
    }

    // cadastrar aluno verificado este metodo confere dados repetidos e grava o cadastro confirmado
    @GetMapping("/cadastrarAlunoVerificado")
    public String registrar(@ModelAttribute("aluno") cadastrarAlunoDTO aluno, BindingResult result, org.springframework.ui.Model model, HttpSession session) {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        // exigir aluno verificado retorna ao cadastro quando a confirmacao ainda nao aconteceu
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

        List<String> erros = UsuarioRepository.validarDadosDuplicados(
                "Aluno",
                aluno.getRgm(),
                aluno.getEmail().toLowerCase(),
                aluno.getTelefone()
        );


        // impedir cadastro duplicado do aluno mostra os conflitos encontrados antes de salvar a conta
        if (!erros.isEmpty()) {
            logger.warn("Cadastro recusado por dados duplicados. perfil=aluno motivos={}", erros);
            // registro de avisos confere se esta habilitado ao cadastrar aluno verificado
            if (logger.isWarnEnabled()) {
                // gravar historico ao cadastrar aluno verificado tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", cadastrarAlunoController.class.getName(), "registrar", null,
                            MessageFormatter.arrayFormat("Cadastro recusado por dados duplicados. perfil=aluno motivos={}", new Object[]{erros}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            SQLErrorHandler.VerificarErro("aluno", erros, model);
            model.addAttribute("aluno", aluno);
            return "Usuarios/Aluno/cadastrarAluno";
        }

        // salvar novo aluno grava o cadastro e retorna ao formulario se o banco falhar
        try {

            UsuarioRepository.InsertCadastroUsuarioIntoBD(
                    "Aluno",
                    aluno.getRgm(),
                    aluno.getNome().toLowerCase(),
                    aluno.getEmail().toLowerCase(),
                    aluno.getTelefone(),
                    aluno.getDataNasc(),
                    aluno.getSenha()
            );

            logger.info("Sucesso ao cadastrar novo aluno com email: {}", aluno.getEmail());
            // registro de informacoes confere se esta habilitado ao cadastrar aluno verificado
            if (logger.isInfoEnabled()) {
                // gravar historico ao cadastrar aluno verificado tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", cadastrarAlunoController.class.getName(), "registrar", null,
                            MessageFormatter.arrayFormat("Sucesso ao cadastrar novo aluno com email: {}", new Object[]{aluno.getEmail()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "Usuarios/Aluno/loginAluno";

        } catch (SQLException e) {
            logger.error("Erro ao inserir aluno no banco com email: {}", aluno.getEmail(), e);
            // registro de erros confere se esta habilitado ao cadastrar aluno verificado
            if (logger.isErrorEnabled()) {
                // gravar historico ao cadastrar aluno verificado tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", cadastrarAlunoController.class.getName(), "registrar", null,
                            MessageFormatter.arrayFormat("Erro ao inserir aluno no banco com email: {}", new Object[]{aluno.getEmail()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("aluno", aluno);
            return "Usuarios/Aluno/cadastrarAluno";
        }
    }

}
