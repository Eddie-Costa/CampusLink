// confirmacao de email organiza os codigos usados em acesso cadastro e recuperacao

package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;

// controle de verificacao esta classe confere codigos e encaminha cada fluxo apos a confirmacao
@Controller
public class VerificarController {

    private static final Logger logger = LoggerFactory.getLogger(VerificarController.class);

    private static final String URL_LOGIN_ADMIN = "/admin/login";

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    @Autowired
    private UsuarioRepository UsuarioRepository;

    @Autowired
    private AlunoRepository alunoRepository;

    @Autowired
    private ProfessorRepository professorRepository;

    // calcular tempos do codigo este metodo prepara a validade e a espera para reenvio na tela
    @ModelAttribute
    public void carregarTempos(HttpSession session, Model model) {
        String email = (String) session.getAttribute("email2FA");
        long validade = twoFactorService.segundosParaExpirarCodigo(email);
        long reenvio = twoFactorService.segundosParaReenvio(email);
        model.addAttribute("segundosValidade", validade);
        model.addAttribute("segundosReenvio", reenvio);
        model.addAttribute("tempoValidade", String.format("%02d:%02d", validade / 60, validade % 60));
        model.addAttribute("tempoReenvio", String.format("%02d:%02d", reenvio / 60, reenvio % 60));
    }

    // reenviar codigo de acesso este metodo respeita o intervalo e envia um novo codigo para a finalidade atual
    @PostMapping("/verificar/reenviar")
    public String reenviarCodigo(HttpSession session, RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute("email2FA");
        Object fluxo = session.getAttribute("redirect");
        // validar reenvio de codigo exige email preenchido e uma finalidade de verificacao conhecida
        if (email == null || email.isBlank()
                || !("Login".equals(fluxo) || "Cadastro".equals(fluxo) || "ResetPassword".equals(fluxo))) {
            return "redirect:/login";
        }

        String codigo;
        // respeitar espera de reenvio tenta gerar outro codigo e informa quando o prazo ainda nao terminou
        try {
            codigo = twoFactorService.reenviarCodigo(email);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("erro", "Aguarde o contador de reenvio chegar a 00:00 para solicitar outro código.");
            return "redirect:/verificar";
        }

        // reenviar codigo por email escolhe a finalidade da mensagem e invalida o codigo se o envio falhar
        try {
            FinalidadeCodigoEmail finalidade = switch (fluxo.toString()) {
                case "Cadastro" -> FinalidadeCodigoEmail.CADASTRO;
                case "ResetPassword" -> FinalidadeCodigoEmail.REDEFINICAO_SENHA;
                default -> FinalidadeCodigoEmail.ACESSO;
            };
            emailService.enviarCodigo(email, codigo, finalidade);
            redirectAttributes.addFlashAttribute("mensagem", "Novo código enviado para seu e-mail. Use o código mais recente.");
        } catch (MailException e) {
            twoFactorService.invalidarCodigo(email);
            redirectAttributes.addFlashAttribute("erro", "Não foi possível enviar o e-mail. Aguarde 2 minutos e tente novamente.");
        }
        return "redirect:/verificar";
    }

