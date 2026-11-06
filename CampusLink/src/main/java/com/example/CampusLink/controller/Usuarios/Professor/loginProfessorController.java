// entrada de professores organiza o login desse perfil
package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.service.LoginAttemptService;
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
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.slf4j.helpers.MessageFormatter;

import java.sql.SQLException;

// autenticacao de professor esta classe confere senha bloqueios e verificacao por email
@Controller
public class loginProfessorController {
    private static final Logger logger = LoggerFactory.getLogger(loginProfessorController.class);

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

    // abrir login de professor este metodo prepara o formulario para quem ainda nao entrou
    @GetMapping("/loginProfessor")
    public String loginPage(Model model, HttpSession session) {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        model.addAttribute("professor", new loginProfessorDTO());
        return "Usuarios/Professor/loginProfessor";
    }

    // validar login de professor este metodo confere senha tentativas e a necessidade de codigo por email
    @PostMapping("/loginProfessor")
    public String fazerLogin(@Valid @ModelAttribute("professor") loginProfessorDTO loginProfessorDTO, BindingResult result, Model model, HttpSession session) throws SQLException {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        String email = loginProfessorDTO.getEmail();


        // bloquear login do professor impede novas tentativas enquanto a conta esta bloqueada
        if (LoginAttemptService.estaBloqueado(email)) {
            logger.warn("Conta bloqueada para professor {}", email);
            // registro de avisos confere se esta habilitado ao validar login de professor
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar login de professor tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", loginProfessorController.class.getName(), "fazerLogin", null,
                            MessageFormatter.arrayFormat("Conta bloqueada para professor {}", new Object[]{email}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            model.addAttribute("mensagemDeErro", "Conta bloqueada por muitas tentativas. Tente mais tarde.");
            return "Usuarios/Professor/loginProfessor";
        }

        // validar campos do login do professor reapresenta o acesso quando os dados possuem erros
        if (result.hasErrors()) {
            logger.warn("Dados de login inválidos para professor {}", email);
            // registro de avisos confere se esta habilitado ao validar login de professor
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar login de professor tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", loginProfessorController.class.getName(), "fazerLogin", null,
                            MessageFormatter.arrayFormat("Dados de login inválidos para professor {}", new Object[]{email}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            model.addAttribute("mensagemDeErro", "Erro ao fazer login, tente novamente!!!");
            return "Usuarios/Professor/loginProfessor";
        }


        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);


        String senhaHash = UsuarioRepository.QueryLoginUsuario( "professor", loginProfessorDTO.getEmail());

        // conferir senha do professor continua o acesso quando a senha corresponde ao valor salvo
        if(senhaHash != null && encoder.matches(loginProfessorDTO.getSenha(), senhaHash)) {

            loginAttemptService.loginSucesso(email);

            // exigir codigo do professor envia a verificacao quando a confirmacao por email esta habilitada
            if (twoFactorEnabled) {
                String codigo = twoFactorService.gerarCodigo(email);
                emailService.enviarCodigo(email, codigo, FinalidadeCodigoEmail.ACESSO);

                session.setAttribute("email2FA", email);
                session.setAttribute("redirect", "Login");
                session.setAttribute("tipoUsuario", "professor");

                return "redirect:/verificarProfessor";
            }

            session.setAttribute("email2FA", email);
            session.setAttribute("redirect", "Login");
            session.setAttribute("tipoUsuario", "professor");
            session.setAttribute("usuarioLogado", UsuarioRepository.buscarPorEmailProfessor(email));
            session.setMaxInactiveInterval(900);

            // confirmar acesso do professor registra o sucesso apenas quando a conta foi carregada
            if (session.getAttribute("usuarioLogado") != null) {
                logger.info("Login concluído. perfil=professor email={} doisFatores=false", email);
                // registro de informacoes confere se esta habilitado ao validar login de professor
                if (logger.isInfoEnabled()) {
                    // gravar historico ao validar login de professor tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", loginProfessorController.class.getName(), "fazerLogin", null,
                                MessageFormatter.arrayFormat("Login concluído. perfil=professor email={} doisFatores=false", new Object[]{email}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }
            return "redirect:/home";
        }


        loginAttemptService.loginFalhou(email);
        logger.warn("Credenciais inválidas para professor {}. Tentativas: {}", email, loginAttemptService.getTentativas(email));
        // registro de avisos confere se esta habilitado ao validar login de professor
        if (logger.isWarnEnabled()) {
            // gravar historico ao validar login de professor tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "WARN", loginProfessorController.class.getName(), "fazerLogin", null,
                        MessageFormatter.arrayFormat("Credenciais inválidas para professor {}. Tentativas: {}", new Object[]{email, loginAttemptService.getTentativas(email)}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        // informar bloqueio do professor avisa o prazo apos cinco tentativas ou informa credenciais invalidas
        if (loginAttemptService.getTentativas(email) >= 5) {
            logger.warn("Conta bloqueada por excesso de tentativas para professor {}", email);
            // registro de avisos confere se esta habilitado ao validar login de professor
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar login de professor tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", loginProfessorController.class.getName(), "fazerLogin", null,
                            MessageFormatter.arrayFormat("Conta bloqueada por excesso de tentativas para professor {}", new Object[]{email}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            model.addAttribute("mensagemDeErro", "Conta bloqueada por 10 minutos.");
        } else {
            model.addAttribute("mensagemDeErro", "Email ou senha inválidos.");
        }
        return "Usuarios/Professor/loginProfessor";
    }
}
