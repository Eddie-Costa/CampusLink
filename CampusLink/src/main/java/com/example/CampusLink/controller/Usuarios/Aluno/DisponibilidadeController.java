package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.dao.alunoDAO;
import com.example.CampusLink.service.DisponibilidadeService;
import com.example.CampusLink.service.EventoService;
import com.example.CampusLink.dao.usuarioDAO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.time.LocalDate;

@Controller
@RequestMapping("/disponibilidade")
@RequiredArgsConstructor
public class DisponibilidadeController {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(DisponibilidadeController.class);

    // dependencias usadas pelo controller
    private final alunoDAO alunoDAO;
    private final EventoService eventoService;
    private final DisponibilidadeService disponibilidadeService;

    // mostra o calendario do aluno
    @GetMapping
    public String exibirCalendario(Model model, HttpSession session) throws SQLException {

        // verifica se o usuario pode acessar a pagina
        if (!usuarioPodeAcessar(session)) {
            logger.warn("Acesso recusado à disponibilidade: usuário não é aluno.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DisponibilidadeController.class.getName(), "exibirCalendario", null,
                            "Acesso recusado à disponibilidade: usuário não é aluno.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        // busca o id do aluno logado
        Long idAluno = buscarIdAluno(session);

        if (idAluno == null) {
            logger.warn("Acesso à disponibilidade recusado: aluno não identificado.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DisponibilidadeController.class.getName(), "exibirCalendario", null,
                            "Acesso à disponibilidade recusado: aluno não identificado.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        carregarDadosDoAluno(model, idAluno);

        logger.debug("Calendário de disponibilidade exibido. alunoId={}", idAluno);
        if (logger.isDebugEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "DEBUG", DisponibilidadeController.class.getName(), "exibirCalendario", null,
                        MessageFormatter.arrayFormat("Calendário de disponibilidade exibido. alunoId={}", new Object[]{idAluno}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        return "Usuarios/Aluno/calendario-disponibilidade";
    }

    // mostra o formulario de disponibilidade
    @GetMapping("/cadastrar")
    public String exibirFormulario(Model model, HttpSession session) throws SQLException {

        if (!usuarioPodeAcessar(session)) {
            logger.warn("Acesso recusado ao cadastro de disponibilidade: usuário não é aluno.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DisponibilidadeController.class.getName(), "exibirFormulario", null,
                            "Acesso recusado ao cadastro de disponibilidade: usuário não é aluno.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        Long idAluno = buscarIdAluno(session);

        if (idAluno == null) {
            logger.warn("Cadastro de disponibilidade recusado: aluno não identificado.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DisponibilidadeController.class.getName(), "exibirFormulario", null,
                            "Cadastro de disponibilidade recusado: aluno não identificado.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        carregarDadosDoAluno(model, idAluno);

        logger.debug("Formulário de disponibilidade exibido. alunoId={}", idAluno);
        if (logger.isDebugEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "DEBUG", DisponibilidadeController.class.getName(), "exibirFormulario", null,
                        MessageFormatter.arrayFormat("Formulário de disponibilidade exibido. alunoId={}", new Object[]{idAluno}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        return "Usuarios/Aluno/cadastrar-disponibilidade";
    }

    // cadastra a disponibilidade do aluno
    @PostMapping("/cadastrar")
    public String cadastrarDisponibilidade(

            @RequestParam("data")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate data,

            @RequestParam("horasDisponiveis")
            Integer horasDisponiveis,

            HttpSession session,
            RedirectAttributes redirectAttributes
    ) throws SQLException {

        if (!usuarioPodeAcessar(session)) {
            logger.warn("Cadastro de disponibilidade recusado: usuário não é aluno.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DisponibilidadeController.class.getName(), "cadastrarDisponibilidade", null,
                            "Cadastro de disponibilidade recusado: usuário não é aluno.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        Long idAluno = buscarIdAluno(session);

        if (idAluno == null) {

            logger.warn("Cadastro de disponibilidade recusado: aluno não identificado.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DisponibilidadeController.class.getName(), "cadastrarDisponibilidade", null,
                            "Cadastro de disponibilidade recusado: aluno não identificado.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return "redirect:/login";
        }

        // verifica se o numero de horas e valido
        if (horasDisponiveis == null
                || horasDisponiveis < 0
                || horasDisponiveis > 24) {

            logger.warn("Disponibilidade inválida. alunoId={} data={} horas={}", idAluno, data, horasDisponiveis);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DisponibilidadeController.class.getName(), "cadastrarDisponibilidade", null,
                            MessageFormatter.arrayFormat("Disponibilidade inválida. alunoId={} data={} horas={}", new Object[]{idAluno, data, horasDisponiveis}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("erro", "Informe uma quantidade entre 0 e 24 horas.");
            return "redirect:/disponibilidade/cadastrar";
        }

        disponibilidadeService.salvarDisponibilidade(idAluno, data, horasDisponiveis);

        redirectAttributes.addFlashAttribute("mensagem", "Disponibilidade cadastrada com sucesso!");
        return "redirect:/disponibilidade";
    }

    // verifica se o usuario esta logado e e aluno
    private boolean usuarioPodeAcessar(HttpSession session) {
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object tipoUsuario = session.getAttribute("tipoUsuario");

        return usuarioLogado != null
                && tipoUsuario != null
                && "aluno".equalsIgnoreCase(
                tipoUsuario.toString()
        );
    }

    // busca o id do aluno pelo email da sessao
    private Long buscarIdAluno(HttpSession session
    ) throws SQLException {

        Object email = session.getAttribute("email2FA");

        if (email == null) {
            logger.warn("Não foi possível identificar o aluno: email2FA ausente.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DisponibilidadeController.class.getName(), "buscarIdAluno", null,
                            "Não foi possível identificar o aluno: email2FA ausente.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return null;
        }

        String idAluno = alunoDAO.buscarPorIDAluno(email.toString());

        if (idAluno == null || idAluno.isBlank()) {
            logger.warn("Nenhum ID de aluno foi encontrado.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DisponibilidadeController.class.getName(), "buscarIdAluno", null,
                            "Nenhum ID de aluno foi encontrado.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return null;
        }

        logger.debug("Aluno identificado. alunoId={}", idAluno);
        if (logger.isDebugEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "DEBUG", DisponibilidadeController.class.getName(), "buscarIdAluno", null,
                        MessageFormatter.arrayFormat("Aluno identificado. alunoId={}", new Object[]{idAluno}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        return Long.valueOf(idAluno);
    }

    // carrega os eventos e disponibilidades do aluno
    private void carregarDadosDoAluno(Model model, Long idAluno) {
        model.addAttribute("eventos", eventoService.listarEventos());
        model.addAttribute("disponibilidades", disponibilidadeService.listarPorAluno(idAluno));
        logger.debug("Dados do calendário do aluno carregados. alunoId={}", idAluno);
        if (logger.isDebugEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "DEBUG", DisponibilidadeController.class.getName(), "carregarDadosDoAluno", null,
                        MessageFormatter.arrayFormat("Dados do calendário do aluno carregados. alunoId={}", new Object[]{idAluno}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }
}