// login unico identifica pelo email se a conta e de aluno ou professor e aplica bloqueio verificacao e sessao
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import com.example.CampusLink.dto.LoginDTO;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.LoginAttemptService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.sql.SQLException;
import java.util.Locale;

// controle do login esta classe recebe email e senha e descobre o perfil da conta antes de conferir a senha
@Controller
public class loginController {

    private static final Logger logger = LoggerFactory.getLogger(loginController.class);
    private static final String TELA_LOGIN = "Geral/login";
    private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";

    @Value("${campuslink.twofa.enabled:true}")
    private boolean twoFactorEnabled;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    @Autowired
    private UsuarioRepository UsuarioRepository;

    // abrir tela de login este metodo mostra o formulario unico ou leva ao inicio quem ja entrou
    @GetMapping("/login")
    public String login(Model model, HttpSession session) {
        // evitar novo login leva ao inicio quando a sessao ja esta autenticada
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }
        model.addAttribute("login", new LoginDTO());
        return TELA_LOGIN;
    }

    // manter enderecos antigos este metodo leva os links de login por perfil para a tela unica
    @GetMapping({"/loginAluno", "/loginProfessor"})
    public String loginAntigo() {
        return "redirect:/login";
    }

    // entrar na conta este metodo identifica o perfil pelo email confere a senha e inicia a verificacao ou a sessao
    @PostMapping("/login")
    public String entrar(@Valid @ModelAttribute("login") LoginDTO login, BindingResult result,
                         Model model, HttpSession session, HttpServletRequest request) throws SQLException {
        // evitar novo login leva ao inicio quando a sessao ja esta autenticada
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        String email = login.getEmail() == null ? "" : login.getEmail().trim().toLowerCase(Locale.ROOT);

        // bloquear conta recusa o acesso enquanto durar o bloqueio por tentativas
        if (LoginAttemptService.estaBloqueado(email)) {
            registrar("WARN", "Conta bloqueada tentou entrar. email=" + email);
            return erro(model, "Conta bloqueada por muitas tentativas. Tente novamente em alguns minutos.");
        }

        // validar formulario reapresenta a tela com o primeiro campo incorreto
        if (result.hasErrors()) {
            return erro(model, result.getAllErrors().get(0).getDefaultMessage());
        }

        String tipoUsuario = perfilDaConta(email);
        String senhaHash = tipoUsuario == null ? null : UsuarioRepository.QueryLoginUsuario(tipoUsuario, email);

        // conferir senha continua o acesso apenas para aluno ou professor com a senha correta
        if (senhaHash != null && !senhaHash.isBlank() && new BCryptPasswordEncoder(10).matches(login.getSenha(), senhaHash)) {
            loginAttemptService.loginSucesso(email);
            // trocar id da sessao impede que um id plantado antes do login seja usado para entrar na conta
            request.changeSessionId();
            session.setAttribute("email2FA", email);
            session.setAttribute("redirect", "Login");
            session.setAttribute("tipoUsuario", tipoUsuario);

            // exigir codigo envia a verificacao por email quando ela esta habilitada
            if (twoFactorEnabled) {
                String codigo = TwoFactorService.gerarCodigoSeLiberado(email);
                // enviar codigo novo so quando a espera terminou senao o codigo ja enviado continua valendo
                if (codigo != null) {
                    emailService.enviarCodigo(email, codigo, FinalidadeCodigoEmail.ACESSO);
                }
                return "aluno".equals(tipoUsuario) ? "redirect:/verificarAluno" : "redirect:/verificarProfessor";
            }

            session.setAttribute("usuarioLogado", "aluno".equals(tipoUsuario)
                    ? UsuarioRepository.buscarPorEmailAluno(email)
                    : UsuarioRepository.buscarPorEmailProfessor(email));
            session.setMaxInactiveInterval(900);
            registrar("INFO", "Login concluído. perfil=" + tipoUsuario + " email=" + email + " doisFatores=false");
            return "redirect:/home";
        }

        // orientar administrador com a senha certa indica o acesso administrativo sem contar como tentativa errada
        // so com a senha correta a mensagem aparece, assim o login nao revela quais emails sao de administrador
        if (tipoUsuario == null) {
            String senhaAdmin = UsuarioRepository.QueryLoginUsuario("admin", email);
            if (senhaAdmin != null && !senhaAdmin.isBlank() && new BCryptPasswordEncoder(10).matches(login.getSenha(), senhaAdmin)) {
                return erro(model, "Esta é uma conta de administrador. Entre pelo acesso administrativo.");
            }
        }

        loginAttemptService.loginFalhou(email);
        registrar("WARN", "Credenciais inválidas. email=" + email + " tentativas=" + loginAttemptService.getTentativas(email));

        // avisar bloqueio informa quando a tentativa atual atingiu o limite
        if (loginAttemptService.getTentativas(email) >= 5) {
            return erro(model, "Conta bloqueada por 10 minutos por excesso de tentativas.");
        }
        return erro(model, CREDENCIAIS_INVALIDAS);
    }

    // descobrir perfil este metodo devolve aluno ou professor e ignora administradores e emails inexistentes
    private String perfilDaConta(String email) throws SQLException {
        String perfil = UsuarioRepository.buscarPorTipoUsuario(email);
        // traduzir perfil converte o nome da tabela no tipo usado pela sessao
        if (perfil == null) {
            return null;
        }
        return switch (perfil.trim().toUpperCase(Locale.ROOT)) {
            case "ALUNOS" -> "aluno";
            case "PROFESSORES" -> "professor";
            default -> null;
        };
    }

    // reapresentar tela este metodo devolve o formulario com a mensagem de erro
    private String erro(Model model, String mensagem) {
        model.addAttribute("mensagemDeErro", mensagem);
        return TELA_LOGIN;
    }

    // registrar evento do login este metodo grava o aviso no log da aplicacao e no banco sem interromper o acesso
    private void registrar(String nivel, String mensagem) {
        // escolher nivel do log usa aviso para falhas e informacao para acessos concluidos
        if ("WARN".equals(nivel)) {
            logger.warn(mensagem);
        } else {
            logger.info(mensagem);
        }
        // gravar historico tenta salvar o evento no banco e apenas registra a falha quando nao consegue
        try {
            UsuarioRepository.InserirLogsNoBD(null, nivel, loginController.class.getName(), "entrar", null, mensagem, null, null);
        } catch (Exception erroLogBD) {
            logger.error("Erro ao gravar log no banco.", erroLogBD);
        }
    }
}
