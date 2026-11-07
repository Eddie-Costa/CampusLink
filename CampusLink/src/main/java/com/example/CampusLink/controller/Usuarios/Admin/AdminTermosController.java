// gestao dos termos de uso lgpd permite ao administrador master publicar novas versoes e ver o historico de aceites
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

// controle dos termos no backoffice esta classe restringe ao administrador master a publicacao de novas versoes
@Controller
public class AdminTermosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminTermosController.class);

    private static final String URL_LOGIN_ADMIN = "/admin/login";

    private final TermosService termosService;
    private final AdminUsuarioRepository adminUsuarioRepository;
    private final UsuarioRepository UsuarioRepository;

    // preparar gestao dos termos este construtor recebe o servico dos termos e as consultas de administradores
    public AdminTermosController(TermosService termosService, AdminUsuarioRepository adminUsuarioRepository,
                                 UsuarioRepository UsuarioRepository) {
        this.termosService = termosService;
        this.adminUsuarioRepository = adminUsuarioRepository;
        this.UsuarioRepository = UsuarioRepository;
    }

    // listar versoes dos termos este metodo mostra o historico e preenche o formulario com o texto vigente
    @GetMapping("/admin/termos")
    public String listar(HttpSession session, Model model) throws SQLException {
        String redirecionamento = verificarMaster(session);
        // exigir administrador master redireciona quem nao tem permissao para gerir os termos
        if (redirecionamento != null) {
            return redirecionamento;
        }

        List<TermoVersaoResumoDTO> versoes = termosService.listarVersoes();
        model.addAttribute("versoes", versoes);
        // preencher formulario usa o texto vigente quando nao ha texto devolvido por um erro anterior
        if (!model.containsAttribute("conteudo")) {
            model.addAttribute("conteudo", versoes.isEmpty() ? "" : versoes.getFirst().conteudo());
        }
        return "Usuarios/Admin/termosAdmin";
    }

    // publicar nova versao dos termos este metodo grava a versao confirmada pelo master e registra quem publicou
    @PostMapping("/admin/termos")
    public String publicar(@RequestParam String versao,
                           @RequestParam String conteudo,
                           @RequestParam(defaultValue = "false") boolean confirmar,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) throws SQLException {
        String redirecionamento = verificarMaster(session);
        // exigir administrador master redireciona quem nao tem permissao para publicar
        if (redirecionamento != null) {
            return redirecionamento;
        }

        // exigir confirmacao recusa a publicacao sem o aviso de que todos precisarao aceitar de novo
        if (!confirmar) {
            return voltarComErro(redirectAttributes, versao, conteudo,
                    "Confirme que todos os usuários precisarão aceitar a nova versão.");
        }

        // gravar nova versao publica o texto e devolve o formulario preenchido quando a validacao falha
        try {
            String hash = termosService.publicarNovaVersao(versao, conteudo);
            String email = (String) session.getAttribute("email2FA");

            logger.info("Nova versão dos termos publicada. versao={} hash={} admin={}", versao.strip(), hash, email);
            // registro de informacoes confere se esta habilitado ao publicar os termos
            if (logger.isInfoEnabled()) {
                // gravar historico da publicacao tenta salvar o registro no banco e informa falhas
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

    // devolver formulario com erro este metodo mantem versao e texto digitados para o admin nao perder o trabalho
    private String voltarComErro(RedirectAttributes redirectAttributes, String versao, String conteudo, String mensagem) {
        redirectAttributes.addFlashAttribute("mensagemErro", mensagem);
        redirectAttributes.addFlashAttribute("versao", versao);
        redirectAttributes.addFlashAttribute("conteudo", conteudo);
        return "redirect:/admin/termos";
    }

    // verificar administrador master este metodo devolve o redirecionamento adequado ou nulo quando o acesso e permitido
    private String verificarMaster(HttpSession session) throws SQLException {
        // exigir login administrativo retorna ao login do admin quando nao ha usuario conectado
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }
        // exigir administrador retorna ao inicio quando a sessao e de outro perfil
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        usuarioAdminDTO admin = adminUsuarioRepository.buscarAdministradorPorEmail((String) session.getAttribute("email2FA"));
        return admin != null && admin.isMaster() ? null : "redirect:/admin/painel";
    }

    // validar administrador dos termos este metodo confere o perfil e o email da conta conectada
    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        // conferir perfil administrativo rejeita sessoes com outro tipo de usuario
        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        // conferir dados administrativos rejeita um usuario que nao possui os dados de administrador
        if (!(usuarioLogado instanceof loginAdminDTO admin)) {
            return false;
        }

        // conferir email da sessao rejeita valores que nao sao texto
        if (!(emailSessao instanceof String)) {
            return false;
        }

        // exigir email do administrador rejeita contas sem email preenchido
        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        return admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}
