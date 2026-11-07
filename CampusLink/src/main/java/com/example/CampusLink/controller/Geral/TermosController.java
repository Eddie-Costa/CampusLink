// termos de uso lgpd apresenta o texto vigente e recebe o aceite e a revogacao do usuario
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

// controle dos termos de uso esta classe exibe a tela de aceite registra o aceite e permite revogar a qualquer momento
@Controller
public class TermosController {

    private static final MediaType TEXTO = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final TermosService termosService;
    private final ChamadoAcessoService acessoService;

    // preparar termos de uso este construtor recebe o servico dos termos e a identificacao do usuario da sessao
    public TermosController(TermosService termosService, ChamadoAcessoService acessoService) {
        this.termosService = termosService;
        this.acessoService = acessoService;
    }

    // exibir tela de aceite este metodo mostra o termo vigente para quem ainda nao aceitou a versao atual
    @GetMapping("/termos/aceite")
    public String exibirAceite(HttpSession session, Model model) {
        DadosUsuarioDTO usuario = usuarioQuePrecisaAceitar(session);
        // exigir aluno ou professor conectado retorna ao login quando a sessao nao identifica o usuario
        if (usuario == null) {
            return "redirect:/login";
        }
        // liberar quem ja aceitou marca a sessao e volta ao inicio quando a versao atual ja foi aceita
        if (termosService.aceitouVersaoAtual(usuario.getId())) {
            session.setAttribute(TermosInterceptor.SESSAO_TERMOS_ACEITOS, true);
            return "redirect:/home";
        }

        model.addAttribute("termo", termosService.versaoAtual());
        return "Geral/aceiteTermos";
    }

    // aceitar termos de uso este metodo grava o aceite da versao atual e libera o uso da plataforma
    @PostMapping("/termos/aceite")
    public String aceitar(HttpSession session) {
        DadosUsuarioDTO usuario = usuarioQuePrecisaAceitar(session);
        // exigir aluno ou professor conectado retorna ao login quando a sessao nao identifica o usuario
        if (usuario == null) {
            return "redirect:/login";
        }

        termosService.registrarAceite(usuario.getId());
        session.setAttribute(TermosInterceptor.SESSAO_TERMOS_ACEITOS, true);
        session.removeAttribute(TermosInterceptor.SESSAO_TERMOS_PENDENTES);
        return "redirect:/home";
    }

    // revogar termos de uso este metodo revoga o aceite sem excluir a conta e leva o usuario de volta a tela de aceite
    @PostMapping("/termos/revogar")
    public String revogar(HttpSession session, RedirectAttributes redirectAttributes) {
        DadosUsuarioDTO usuario = usuarioQuePrecisaAceitar(session);
        // exigir aluno ou professor conectado retorna ao login quando a sessao nao identifica o usuario
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

    // identificar usuario dos termos este metodo aceita apenas alunos e professores pois administradores nao aceitam termos
    private DadosUsuarioDTO usuarioQuePrecisaAceitar(HttpSession session) {
        Object tipo = session.getAttribute("tipoUsuario");
        // restringir perfis dos termos recusa administradores e sessoes sem perfil
        if (!"aluno".equals(tipo) && !"professor".equals(tipo)) {
            return null;
        }
        return acessoService.usuarioDaSessao(session);
    }

    // entregar texto dos termos este metodo devolve o termo vigente conferido pelo hash para o modal do rodape
    @GetMapping("/termos")
    @ResponseBody
    public ResponseEntity<String> termos() {
        // buscar termo conferido envia o texto com versao e hash ou avisa indisponibilidade quando o termo nao confere
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
