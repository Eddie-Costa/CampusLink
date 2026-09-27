package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
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

@Controller
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    @Autowired
    private usuarioDAO usuarioDAO;

    @Autowired
    private emailService emailService;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private SessaoLogListener sessaoLogListener;

    @GetMapping({"/profile", "/profile/dados"})
    public String profile(HttpSession session, Model model) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        DadosUsuarioDTO dados = dadosDaSessao(session);
        if (dados == null) {
            return "redirect:/login";
        }
        model.addAttribute("dados", dados);
        model.addAttribute("possuiVinculos", usuarioDAO.possuiVinculosParaExclusao(dados.getId()));
        model.addAttribute("exclusaoPendente", Boolean.TRUE.equals(session.getAttribute("exclusaoPendente")));
        return "Geral/profile";
    }

    @PostMapping("/profile/exportar")
    public String exportar(HttpSession session, RedirectAttributes redirectAttributes) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        DadosUsuarioDTO dados = dadosDaSessao(session);
        if (dados == null) {
            return "redirect:/login";
        }
        try {
            emailService.enviarEmailDados(dados);
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Seus dados cadastrais foram enviados para o e-mail da sua conta.");
        } catch (MailException e) {
            logger.warn("Falha no envio da exportação de dados cadastrais.");
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível enviar o e-mail. Tente novamente mais tarde.");
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/excluir")
    public String solicitarExclusao(HttpSession session, RedirectAttributes redirectAttributes) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        DadosUsuarioDTO dados = dadosDaSessao(session);
        if (dados == null) {
            return "redirect:/login";
        }
        if (usuarioDAO.possuiVinculosParaExclusao(dados.getId())) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Antes de excluir sua conta, remova ou transfira suas turmas, materiais e eventos.");
            return "redirect:/profile";
        }

        synchronized (session) {
            Long ultimoEnvio = (Long) session.getAttribute("ultimoEnvioExclusao");
            if (ultimoEnvio != null && System.currentTimeMillis() - ultimoEnvio < 120_000) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Aguarde dois minutos entre os envios do código de exclusão.");
                return "redirect:/profile";
            }
            // Uma chave própria impede o uso de códigos de login/recuperação para excluir uma conta.
            String chave = chaveExclusao(session, dados);
            String codigo = TwoFactorService.gerarCodigo(chave);
            session.setAttribute("ultimoEnvioExclusao", System.currentTimeMillis());
            try {
                emailService.enviarCodigo(dados.getEmail(), codigo);
                session.setAttribute("exclusaoPendente", true);
                session.setAttribute("tentativasExclusao", 0);
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Solicitamos o envio de um código ao seu e-mail. Ele vale por cinco minutos.");
            } catch (MailException e) {
                twoFactorService.limparCodigo(chave);
                session.removeAttribute("exclusaoPendente");
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível enviar o código. Tente novamente mais tarde.");
            }
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/excluir/confirmar")
    public String confirmarExclusao(HttpSession session, @RequestParam(defaultValue = "") String codigo,
                                   @RequestParam(defaultValue = "false") boolean confirmar,
                                   RedirectAttributes redirectAttributes) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        DadosUsuarioDTO dados = dadosDaSessao(session);
        if (dados == null) {
            return "redirect:/login";
        }
        synchronized (session) {
            if (!Boolean.TRUE.equals(session.getAttribute("exclusaoPendente")) || !confirmar) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Solicite um código e confirme que deseja excluir a conta.");
                return "redirect:/profile";
            }
            String chave = chaveExclusao(session, dados);
            int tentativas = (Integer) session.getAttribute("tentativasExclusao");
            if (!codigo.matches("\\d{6}") || !twoFactorService.validarCodigo(chave, codigo)) {
                session.setAttribute("tentativasExclusao", ++tentativas);
                if (tentativas >= 5) {
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
            try {
                usuarioDAO.excluirDadosPessoais(dados.getId(), dados.getEmail());
            } catch (IllegalStateException e) {
                redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
                return "redirect:/profile";
            }
        }
        twoFactorService.limparCodigo(dados.getEmail());
        sessaoLogListener.encerrarSessoesDoUsuario(dados.getEmail());
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // O listener já pode ter encerrado a sessão atual.
        }
        return "redirect:/login?contaExcluida";
    }

    @PostMapping("/profile/excluir/cancelar")
    public String cancelarExclusao(HttpSession session) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }
        DadosUsuarioDTO dados = dadosDaSessao(session);
        if (dados == null) {
            return "redirect:/login";
        }
        twoFactorService.limparCodigo(chaveExclusao(session, dados));
        session.removeAttribute("exclusaoPendente");
        session.removeAttribute("tentativasExclusao");
        return "redirect:/profile";
    }

    private DadosUsuarioDTO dadosDaSessao(HttpSession session) throws SQLException {
        Object usuario = session.getAttribute("usuarioLogado");
        // O titular vem da identidade autenticada, nunca de parâmetros ou do e-mail temporário do 2FA.
        String email = null;

        if (usuario instanceof loginAlunoDTO aluno) {
            email = aluno.getEmail();
        } else if (usuario instanceof loginProfessorDTO professor) {
            email = professor.getEmail();
        }

        if (email == null) {
            session.invalidate();
            return null;
        }

        DadosUsuarioDTO dados = usuarioDAO.consultarDadosPessoais(email);
        if (dados == null) {
            session.invalidate();
        }
        return dados;
    }

    private String chaveExclusao(HttpSession session, DadosUsuarioDTO dados) {
        return "exclusao:" + session.getId() + ":" + dados.getId();
    }

    @ExceptionHandler({SQLException.class, RestClientException.class})
    public String erroBanco(Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        model.addAttribute("mensagemErro", "Não foi possível acessar seus dados. Tente novamente mais tarde.");
        return "Geral/profile";
    }
}