    // abrir verificacao geral este metodo exige um email na sessao antes de mostrar o campo de codigo
    @GetMapping("/verificar")
    public String paginaVerificacao(HttpSession session) {


        // proteger pagina de verificacao retorna ao login quando falta o email que recebera o codigo
        if (session.getAttribute("email2FA") == null) {
            logger.warn("[LOGIN] email 2FA ausente - redirecionando para /login");

            // registro de avisos confere se esta habilitado ao abrir verificacao geral
            if (logger.isWarnEnabled()) {
                // gravar historico ao abrir verificacao geral tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "paginaVerificacao", null,
                            "[LOGIN] email 2FA ausente - redirecionando para /login", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return "redirect:/login";
        }

        return "Geral/verificar";
    }

    // abrir verificacao do aluno este metodo retorna ao login do aluno quando falta o email
    @GetMapping("/verificarAluno")
    public String paginaVerificacaoAluno(HttpSession session) {


        // proteger verificacao do aluno retorna ao login do aluno quando falta o email
        if (session.getAttribute("email2FA") == null) {
            logger.warn("[LOGIN] email 2FA ausente - redirecionando para /loginAluno");

            // registro de avisos confere se esta habilitado ao abrir verificacao do aluno
            if (logger.isWarnEnabled()) {
                // gravar historico ao abrir verificacao do aluno tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "paginaVerificacaoAluno", null,
                            "[LOGIN] email 2FA ausente - redirecionando para /loginAluno", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return "redirect:/loginAluno";
        }

        return "Geral/verificar";
    }

    // abrir verificacao do professor este metodo retorna ao login do professor quando falta o email
    @GetMapping("/verificarProfessor")
    public String paginaVerificacaoProfessor(HttpSession session) {


        // proteger verificacao do professor retorna ao login do professor quando falta o email
        if (session.getAttribute("email2FA") == null) {
            logger.warn("[LOGIN] email 2FA ausente - redirecionando para /loginProfessor");

            // registro de avisos confere se esta habilitado ao abrir verificacao do professor
            if (logger.isWarnEnabled()) {
                // gravar historico ao abrir verificacao do professor tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "paginaVerificacaoProfessor", null,
                            "[LOGIN] email 2FA ausente - redirecionando para /loginProfessor", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return "redirect:/loginProfessor";
        }

        return "Geral/verificar";
    }

    // abrir verificacao do administrador este metodo retorna ao login administrativo quando falta o email
    @GetMapping("/verificarAdmin")
    public String paginaVerificacaoAdmin(HttpSession session) {


        // proteger verificacao administrativa retorna ao login administrativo quando falta o email
        if (session.getAttribute("email2FA") == null) {
            logger.warn("[LOGIN] email 2FA ausente para administrador");

            // registro de avisos confere se esta habilitado ao abrir verificacao do administrador
            if (logger.isWarnEnabled()) {
                // gravar historico ao abrir verificacao do administrador tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "paginaVerificacaoAdmin", null,
                            "[LOGIN] email 2FA ausente para administrador", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return "redirect:" + URL_LOGIN_ADMIN;
        }

        return "Geral/verificar";
    }

    // verificar codigo este metodo confirma o codigo e continua o login cadastro ou recuperacao de senha
    @PostMapping("/verificar")
    public String verificarCodigo(@RequestParam String codigo, HttpSession session, Model model) throws SQLException {

        // exigir finalidade do codigo retorna ao login quando falta email ou etapa de verificacao
        if (session.getAttribute("email2FA") == null || session.getAttribute("redirect") == null) {
            return "redirect:/login";
        }

        // verificar codigo de acesso executa as etapas de autenticacao quando a finalidade e login
        if (session.getAttribute("redirect").equals("Login")) {

            String email = (String) session.getAttribute("email2FA");


            // tratar acesso sem email escolhe a tela de login conforme o perfil informado
            if (email == null) {

                // retornar ao acesso do aluno encaminha o aluno quando falta email para verificar
                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginAluno");

                    // registro de avisos confere se esta habilitado ao verificar codigo
                    if (logger.isWarnEnabled()) {
                        // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                        try {
                            UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginAluno", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginAluno";

                }
                // retornar ao acesso do professor encaminha o professor quando falta email para verificar
                else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginProfessor");

                    // registro de avisos confere se esta habilitado ao verificar codigo
                    if (logger.isWarnEnabled()) {
                        // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                        try {
                            UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginProfessor", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginProfessor";

                }
                // retornar ao acesso administrativo encaminha o administrador quando falta email para verificar
                else if (session.getAttribute("tipoUsuario").equals("admin")) {
                    logger.warn("[LOGIN] email 2FA invalido para administrador");

                    // registro de avisos confere se esta habilitado ao verificar codigo
                    if (logger.isWarnEnabled()) {
                        // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                        try {
                            UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido para administrador", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:" + URL_LOGIN_ADMIN;
                }
            }

            // aceitar codigo de acesso cria a sessao autenticada quando o codigo e valido
            if (twoFactorService.validarCodigo(email, codigo)) {

                session.removeAttribute("redirect");
                // registrar contexto do aluno associa email e sessao ao registro do acesso confirmado
                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    MDC.put("aluno", email);
                    MDC.put("sessionId", session.getId());

                }
                // registrar contexto do professor associa email e sessao ao registro do acesso confirmado
                else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    MDC.put("professor", email);
                    MDC.put("sessionId", session.getId());

                }
                // registrar contexto administrativo associa email e sessao ao registro do acesso confirmado
                else if (session.getAttribute("tipoUsuario").equals("admin")) {
                    MDC.put("admin", email);
                    MDC.put("sessionId", session.getId());
                }


                // carregar aluno autenticado busca a conta e guarda seus dados na sessao
                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    loginAlunoDTO aluno = alunoRepository.buscarPorEmailAluno(email);
                    session.setAttribute("usuarioLogado", aluno);

                }
                // carregar professor autenticado busca a conta e guarda seus dados na sessao
                else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    loginProfessorDTO professor = professorRepository.buscarPorEmailProfessor(email);
                    session.setAttribute("usuarioLogado", professor);

                }
                // carregar administrador autenticado busca a conta e guarda seus dados na sessao
                else if (session.getAttribute("tipoUsuario").equals("admin")) {
                    loginAdminDTO admin = UsuarioRepository.buscarPorEmailAdmin(email);
                    session.setAttribute("usuarioLogado", admin);
                }

                session.setMaxInactiveInterval(900);

                // confirmar login registrado informa o sucesso apenas quando a conta foi carregada
                if (session.getAttribute("usuarioLogado") != null) {
                    logger.info("Login concluído. perfil={} email={} doisFatores=true", session.getAttribute("tipoUsuario"), email);

                    // registro de informacoes confere se esta habilitado ao verificar codigo
                    if (logger.isInfoEnabled()) {
                        // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                        try {
                            UsuarioRepository.InserirLogsNoBD(null, "INFO", VerificarController.class.getName(), "verificarCodigo", null,
                                    MessageFormatter.arrayFormat(
                                            "Login concluído. perfil={} email={} doisFatores=true",
                                            new Object[]{session.getAttribute("tipoUsuario"), email}
                                    ).getMessage(), null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }
                }

                // encaminhar acesso administrativo abre o painel depois da verificacao do administrador
                if (session.getAttribute("tipoUsuario").equals("admin")) {
                    return "redirect:/admin/painel";
                }

                return "redirect:/home";
            }

            logger.warn("[LOGIN] codigo 2FA invalido no fluxo de login");

            // registro de avisos confere se esta habilitado ao verificar codigo
            if (logger.isWarnEnabled()) {
                // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                            "[LOGIN] codigo 2FA invalido no fluxo de login", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        }
        // verificar recuperacao de senha identifica a conta quando o codigo serve para redefinir a senha
        else if (session.getAttribute("redirect").equals("ResetPassword")) {

            String email = (String) session.getAttribute("email2FA");

            session.setAttribute("tipoUsuario", UsuarioRepository.buscarPorTipoUsuario(email));


            // tratar recuperacao sem email escolhe o login conforme o perfil encontrado
            if (email == null) {

                // retornar aluno na recuperacao encaminha o aluno ao login quando falta email
                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginAluno");

                    // registro de avisos confere se esta habilitado ao verificar codigo
                    if (logger.isWarnEnabled()) {
                        // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                        try {
                            UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginAluno", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginAluno";

                }
                // retornar professor na recuperacao encaminha o professor ao login quando falta email
                else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginProfessor");

                    // registro de avisos confere se esta habilitado ao verificar codigo
                    if (logger.isWarnEnabled()) {
                        // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                        try {
                            UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginProfessor", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginProfessor";
                }
            }

            // aceitar codigo de recuperacao abre a redefinicao quando o codigo confere ou mostra o erro
            if (twoFactorService.validarCodigo(email, codigo)) {
                session.removeAttribute("redirect");
                logger.info("O usuário com email: {} passou na validação de token para Reset de senha", email);

                // registro de informacoes confere se esta habilitado ao verificar codigo
                if (logger.isInfoEnabled()) {
                    // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", VerificarController.class.getName(), "verificarCodigo", null,
                                MessageFormatter.arrayFormat(
                                        "O usuário com email: {} passou na validação de token para Reset de senha",
                                        new Object[]{email}
                                ).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }


                // consultar aluno na recuperacao busca os dados quando a conta pertence a um aluno
                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    loginAlunoDTO aluno = alunoRepository.buscarPorEmailAluno(email);

                }
                // consultar professor na recuperacao busca os dados quando a conta pertence a um professor
                else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    loginProfessorDTO professor = professorRepository.buscarPorEmailProfessor(email);
                }

                return "redirect:/ResetPassword";

            } else {
                model.addAttribute("erro", "Código inválido ou expirado");

                logger.warn("Codigo 2FA inválido inserido para o email: {}", email);

                // registro de avisos confere se esta habilitado ao verificar codigo
                if (logger.isWarnEnabled()) {
                    // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                MessageFormatter.arrayFormat(
                                        "Codigo 2FA inválido inserido para o email: {}",
                                        new Object[]{email}
                                ).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                return "Geral/verificar";
            }

        }
        // verificar codigo de cadastro segue a confirmacao de email para criar uma conta
        else if (session.getAttribute("redirect").equals("Cadastro")) {

            String email = (String) session.getAttribute("email2FA");


            // tratar cadastro sem email encaminha o usuario ao acesso do perfil informado
            if (email == null) {

                // retornar aluno no cadastro encaminha o aluno ao login quando falta email
                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginAluno");

                    // registro de avisos confere se esta habilitado ao verificar codigo
                    if (logger.isWarnEnabled()) {
                        // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                        try {
                            UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginAluno", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginAluno";

                }
                // retornar professor no cadastro encaminha o professor ao login quando falta email
                else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginProfessor");

                    // registro de avisos confere se esta habilitado ao verificar codigo
                    if (logger.isWarnEnabled()) {
                        // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
                        try {
                            UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginProfessor", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginProfessor";
                }
            }

            // confirmar email de cadastro encerra a etapa do codigo quando a verificacao e valida
            if (twoFactorService.validarCodigo(email, codigo)) {

                session.removeAttribute("redirect");
                // concluir verificacao do aluno marca o email confirmado e segue para salvar o cadastro
                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    session.setAttribute("verificado", "true");
                    return "redirect:/cadastrarAlunoVerificado";

                }
                // concluir verificacao do professor marca o email confirmado e segue para salvar o cadastro
                else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    session.setAttribute("verificado", "true");
                    return "redirect:/cadastrarProfessorVerificado";
                }
            }
        }

        model.addAttribute("erro", "Código inválido ou expirado");

        logger.warn("Codigo 2FA inválido inserido para usuario com email: {}", session.getAttribute("email2FA"));

        // registro de avisos confere se esta habilitado ao verificar codigo
        if (logger.isWarnEnabled()) {
            // gravar historico ao verificar codigo tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                        MessageFormatter.arrayFormat(
                                "Codigo 2FA inválido inserido para usuario com email: {}",
                                new Object[]{session.getAttribute("email2FA")}
                        ).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        return "Geral/verificar";
    }
}
