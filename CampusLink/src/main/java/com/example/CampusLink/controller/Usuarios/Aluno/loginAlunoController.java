// entrada de alunos organiza o login desse perfil
package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.service.LoginAttemptService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.sql.SQLException;

// autenticacao de aluno esta classe confere senha bloqueios e verificacao por email
@Controller
public class loginAlunoController {
    private static final Logger logger = LoggerFactory.getLogger(loginAlunoController.class);

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

    // abrir login de aluno este metodo prepara o formulario para quem ainda nao entrou
    @GetMapping("/loginAluno")
    public String loginPage(Model model, HttpSession session) {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        model.addAttribute("aluno", new loginAlunoDTO());
        return "Usuarios/Aluno/loginAluno";
    }

    // validar login de aluno este metodo confere senha tentativas e a necessidade de codigo por email
    @PostMapping("/loginAluno")
    public String fazerLogin(@Valid @ModelAttribute("aluno") loginAlunoDTO loginAlunoDTO, BindingResult result, Model model, HttpSession session) throws SQLException {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        String email = loginAlunoDTO.getEmail();


        // bloquear login do aluno impede novas tentativas enquanto a conta esta bloqueada
        if (LoginAttemptService.estaBloqueado(email)) {
            logger.warn("Conta bloqueada para aluno {}", email);
            // registro de avisos confere se esta habilitado ao validar login de aluno
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar login de aluno tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", loginAlunoController.class.getName(), "fazerLogin", null,
                            MessageFormatter.arrayFormat("Conta bloqueada para aluno {}", new Object[]{email}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            model.addAttribute("mensagemDeErro", "Conta bloqueada por muitas tentativas. Tente mais tarde.");
            return "Usuarios/Aluno/loginAluno";
        }

        // validar campos do login do aluno reapresenta o acesso quando os dados possuem erros
        if (result.hasErrors()) {
            logger.warn("Dados de login inválidos para aluno {}", email);
            // registro de avisos confere se esta habilitado ao validar login de aluno
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar login de aluno tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", loginAlunoController.class.getName(), "fazerLogin", null,
                            MessageFormatter.arrayFormat("Dados de login inválidos para aluno {}", new Object[]{email}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            model.addAttribute("mensagemDeErro", "Erro ao fazer login, tente novamente!!!");
            return "Usuarios/Aluno/loginAluno";
        }


        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);


        String senhaHash = UsuarioRepository.QueryLoginUsuario( "aluno", loginAlunoDTO.getEmail());

        // conferir senha do aluno continua o acesso quando a senha corresponde ao valor salvo
        if(senhaHash != null && encoder.matches(loginAlunoDTO.getSenha(), senhaHash)) {

            loginAttemptService.loginSucesso(email);

            // exigir codigo do aluno envia a verificacao quando a confirmacao por email esta habilitada
            if (twoFactorEnabled) {
                String codigo = twoFactorService.gerarCodigo(email);
                emailService.enviarCodigo(email, codigo, FinalidadeCodigoEmail.ACESSO);

                session.setAttribute("email2FA", email);
                session.setAttribute("redirect", "Login");
                session.setAttribute("tipoUsuario", "aluno");

                return "redirect:/verificarAluno";
            }

            session.setAttribute("email2FA", email);
            session.setAttribute("redirect", "Login");
            session.setAttribute("tipoUsuario", "aluno");
            session.setAttribute("usuarioLogado", UsuarioRepository.buscarPorEmailAluno(email));
            session.setMaxInactiveInterval(900);

            // confirmar acesso do aluno registra o sucesso apenas quando a conta foi carregada na sessao
            if (session.getAttribute("usuarioLogado") != null) {
                logger.info("Login concluído. perfil=aluno email={} doisFatores=false", email);
                // registro de informacoes confere se esta habilitado ao validar login de aluno
                if (logger.isInfoEnabled()) {
                    // gravar historico ao validar login de aluno tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", loginAlunoController.class.getName(), "fazerLogin", null,
                                MessageFormatter.arrayFormat("Login concluído. perfil=aluno email={} doisFatores=false", new Object[]{email}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }
            return "redirect:/home";
        }


        loginAttemptService.loginFalhou(email);
        logger.warn("Credenciais inválidas para aluno {}. Tentativas: {}", email, loginAttemptService.getTentativas(email));
        // registro de avisos confere se esta habilitado ao validar login de aluno
        if (logger.isWarnEnabled()) {
            // gravar historico ao validar login de aluno tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "WARN", loginAlunoController.class.getName(), "fazerLogin", null,
                        MessageFormatter.arrayFormat("Credenciais inválidas para aluno {}. Tentativas: {}", new Object[]{email, loginAttemptService.getTentativas(email)}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        // informar bloqueio do aluno avisa o prazo apos cinco tentativas ou informa credenciais invalidas
        if (loginAttemptService.getTentativas(email) >= 5) {
            logger.warn("Conta bloqueada por excesso de tentativas para aluno {}", email);
            // registro de avisos confere se esta habilitado ao validar login de aluno
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar login de aluno tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", loginAlunoController.class.getName(), "fazerLogin", null,
                            MessageFormatter.arrayFormat("Conta bloqueada por excesso de tentativas para aluno {}", new Object[]{email}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            model.addAttribute("mensagemDeErro", "Conta bloqueada por 10 minutos.");
        } else {
            model.addAttribute("mensagemDeErro", "Email ou senha inválidos.");
        }
        return "Usuarios/Aluno/loginAluno";
    }
}
