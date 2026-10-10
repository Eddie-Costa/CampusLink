
package com.example.CampusLink.controller.Usuarios.Admin;

import org.slf4j.helpers.MessageFormatter;
import java.io.StringWriter;
import java.io.PrintWriter;
import com.example.CampusLink.dao.usuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.DenunciaDTO;
import com.example.CampusLink.service.ArquivoService;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.DenunciaService;
import com.example.CampusLink.service.RevisaoConteudoService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminConteudosController {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(AdminConteudosController.class);

    private static final String URL_LOGIN_ADMIN = "/admin/login";

    private final ConteudoService conteudoService;
    private final DenunciaService denunciaService;
    private final RevisaoConteudoService revisaoConteudoService;
    private final ArquivoService arquivoService;

    public AdminConteudosController(
            ConteudoService conteudoService,
            DenunciaService denunciaService,
            RevisaoConteudoService revisaoConteudoService,
            ArquivoService arquivoService
    ) {
        this.conteudoService = conteudoService;
        this.denunciaService = denunciaService;
        this.revisaoConteudoService = revisaoConteudoService;
        this.arquivoService = arquivoService;
    }

    @GetMapping("/admin/conteudos-denunciados")
    public String listarConteudos(HttpSession session, Model model) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            List<ConteudoDTO> conteudos = conteudoService.listarParaAnaliseAdmin();
            Map<Long, List<DenunciaDTO>> denunciasPorConteudo = new HashMap<>();
            Map<Long, ArquivoDTO> arquivosPorConteudo = new HashMap<>();

            for (ConteudoDTO conteudo : conteudos) {
                denunciasPorConteudo.put(
                        conteudo.getId(),
                        denunciaService.listarPorConteudo(conteudo.getId())
                );

                ArquivoDTO arquivo = arquivoService.buscarMaisRecentePorConteudo(conteudo.getId());

                if (arquivo != null) {
                    arquivosPorConteudo.put(conteudo.getId(), arquivo);
                }
            }

            model.addAttribute("conteudos", conteudos);
            model.addAttribute("denunciasPorConteudo", denunciasPorConteudo);
            model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);

        } catch (RuntimeException e) {
            logger.error("erro ao carregar conteúdos para análise", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminConteudosController.class.getName(), "listarConteudos", null,
                            "erro ao carregar conteúdos para análise", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("mensagemErro", "Não foi possível carregar os conteúdos para análise.");
            model.addAttribute("conteudos", new ArrayList<ConteudoDTO>());
            model.addAttribute("denunciasPorConteudo", new HashMap<Long, List<DenunciaDTO>>());
            model.addAttribute("arquivosPorConteudo", new HashMap<Long, ArquivoDTO>());
        }

        return "Usuarios/Admin/conteudosDenunciados";
    }

    @PostMapping("/admin/conteudos-denunciados/{idConteudo}/liberar")
    public String liberarConteudo(
            @PathVariable Long idConteudo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            boolean liberado = revisaoConteudoService.liberarConteudo(idConteudo);

            if (liberado) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Conteúdo liberado novamente.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Este conteúdo não está aguardando análise.");
            }

        } catch (RuntimeException e) {
            logger.error("erro ao liberar o conteúdo {}", idConteudo, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminConteudosController.class.getName(), "liberarConteudo", null,
                            MessageFormatter.arrayFormat("erro ao liberar o conteúdo {}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível liberar o conteúdo.");
        }

        return "redirect:/admin/conteudos-denunciados";
    }

    @PostMapping("/admin/conteudos-denunciados/{idConteudo}/reprovar")
    public String reprovarConteudo(
            @PathVariable Long idConteudo,
            @RequestParam String comentarioAdmin,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        if (comentarioAdmin == null || comentarioAdmin.isBlank()) {
            logger.warn("Reprovação recusada: motivo vazio. conteudoId={}", idConteudo);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", AdminConteudosController.class.getName(), "reprovarConteudo", null,
                            MessageFormatter.arrayFormat("Reprovação recusada: motivo vazio. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemErro", "Informe o motivo da reprovação.");

            return "redirect:/admin/conteudos-denunciados";
        }

        try {
            boolean reprovado = revisaoConteudoService.reprovarConteudo(idConteudo, comentarioAdmin);

            if (reprovado) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Conteúdo reprovado e removido.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Este conteúdo não está aguardando análise.");
            }

        } catch (RuntimeException e) {
            logger.error("erro ao reprovar o conteúdo {}", idConteudo, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", AdminConteudosController.class.getName(), "reprovarConteudo", null,
                            MessageFormatter.arrayFormat("erro ao reprovar o conteúdo {}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível reprovar o conteúdo.");
        }

        return "redirect:/admin/conteudos-denunciados";
    }

    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        if (!(usuarioLogado instanceof loginAdminDTO)) {
            return false;
        }

        if (!(emailSessao instanceof String)) {
            return false;
        }

        loginAdminDTO admin = (loginAdminDTO) usuarioLogado;

        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        return admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}
