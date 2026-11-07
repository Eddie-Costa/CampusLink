// entrada administrativa organiza o login de contas de gestao

package com.example.CampusLink.controller.Usuarios.Admin;

import org.slf4j.helpers.MessageFormatter;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
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

import java.sql.SQLException;

// autenticacao administrativa esta classe confere senha bloqueios e verificacao por email
@Controller
public class loginAdminController {

    private static final Logger logger = LoggerFactory.getLogger(loginAdminController.class);

    private static final String URL_LOGIN_ADMIN = "/admin/login";

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

    // abrir login administrativo este metodo prepara o formulario de entrada do administrador
    @GetMapping(URL_LOGIN_ADMIN)
    public String loginPage(Model model) {

        model.addAttribute("admin", new loginAdminDTO());

        return "Usuarios/Admin/loginAdmin";
    }

    // validar login administrativo este metodo confere senha tentativas e a necessidade de codigo por email
    @PostMapping(URL_LOGIN_ADMIN)
    public String fazerLogin(
            @Valid @ModelAttribute("admin") loginAdminDTO loginAdminDTO,
            BindingResult result,
            Model model,
            HttpSession session
    ) throws SQLException {

        String email = loginAdminDTO.getEmail();


        // bloquear login administrativo impede novas tentativas enquanto a conta esta bloqueada
        if (LoginAttemptService.estaBloqueado(email)) {

            logger.warn("Conta bloqueada para administrador {}", email);
            // registro de avisos confere se esta habilitado ao validar login administrativo
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar login administrativo tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", loginAdminController.class.getName(), "fazerLogin", null,
                            MessageFormatter.arrayFormat("Conta bloqueada para administrador {}", new Object[]{email}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute(
                    "mensagemDeErro",
                    "Conta bloqueada por muitas tentativas. Tente mais tarde."
            );

            return "Usuarios/Admin/loginAdmin";
        }

        // validar campos do login administrativo reapresenta o formulario quando os dados possuem erros
        if (result.hasErrors()) {

            logger.warn("Dados de login inválidos para administrador {}", email);
            // registro de avisos confere se esta habilitado ao validar login administrativo
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar login administrativo tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", loginAdminController.class.getName(), "fazerLogin", null,
                            MessageFormatter.arrayFormat("Dados de login inválidos para administrador {}", new Object[]{email}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute(
                    "mensagemDeErro",
                    "Erro ao fazer login, tente novamente."
            );

            return "Usuarios/Admin/loginAdmin";
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        String senhaHash = UsuarioRepository.QueryLoginUsuario(
                "admin",
                loginAdminDTO.getEmail()
        );

        // conferir senha administrativa continua o acesso apenas quando a senha corresponde ao valor salvo
        if (senhaHash != null &&
                !senhaHash.isBlank() &&
                encoder.matches(loginAdminDTO.getSenha(), senhaHash)) {

            loginAttemptService.loginSucesso(email);

            // exigir codigo administrativo envia o codigo e abre a verificacao quando essa etapa esta habilitada
            if (twoFactorEnabled) {

                String codigo = twoFactorService.gerarCodigo(email);

                emailService.enviarCodigo(email, codigo, FinalidadeCodigoEmail.ACESSO);

                session.setAttribute("email2FA", email);
                session.setAttribute("redirect", "Login");
                session.setAttribute("tipoUsuario", "admin");

                return "redirect:/verificarAdmin";
            }

            session.setAttribute("email2FA", email);
            session.setAttribute("redirect", "Login");
            session.setAttribute("tipoUsuario", "admin");
            session.setAttribute(
                    "usuarioLogado",
                    UsuarioRepository.buscarPorEmailAdmin(email)
            );

            session.setMaxInactiveInterval(900);

            logger.info("Login concluído. perfil=admin email={} doisFatores=false", email);
            // registro de informacoes confere se esta habilitado ao validar login administrativo
            if (logger.isInfoEnabled()) {
                // gravar historico ao validar login administrativo tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", loginAdminController.class.getName(), "fazerLogin", null,
                            MessageFormatter.arrayFormat("Login concluído. perfil=admin email={} doisFatores=false", new Object[]{email}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            return "redirect:/admin/painel";
        }

        loginAttemptService.loginFalhou(email);

        logger.warn("Credenciais inválidas para administrador {}. Tentativas: {}", email, loginAttemptService.getTentativas(email));
        // registro de avisos confere se esta habilitado ao validar login administrativo
        if (logger.isWarnEnabled()) {
            // gravar historico ao validar login administrativo tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "WARN", loginAdminController.class.getName(), "fazerLogin", null,
                        MessageFormatter.arrayFormat("Credenciais inválidas para administrador {}. Tentativas: {}", new Object[]{email, loginAttemptService.getTentativas(email)}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        // informar bloqueio administrativo avisa o prazo apos cinco tentativas ou informa credenciais invalidas
        if (loginAttemptService.getTentativas(email) >= 5) {
            logger.warn("Conta bloqueada por excesso de tentativas para administrador {}", email);
            // registro de avisos confere se esta habilitado ao validar login administrativo
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar login administrativo tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", loginAdminController.class.getName(), "fazerLogin", null,
                            MessageFormatter.arrayFormat("Conta bloqueada por excesso de tentativas para administrador {}", new Object[]{email, loginAttemptService.getTentativas(email)}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute(
                    "mensagemDeErro",
                    "Conta bloqueada por 10 minutos."
            );

        } else {

            model.addAttribute(
                    "mensagemDeErro",
                    "Email ou senha inválidos."
            );
        }

        return "Usuarios/Admin/loginAdmin";
    }
}
