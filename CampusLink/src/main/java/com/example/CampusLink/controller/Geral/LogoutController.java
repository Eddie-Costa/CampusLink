// saida da plataforma recebe o pedido de encerrar o acesso
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.repository.UsuarioRepository;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;

// controle de logout esta classe registra a saida e invalida a sessao atual
@Controller
public class LogoutController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(LogoutController.class);

    // encerrar login este metodo registra a saida e invalida a sessao do usuario
    @GetMapping("/logout")
    public String logout(HttpSession session) {

        Object usuarioLogado = session.getAttribute("usuarioLogado");

        // encerrar acesso inexistente retorna ao login quando nao ha usuario para desconectar
        if (usuarioLogado == null) {

            return "redirect:/login";
        }

        Object tipoSalvo = session.getAttribute("tipoUsuario");

        String tipoUsuario = tipoSalvo instanceof String ? (String) tipoSalvo : null;


        // recuperar perfil ausente identifica o tipo pelos dados guardados do usuario
        if (tipoUsuario == null) {

            // reconhecer aluno na saida define o perfil quando os dados pertencem a um aluno
            if (usuarioLogado instanceof loginAlunoDTO) {

                tipoUsuario = "aluno";

            }
            // reconhecer professor na saida define o perfil quando os dados pertencem a um professor
            else if (usuarioLogado instanceof loginProfessorDTO) {

                tipoUsuario = "professor";
            }
            // reconhecer administrador na saida define o perfil quando os dados pertencem a um administrador
            else if (usuarioLogado instanceof loginAdminDTO) {
                tipoUsuario = "admin";
            }
        }

        String usuario = null;

        // registrar saida do aluno recupera o usuario associado ao contexto desse perfil
        if ("aluno".equals(tipoUsuario)) {

            usuario = MDC.get("aluno");

        }
        // registrar saida do professor recupera o usuario associado ao contexto desse perfil
        else if ("professor".equals(tipoUsuario)) {

            usuario = MDC.get("professor");
        }
        // registrar saida do administrador recupera o usuario associado ao contexto desse perfil
        else if ("admin".equals(tipoUsuario)) {
            usuario = MDC.get("admin");
        }

        logger.info("Sessão encerrada para o usuário {}", usuario != null ? usuario : "usuario autenticado");
        // registro de informacoes confere se esta habilitado ao encerrar login
        if (logger.isInfoEnabled()) {
            // gravar historico ao encerrar login tenta salvar o registro no banco e informa falhas
            try {
                UsuarioRepository.InserirLogsNoBD(null, "INFO", LogoutController.class.getName(), "logout", null,
                        MessageFormatter.arrayFormat("Sessão encerrada para o usuário {}", new Object[]{usuario != null ? usuario : "usuario autenticado"}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        session.invalidate();
        MDC.remove("aluno");
        MDC.remove("professor");
        MDC.remove("admin");
        MDC.remove("sessionId");
        return "redirect:/home";
    }
}
