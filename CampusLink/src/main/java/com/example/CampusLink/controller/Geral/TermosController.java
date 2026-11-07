package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.config.TermosInterceptor;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.dto.TermoVersaoDTO;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.TermosService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

@Controller
public class TermosController {

    private static final MediaType TEXTO = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final TermosService termosService;
    private final ChamadoAcessoService acessoService;

    public TermosController(TermosService termosService, ChamadoAcessoService acessoService) {
        this.termosService = termosService;
        this.acessoService = acessoService;
    }

    @GetMapping("/termos/aceite")
    public String exibirAceite(HttpSession session, Model model) {
        DadosUsuarioDTO usuario = usuarioQuePrecisaAceitar(session);
        if (usuario == null) {
            return "redirect:/login";
        }
        if (termosService.aceitouVersaoAtual(usuario.getId())) {
            session.setAttribute(TermosInterceptor.SESSAO_TERMOS_ACEITOS, true);
            return "redirect:/home";
        }

        model.addAttribute("termo", termosService.versaoAtual());
        return "Geral/aceiteTermos";
    }

    @PostMapping("/termos/aceite")
    public String aceitar(HttpSession session) {
        DadosUsuarioDTO usuario = usuarioQuePrecisaAceitar(session);
        if (usuario == null) {
            return "redirect:/login";
        }

        termosService.registrarAceite(usuario.getId());
        session.setAttribute(TermosInterceptor.SESSAO_TERMOS_ACEITOS, true);
        session.removeAttribute(TermosInterceptor.SESSAO_TERMOS_PENDENTES);
        return "redirect:/home";
    }

    @PostMapping("/termos/revogar")
    public String revogar(HttpSession session, RedirectAttributes redirectAttributes) {
        DadosUsuarioDTO usuario = usuarioQuePrecisaAceitar(session);
        if (usuario == null) {
            return "redirect:/login";
        }

        termosService.revogarAceite(usuario.getId());
        session.removeAttribute(TermosInterceptor.SESSAO_TERMOS_ACEITOS);
        session.setAttribute(TermosInterceptor.SESSAO_TERMOS_PENDENTES, true);
        redirectAttributes.addFlashAttribute("mensagemAviso",
                "Você revogou seu aceite dos termos. Sua conta e seus dados continuam guardados. "
                        + "Para voltar a usar o CampusLink, aceite os termos novamente.");
        return "redirect:/termos/aceite";
    }

    private DadosUsuarioDTO usuarioQuePrecisaAceitar(HttpSession session) {
        Object tipo = session.getAttribute("tipoUsuario");
        if (!"aluno".equals(tipo) && !"professor".equals(tipo)) {
            return null;
        }
        return acessoService.usuarioDaSessao(session);
    }

    @GetMapping("/termos")
    @ResponseBody
    public ResponseEntity<String> termos() {
        try {
            TermoVersaoDTO termo = termosService.versaoAtual();
            return ResponseEntity.ok()
                    .contentType(TEXTO)
                    .cacheControl(CacheControl.noCache())
                    .header("X-Termos-Versao", termo.versao())
                    .header("X-Termos-Hash", termo.hashSha256())
                    .body(termo.conteudo());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(TEXTO)
                    .body("Os termos estão temporariamente indisponíveis.");
        }
    }
}
