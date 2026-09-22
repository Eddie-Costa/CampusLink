package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.DenunciaDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.service.ArquivoService;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.DenunciaService;
import com.example.CampusLink.service.RevisaoConteudoService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminConteudosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminConteudosController.class);

    private final ConteudoService conteudoService;
    private final DenunciaService denunciaService;
    private final RevisaoConteudoService revisaoConteudoService;
    private final ArquivoService arquivoService;

    @Value("${CAMPUSLINK_ADMIN_EMAIL:}")
    private String emailAdmin;

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
            return "redirect:/login";
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
            return "redirect:/login";
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
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível liberar o conteúdo.");
        }

        return "redirect:/admin/conteudos-denunciados";
    }

    private boolean ehAdministrador(HttpSession session) {

        if (emailAdmin == null || emailAdmin.isBlank() ||
                !"professor".equals(session.getAttribute("tipoUsuario"))) {
            return false;
        }

        Object usuario = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        if (!(usuario instanceof loginProfessorDTO) || !(emailSessao instanceof String)) {
            return false;
        }

        String emailProfessor = ((loginProfessorDTO) usuario).getEmail();

        if (emailProfessor == null || emailProfessor.isBlank()) {
            return false;
        }

        return emailProfessor.trim().equalsIgnoreCase(emailAdmin.trim()) &&
                emailProfessor.trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}