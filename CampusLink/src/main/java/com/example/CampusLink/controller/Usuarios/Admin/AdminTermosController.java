package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import com.example.CampusLink.dto.TermoVersaoResumoDTO;
import com.example.CampusLink.repository.AdminUsuarioRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.TermosService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.util.List;

@Controller
public class AdminTermosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminTermosController.class);

    private static final String URL_LOGIN_ADMIN = "/admin/login";

    private final TermosService termosService;
    private final AdminUsuarioRepository adminUsuarioRepository;
    private final UsuarioRepository UsuarioRepository;

    public AdminTermosController(TermosService termosService, AdminUsuarioRepository adminUsuarioRepository,
                                 UsuarioRepository UsuarioRepository) {
        this.termosService = termosService;
        this.adminUsuarioRepository = adminUsuarioRepository;
        this.UsuarioRepository = UsuarioRepository;
    }

    @GetMapping("/admin/termos")
    public String listar(HttpSession session, Model model) throws SQLException {
        String redirecionamento = verificarMaster(session);
        if (redirecionamento != null) {
            return redirecionamento;
        }

        List<TermoVersaoResumoDTO> versoes = termosService.listarVersoes();
        model.addAttribute("versoes", versoes);
        if (!model.containsAttribute("conteudo")) {
            model.addAttribute("conteudo", versoes.isEmpty() ? "" : versoes.getFirst().conteudo());
        }
        return "Usuarios/Admin/termosAdmin";
    }

    @PostMapping("/admin/termos")
    public String publicar(@RequestParam String versao,
                           @RequestParam String conteudo,
                           @RequestParam(defaultValue = "false") boolean confirmar,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) throws SQLException {
        String redirecionamento = verificarMaster(session);
        if (redirecionamento != null) {
            return redirecionamento;
        }

        if (!confirmar) {
            return voltarComErro(redirectAttributes, versao, conteudo,
                    "Confirme que todos os usuários precisarão aceitar a nova versão.");
        }

        try {
            String hash = termosService.publicarNovaVersao(versao, conteudo);
            String email = (String) session.getAttribute("email2FA");

            logger.info("Nova versão dos termos publicada. versao={} hash={} admin={}", versao.strip(), hash, email);
            if (logger.isInfoEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", AdminTermosController.class.getName(), "publicar", null,
                            MessageFormatter.arrayFormat("Nova versão dos termos publicada. versao={} hash={} admin={}", new Object[]{versao.strip(), hash, email}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Versão " + versao.strip() + " publicada. Os usuários verão o novo termo no próximo acesso.");
        } catch (IllegalArgumentException e) {
            return voltarComErro(redirectAttributes, versao, conteudo, e.getMessage());
        }

        return "redirect:/admin/termos";
    }

    private String voltarComErro(RedirectAttributes redirectAttributes, String versao, String conteudo, String mensagem) {
        redirectAttributes.addFlashAttribute("mensagemErro", mensagem);
        redirectAttributes.addFlashAttribute("versao", versao);
        redirectAttributes.addFlashAttribute("conteudo", conteudo);
        return "redirect:/admin/termos";
    }

    private String verificarMaster(HttpSession session) throws SQLException {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        usuarioAdminDTO admin = adminUsuarioRepository.buscarAdministradorPorEmail((String) session.getAttribute("email2FA"));
        return admin != null && admin.isMaster() ? null : "redirect:/admin/painel";
    }

    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        if (!(usuarioLogado instanceof loginAdminDTO admin)) {
            return false;
        }

        if (!(emailSessao instanceof String)) {
            return false;
        }

        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        return admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}
