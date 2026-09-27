package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.alunoDAO;
import com.example.CampusLink.dao.professorDAO;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.SQLException;

@Controller
public class VerificarController {

    private static final Logger logger = LoggerFactory.getLogger(VerificarController.class);

    private static final String URL_LOGIN_ADMIN = "/gestao/8f3c1d7a-2b94-4e61-a5c8-7d2f9b4a6e31";

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    @Autowired
    private usuarioDAO usuarioDAO;

    @Autowired
    private alunoDAO alunoDAO;

    @Autowired
    private professorDAO professorDAO;

    @GetMapping("/verificar")
    public String paginaVerificacao(HttpSession session) {

        // verifica se o usuario passou pela pagina de login primeiro
        if (session.getAttribute("email2FA") == null) {
            logger.warn("[LOGIN] email 2FA ausente - redirecionando para /login");

            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "paginaVerificacao", null,
                            "[LOGIN] email 2FA ausente - redirecionando para /login", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return "redirect:/login";
        }

        return "Geral/verificar";
    }

    @GetMapping("/verificarAluno")
    public String paginaVerificacaoAluno(HttpSession session) {

        // verifica se o aluno passou pela pagina de login primeiro
        if (session.getAttribute("email2FA") == null) {
            logger.warn("[LOGIN] email 2FA ausente - redirecionando para /loginAluno");

            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "paginaVerificacaoAluno", null,
                            "[LOGIN] email 2FA ausente - redirecionando para /loginAluno", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return "redirect:/loginAluno";
        }

        return "Geral/verificar";
    }

    @GetMapping("/verificarProfessor")
    public String paginaVerificacaoProfessor(HttpSession session) {

        // verifica se o professor passou pela pagina de login primeiro
        if (session.getAttribute("email2FA") == null) {
            logger.warn("[LOGIN] email 2FA ausente - redirecionando para /loginProfessor");

            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "paginaVerificacaoProfessor", null,
                            "[LOGIN] email 2FA ausente - redirecionando para /loginProfessor", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return "redirect:/loginProfessor";
        }

        return "Geral/verificar";
    }

    @GetMapping("/verificarAdmin")
    public String paginaVerificacaoAdmin(HttpSession session) {

        // verifica se o admin passou pela pagina de login primeiro
        if (session.getAttribute("email2FA") == null) {
            logger.warn("[LOGIN] email 2FA ausente para administrador");

            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "paginaVerificacaoAdmin", null,
                            "[LOGIN] email 2FA ausente para administrador", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return "redirect:" + URL_LOGIN_ADMIN;
        }

        return "Geral/verificar";
    }

    @PostMapping("/verificar")
    public String verificarCodigo(@RequestParam String codigo, HttpSession session, Model model) throws SQLException {

        if (session.getAttribute("redirect").equals("Login")) {

            String email = (String) session.getAttribute("email2FA");

            // verifica se o email da sessao existe
            if (email == null) {

                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginAluno");

                    if (logger.isWarnEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginAluno", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginAluno";

                } else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginProfessor");

                    if (logger.isWarnEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginProfessor", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginProfessor";

                } else if (session.getAttribute("tipoUsuario").equals("admin")) {
                    logger.warn("[LOGIN] email 2FA invalido para administrador");

                    if (logger.isWarnEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido para administrador", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:" + URL_LOGIN_ADMIN;
                }
            }

            if (twoFactorService.validarCodigo(email, codigo)) {

                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    MDC.put("aluno", email);
                    MDC.put("sessionId", session.getId());

                } else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    MDC.put("professor", email);
                    MDC.put("sessionId", session.getId());

                } else if (session.getAttribute("tipoUsuario").equals("admin")) {
                    MDC.put("admin", email);
                    MDC.put("sessionId", session.getId());
                }

                // busca o usuario e cria a sessao
                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    loginAlunoDTO aluno = alunoDAO.buscarPorEmailAluno(email);
                    session.setAttribute("usuarioLogado", aluno);

                } else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    loginProfessorDTO professor = professorDAO.buscarPorEmailProfessor(email);
                    session.setAttribute("usuarioLogado", professor);

                } else if (session.getAttribute("tipoUsuario").equals("admin")) {
                    loginAdminDTO admin = usuarioDAO.buscarPorEmailAdmin(email);
                    session.setAttribute("usuarioLogado", admin);
                }

