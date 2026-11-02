
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.ChamadoRespostaDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.ChamadoService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ChamadoController {

    private final ChamadoService chamadoService;
    private final ChamadoAcessoService acessoService;
    private final emailService emails;

    public ChamadoController(ChamadoService chamadoService, ChamadoAcessoService acessoService, emailService emails) {
        this.chamadoService = chamadoService;
        this.acessoService = acessoService;
        this.emails = emails;
    }

    @InitBinder("chamado")
    public void limitarCampos(WebDataBinder binder) {
        binder.setAllowedFields("assunto", "descricao");
    }

    @InitBinder("resposta")
    public void limitarMensagem(WebDataBinder binder) {
        binder.setAllowedFields("resposta");
    }

    @GetMapping({"/ajuda", "/ajuda/chamado"})
    public String formulario(HttpSession session, Model model) {
        if (acessoService.usuarioDaSessao(session) == null) return "redirect:/login";
        model.addAttribute("chamado", new ChamadoDTO());
        return "Geral/abrirChamado";
    }

    @PostMapping({"/ajuda", "/ajuda/chamado"})
    public String abrir(@Valid @ModelAttribute("chamado") ChamadoDTO formulario, BindingResult erros,
                        HttpSession session, RedirectAttributes redirectAttributes) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        if (usuario == null) return "redirect:/login";
        if (erros.hasErrors()) return "Geral/abrirChamado";

        ChamadoDTO chamado = chamadoService.abrir(usuario, formulario);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Chamado #" + chamado.getId() + " registrado com sucesso.");

        // envia somente depois que o service confirmou a gravacao no banco
        if (!emails.enviarEmailChamado(chamado)) {
            redirectAttributes.addFlashAttribute("mensagemAviso",
                    "Seu chamado foi salvo, mas não foi possível enviar o e-mail de confirmação. Acompanhe por esta página.");
        }
        return "redirect:/meus-chamados/" + chamado.getId();
    }

    @GetMapping("/meus-chamados")
    public String listar(HttpSession session, Model model) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        if (usuario == null) return "redirect:/login";
        model.addAttribute("chamados", chamadoService.listarDoUsuario(usuario));
        return "Geral/meusChamados";
    }

    @GetMapping("/meus-chamados/{id}")
    public String detalhes(@PathVariable Long id, HttpSession session, Model model) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        if (usuario == null) return "redirect:/login";
        model.addAttribute("chamado", chamadoService.buscarDoUsuario(id, usuario));
        model.addAttribute("administrador", false);
        model.addAttribute("resposta", new ChamadoRespostaDTO());
        return "Geral/detalheChamado";
    }

    @PostMapping("/meus-chamados/{id}/responder")
    public String responder(@PathVariable Long id, @Valid @ModelAttribute("resposta") ChamadoRespostaDTO formulario,
                            BindingResult erros, HttpSession session, Model model) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        if (usuario == null) return "redirect:/login";
        if (!erros.hasErrors()) {
            try {
                chamadoService.enviarMensagemUsuario(id, usuario, formulario.getResposta());
                return "redirect:/meus-chamados/" + id;
            } catch (IllegalArgumentException | IllegalStateException e) {
                model.addAttribute("mensagemErro", e.getMessage());
            }
        }
        model.addAttribute("chamado", chamadoService.buscarDoUsuario(id, usuario));
        model.addAttribute("administrador", false);
        return "Geral/detalheChamado";
    }

    @GetMapping("/meus-chamados/{id}/conversa")
    @ResponseBody
    public ResponseEntity<ChamadoDTO> conversa(@PathVariable Long id, HttpSession session) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        if (usuario == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chamadoService.buscarDoUsuario(id, usuario));
    }

    @PostMapping("/meus-chamados/{id}/mensagens")
    @ResponseBody
    public ResponseEntity<ChamadoDTO> enviarMensagem(@PathVariable Long id, @RequestParam String resposta, HttpSession session) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        if (usuario == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        chamadoService.enviarMensagemUsuario(id, usuario, resposta);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chamadoService.buscarDoUsuario(id, usuario));
    }
}
