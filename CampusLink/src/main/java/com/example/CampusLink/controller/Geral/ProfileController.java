// dados pessoais disponibiliza consulta exportacao e exclusao da propria conta
package com.example.CampusLink.controller.Geral;

import org.slf4j.helpers.MessageFormatter;
import java.io.StringWriter;
import java.io.PrintWriter;
import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.service.TermosService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.client.RestClientException;

import java.sql.SQLException;

// controle do perfil esta classe usa a identidade conectada e exige codigo para confirmar a exclusao
@Controller
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    @Autowired
    private UsuarioRepository UsuarioRepository;

    @Autowired
    private emailService emailService;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private SessaoLogListener sessaoLogListener;

    @Autowired
    private TermosService termosService;

    // consultar meu perfil este metodo carrega os dados pessoais e avisa quando ha turmas materiais ou eventos vinculados
    @GetMapping({"/profile", "/profile/dados"})
    public String profile(HttpSession session, Model model) throws SQLException {
        // encaminhar administrador usa o painel administrativo em vez da area de perfil pessoal
        if (ehAdministrador(session)) {
            return "redirect:/admin/painel";
        }
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        DadosUsuarioDTO dados = dadosDaSessao(session);
        // exigir dados da conta retorna ao login quando nao encontra o cadastro
        if (dados == null) {
            return "redirect:/login";
        }
        model.addAttribute("dados", dados);
        model.addAttribute("possuiVinculos", UsuarioRepository.possuiVinculosParaExclusao(dados.getId()));
        model.addAttribute("exclusaoPendente", Boolean.TRUE.equals(session.getAttribute("exclusaoPendente")));
        model.addAttribute("aceiteTermos", termosService.aceiteAtivo(dados.getId()));
        return "Geral/profile";
    }

    // enviar email com dados pessoais este metodo envia o cadastro ao email do usuario conectado
    @PostMapping("/profile/exportar")
    public String exportar(HttpSession session, RedirectAttributes redirectAttributes) throws SQLException {
        // encaminhar administrador usa o painel administrativo em vez da area de perfil pessoal
        if (ehAdministrador(session)) {
            return "redirect:/admin/painel";
        }
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        DadosUsuarioDTO dados = dadosDaSessao(session);
        // exigir dados da conta retorna ao login quando nao encontra o cadastro
        if (dados == null) {
            return "redirect:/login";
        }
        // exportar dados por email tenta enviar os dados cadastrais e informa falhas de envio
        try {
            emailService.enviarEmailDados(dados);
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Seus dados cadastrais foram enviados para o e-mail da sua conta.");
        } catch (MailException e) {
            logger.error("Falha no envio da exportação de dados cadastrais. usuarioId={}", dados.getId(), e);
            // registro de erros confere se esta habilitado ao enviar email com dados pessoais
            if (logger.isErrorEnabled()) {
                // gravar historico ao enviar email com dados pessoais tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ProfileController.class.getName(), "exportar", null,
                            MessageFormatter.arrayFormat("Falha no envio da exportação de dados cadastrais. usuarioId={}", new Object[]{dados.getId()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível enviar o e-mail. Tente novamente mais tarde.");
        }
        return "redirect:/profile";
    }

    // solicitar delete conta este metodo verifica os vinculos e envia um codigo exclusivo de confirmacao
    @PostMapping("/profile/excluir")
    public String solicitarExclusao(HttpSession session, RedirectAttributes redirectAttributes) throws SQLException {
        // encaminhar administrador usa o painel administrativo em vez da area de perfil pessoal
        if (ehAdministrador(session)) {
            return "redirect:/admin/painel";
        }
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        DadosUsuarioDTO dados = dadosDaSessao(session);
        // exigir dados da conta retorna ao login quando nao encontra o cadastro
        if (dados == null) {
            return "redirect:/login";
        }
        synchronized (session) {
            Long ultimoEnvio = (Long) session.getAttribute("ultimoEnvioExclusao");
            // limitar codigo de exclusao exige dois minutos entre os envios do codigo
            if (ultimoEnvio != null && System.currentTimeMillis() - ultimoEnvio < 120_000) {
                logger.warn("Reenvio de código de exclusão recusado: intervalo mínimo. usuarioId={}", dados.getId());
                // registro de avisos confere se esta habilitado ao solicitar delete conta
                if (logger.isWarnEnabled()) {
                    // gravar historico ao solicitar delete conta tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ProfileController.class.getName(), "solicitarExclusao", null,
                                MessageFormatter.arrayFormat("Reenvio de código de exclusão recusado: intervalo mínimo. usuarioId={}", new Object[]{dados.getId()}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", "Aguarde dois minutos entre os envios do código de exclusão.");
                return "redirect:/profile";
            }

            String chave = chaveExclusao(session, dados);
            String codigo = TwoFactorService.gerarCodigo(chave);
            session.setAttribute("ultimoEnvioExclusao", System.currentTimeMillis());
            // solicitar codigo de exclusao envia a mensagem e cancela a solicitacao pendente se o envio falhar
            try {
                emailService.enviarCodigo(dados.getEmail(), codigo, FinalidadeCodigoEmail.EXCLUSAO_CONTA);
                session.setAttribute("exclusaoPendente", true);
                session.setAttribute("tentativasExclusao", 0);
                logger.info("Exclusão de conta solicitada. usuarioId={}", dados.getId());
                // registro de informacoes confere se esta habilitado ao solicitar delete conta
                if (logger.isInfoEnabled()) {
                    // gravar historico ao solicitar delete conta tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", ProfileController.class.getName(), "solicitarExclusao", null,
                                MessageFormatter.arrayFormat("Exclusão de conta solicitada. usuarioId={}", new Object[]{dados.getId()}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Solicitamos o envio de um código ao seu e-mail. Ele vale por cinco minutos.");
            } catch (MailException e) {
                logger.error("Falha ao solicitar envio do código de exclusão. usuarioId={}", dados.getId(), e);
                // registro de erros confere se esta habilitado ao solicitar delete conta
                if (logger.isErrorEnabled()) {
                    // gravar historico ao solicitar delete conta tenta salvar o registro no banco e informa falhas
                    try {
                        StringWriter excecaoLogBD = new StringWriter();
                        e.printStackTrace(new PrintWriter(excecaoLogBD));
                        UsuarioRepository.InserirLogsNoBD(null, "ERROR", ProfileController.class.getName(), "solicitarExclusao", null,
                                MessageFormatter.arrayFormat("Falha ao solicitar envio do código de exclusão. usuarioId={}", new Object[]{dados.getId()}).getMessage(), null, excecaoLogBD.toString());
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                twoFactorService.limparCodigo(chave);
                session.removeAttribute("exclusaoPendente");
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível enviar o código. Tente novamente mais tarde.");
            }
        }
        return "redirect:/profile";
    }

    // confirmar delete conta este metodo valida o codigo remove os dados e encerra as sessoes
    @PostMapping("/profile/excluir/confirmar")
    public String confirmarExclusao(HttpSession session, @RequestParam(defaultValue = "") String codigo,
                                   @RequestParam(defaultValue = "false") boolean confirmar,
                                   RedirectAttributes redirectAttributes) throws SQLException {
        // encaminhar administrador usa o painel administrativo em vez da area de perfil pessoal
        if (ehAdministrador(session)) {
            return "redirect:/admin/painel";
        }
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        DadosUsuarioDTO dados = dadosDaSessao(session);
        // exigir dados da conta retorna ao login quando nao encontra o cadastro
        if (dados == null) {
            return "redirect:/login";
        }
        synchronized (session) {
            // exigir confirmacao da conta recusa a exclusao sem solicitacao pendente ou aceite expresso
            if (!Boolean.TRUE.equals(session.getAttribute("exclusaoPendente")) || !confirmar) {
                logger.warn("Exclusão recusada: solicitação ou confirmação ausente. usuarioId={}", dados.getId());
                // registro de avisos confere se esta habilitado ao confirmar delete conta
                if (logger.isWarnEnabled()) {
                    // gravar historico ao confirmar delete conta tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ProfileController.class.getName(), "confirmarExclusao", null,
                                MessageFormatter.arrayFormat("Exclusão recusada: solicitação ou confirmação ausente. usuarioId={}", new Object[]{dados.getId()}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", "Solicite um código e confirme que deseja excluir a conta.");
                return "redirect:/profile";
            }
            String chave = chaveExclusao(session, dados);
            int tentativas = (Integer) session.getAttribute("tentativasExclusao");
            // verificar codigo de exclusao conta uma tentativa quando o codigo tem formato invalido ou nao confere
            if (!codigo.matches("\\d{6}") || !twoFactorService.validarCodigo(chave, codigo)) {
                session.setAttribute("tentativasExclusao", ++tentativas);
                logger.warn("Código de exclusão inválido ou expirado. usuarioId={} tentativas={}", dados.getId(), tentativas);
                // registro de avisos confere se esta habilitado ao confirmar delete conta
                if (logger.isWarnEnabled()) {
                    // gravar historico ao confirmar delete conta tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ProfileController.class.getName(), "confirmarExclusao", null,
                                MessageFormatter.arrayFormat("Código de exclusão inválido ou expirado. usuarioId={} tentativas={}", new Object[]{dados.getId(), tentativas}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                // limitar tentativas de exclusao cancela o codigo apos cinco erros ou avisa para tentar novamente
                if (tentativas >= 5) {
                    logger.warn("Limite de tentativas de exclusão atingido. usuarioId={}", dados.getId());
                    // registro de avisos confere se esta habilitado ao confirmar delete conta
                    if (logger.isWarnEnabled()) {
                        // gravar historico ao confirmar delete conta tenta salvar o registro no banco e informa falhas
                        try {
                            UsuarioRepository.InserirLogsNoBD(null, "WARN", ProfileController.class.getName(), "confirmarExclusao", null,
                                    MessageFormatter.arrayFormat("Limite de tentativas de exclusão atingido. usuarioId={}", new Object[]{dados.getId()}).getMessage(), null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }
                    twoFactorService.limparCodigo(chave);
                    session.removeAttribute("exclusaoPendente");
                    redirectAttributes.addFlashAttribute("mensagemErro", "Limite de tentativas atingido. Solicite outro código.");
                } else {
                    redirectAttributes.addFlashAttribute("mensagemErro", "Código inválido ou expirado. Confira o e-mail ou solicite outro código.");
                }
                return "redirect:/profile";
            }
            session.removeAttribute("exclusaoPendente");
            session.removeAttribute("tentativasExclusao");
            // delete dados pessoais tenta excluir a conta e informa os vinculos que impedem a operacao
            try {
                UsuarioRepository.anonimizarDadosPessoais(dados.getId(), dados.getEmail());
                logger.info("Conta excluída. usuarioId={}", dados.getId());
                // registro de informacoes confere se esta habilitado ao confirmar delete conta
                if (logger.isInfoEnabled()) {
                    // gravar historico ao confirmar delete conta tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", ProfileController.class.getName(), "confirmarExclusao", null,
                                MessageFormatter.arrayFormat("Conta excluída. usuarioId={}", new Object[]{dados.getId()}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            } catch (IllegalStateException e) {
                logger.warn("Exclusão de conta recusada. usuarioId={} motivo={}", dados.getId(), e.getMessage());
                // registro de avisos confere se esta habilitado ao confirmar delete conta
                if (logger.isWarnEnabled()) {
                    // gravar historico ao confirmar delete conta tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ProfileController.class.getName(), "confirmarExclusao", null,
                                MessageFormatter.arrayFormat("Exclusão de conta recusada. usuarioId={} motivo={}", new Object[]{dados.getId(), e.getMessage()}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
                return "redirect:/profile";
            }
        }
        twoFactorService.limparCodigo(dados.getEmail());
        sessaoLogListener.encerrarSessoesDoUsuario(dados.getEmail());
        // encerrar conta excluida invalida a sessao e ignora o caso em que ela ja foi encerrada
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {

        }
        return "redirect:/login?contaExcluida";
    }

    // cancelar delete conta este metodo descarta o codigo e limpa a solicitacao de exclusao
    @PostMapping("/profile/excluir/cancelar")
    public String cancelarExclusao(HttpSession session) throws SQLException {
        // encaminhar administrador usa o painel administrativo em vez da area de perfil pessoal
        if (ehAdministrador(session)) {
            return "redirect:/admin/painel";
        }
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        DadosUsuarioDTO dados = dadosDaSessao(session);
        // exigir dados da conta retorna ao login quando nao encontra o cadastro
        if (dados == null) {
            return "redirect:/login";
        }
        twoFactorService.limparCodigo(chaveExclusao(session, dados));
        session.removeAttribute("exclusaoPendente");
        session.removeAttribute("tentativasExclusao");
        logger.info("Solicitação de exclusão de conta cancelada. usuarioId={}", dados.getId());
        // registro de informacoes confere se esta habilitado ao cancelar delete conta
        if (logger.isInfoEnabled()) {
            // gravar historico ao cancelar delete conta tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", ProfileController.class.getName(), "cancelarExclusao", null,
                        MessageFormatter.arrayFormat("Solicitação de exclusão de conta cancelada. usuarioId={}", new Object[]{dados.getId()}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        return "redirect:/profile";
    }

    // verificar administrador no perfil este metodo identifica o perfil administrativo da sessao
    private boolean ehAdministrador(HttpSession session) {
        return "admin".equals(session.getAttribute("tipoUsuario"));
    }

    // buscar titular da conta este metodo consulta os dados pela identidade conectada e encerra sessoes invalidas
    private DadosUsuarioDTO dadosDaSessao(HttpSession session) throws SQLException {
        Object usuario = session.getAttribute("usuarioLogado");

        String email = null;

        // ler email pessoal do aluno identifica a conta pelos dados do aluno conectado
        if (usuario instanceof loginAlunoDTO aluno) {
            email = aluno.getEmail();
        }
        // ler email pessoal do professor identifica a conta pelos dados do professor conectado
        else if (usuario instanceof loginProfessorDTO professor) {
            email = professor.getEmail();
        }

        // encerrar sessao sem email invalida o acesso quando nao consegue identificar a conta
        if (email == null) {
            session.invalidate();
            return null;
        }

        DadosUsuarioDTO dados = UsuarioRepository.consultarDadosPessoais(email);
        // encerrar sessao sem cadastro invalida o acesso quando o usuario nao existe mais no banco
        if (dados == null) {
            session.invalidate();
        }
        return dados;
    }

    // separar codigo de exclusao este metodo cria uma chave ligada a sessao e ao titular
    private String chaveExclusao(HttpSession session, DadosUsuarioDTO dados) {
        return "exclusao:" + session.getId() + ":" + dados.getId();
    }

    // tratar falha no perfil este metodo registra o problema e informa a indisponibilidade dos dados
    @ExceptionHandler({SQLException.class, RestClientException.class})
    public String erroBanco(Exception e, Model model, HttpServletResponse response) {
        logger.error("Erro ao acessar ou alterar dados do perfil.", e);
        // registro de erros confere se esta habilitado ao tratar falha no perfil
        if (logger.isErrorEnabled()) {
            // gravar historico ao tratar falha no perfil tenta salvar o registro no banco e informa falhas
            try {
                StringWriter excecaoLogBD = new StringWriter();
                e.printStackTrace(new PrintWriter(excecaoLogBD));
                UsuarioRepository.InserirLogsNoBD(null, "ERROR", ProfileController.class.getName(), "erroBanco", null,
                        "Erro ao acessar ou alterar dados do perfil.", null, excecaoLogBD.toString());
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        model.addAttribute("mensagemErro", "Não foi possível acessar seus dados. Tente novamente mais tarde.");
        return "Geral/profile";
    }
}