                session.setMaxInactiveInterval(900);

                if (session.getAttribute("usuarioLogado") != null) {
                    logger.info("Login concluído. perfil={} email={} doisFatores=true", session.getAttribute("tipoUsuario"), email);

                    if (logger.isInfoEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "INFO", VerificarController.class.getName(), "verificarCodigo", null,
                                    MessageFormatter.arrayFormat(
                                            "Login concluído. perfil={} email={} doisFatores=true",
                                            new Object[]{session.getAttribute("tipoUsuario"), email}
                                    ).getMessage(), null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }
                }

                if (session.getAttribute("tipoUsuario").equals("admin")) {
                    return "redirect:/admin/painel";
                }

                return "redirect:/home";
            }

            logger.warn("[LOGIN] codigo 2FA invalido no fluxo de login");

            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                            "[LOGIN] codigo 2FA invalido no fluxo de login", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } else if (session.getAttribute("redirect").equals("ResetPassword")) {

            String email = (String) session.getAttribute("email2FA");

            session.setAttribute("tipoUsuario", usuarioDAO.buscarPorTipoUsuario(email));

            // verifica se o email da sessao existe
            if (email == null) {

                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginAluno");

                    if (logger.isWarnEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginAluno", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginAluno";

                } else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginProfessor");

                    if (logger.isWarnEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginProfessor", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginProfessor";
                }
            }

            if (twoFactorService.validarCodigo(email, codigo)) {
                logger.info("O usuário com email: {} passou na validação de token para Reset de senha", email);

                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", VerificarController.class.getName(), "verificarCodigo", null,
                                MessageFormatter.arrayFormat(
                                        "O usuário com email: {} passou na validação de token para Reset de senha",
                                        new Object[]{email}
                                ).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                // busca o usuario para validar o tipo
                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    loginAlunoDTO aluno = alunoDAO.buscarPorEmailAluno(email);

                } else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    loginProfessorDTO professor = professorDAO.buscarPorEmailProfessor(email);
                }

                return "redirect:/ResetPassword";

            } else {
                model.addAttribute("erro", "Código inválido ou expirado");

                logger.warn("Codigo 2FA inválido inserido para o email: {}", email);

                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
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

        } else if (session.getAttribute("redirect").equals("Cadastro")) {

            String email = (String) session.getAttribute("email2FA");

            // verifica se o email da sessao existe
            if (email == null) {

                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginAluno");

                    if (logger.isWarnEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginAluno", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginAluno";

                } else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    logger.warn("[LOGIN] email 2FA invalido - redirecionando para /loginProfessor");

                    if (logger.isWarnEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
                                    "[LOGIN] email 2FA invalido - redirecionando para /loginProfessor", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }

                    return "redirect:/loginProfessor";
                }
            }

            if (twoFactorService.validarCodigo(email, codigo)) {

                if (session.getAttribute("tipoUsuario").equals("aluno")) {
                    session.setAttribute("verificado", "true");
                    return "redirect:/cadastrarAlunoVerificado";

                } else if (session.getAttribute("tipoUsuario").equals("professor")) {
                    session.setAttribute("verificado", "true");
                    return "redirect:/cadastrarProfessorVerificado";
                }
            }
        }

        model.addAttribute("erro", "Código inválido ou expirado");

        logger.warn("Codigo 2FA inválido inserido para usuario com email: {}", session.getAttribute("email2FA"));

        if (logger.isWarnEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "WARN", VerificarController.class.getName(), "verificarCodigo", null,
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