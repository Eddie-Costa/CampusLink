package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.ChamadoDTO;
import com.example.CampusLink.dto.ChamadoRespostaDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.ChamadoService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
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
public class AdminChamadosController {

    private final ChamadoService chamadoService;
    private final ChamadoAcessoService acessoService;
    private final emailService emails;

    public AdminChamadosController(ChamadoService chamadoService, ChamadoAcessoService acessoService, emailService emails) {
        this.chamadoService = chamadoService;
        this.acessoService = acessoService;
        this.emails = emails;
    }

    @InitBinder("resposta")
    public void limitarCampos(WebDataBinder binder) {
        binder.setAllowedFields("resposta");
    }

    @GetMapping("/admin/chamados")
    public String listar(@RequestParam(defaultValue = "") String status, HttpSession session, Model model) {
        DadosUsuarioDTO admin = administrador(session);
        if (admin == null) return "redirect:/admin/login";
        model.addAttribute("chamados", chamadoService.listarParaAdmin(admin, status));
        model.addAttribute("statusSelecionado", status);
        return "Usuarios/Admin/chamadosAdmin";
    }

    @GetMapping("/admin/chamados/{id}")
    public String detalhes(@PathVariable Long id, HttpSession session, Model model) {
        DadosUsuarioDTO admin = administrador(session);
        if (admin == null) return "redirect:/admin/login";
        carregarDetalhes(id, admin, model);
        model.addAttribute("resposta", new ChamadoRespostaDTO());
        return "Geral/detalheChamado";
    }

    @PostMapping("/admin/chamados/{id}/responder")
    public String responder(@PathVariable Long id, @Valid @ModelAttribute("resposta") ChamadoRespostaDTO formulario,
                            BindingResult erros, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        DadosUsuarioDTO admin = administrador(session);
        if (admin == null) return "redirect:/admin/login";
        if (erros.hasErrors()) {
            carregarDetalhes(id, admin, model);
            return "Geral/detalheChamado";
        }
        try {
            chamadoService.responder(id, admin, formulario.getResposta());
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Resposta enviada. O usuário já pode consultá-la em Meus chamados.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            carregarDetalhes(id, admin, model);
            model.addAttribute("mensagemErro", e.getMessage());
            return "Geral/detalheChamado";
        }
        return "redirect:/admin/chamados/" + id;
    }

    @PostMapping("/admin/chamados/{id}/concluir")
    public String concluir(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        DadosUsuarioDTO admin = administrador(session);
        if (admin == null) return "redirect:/admin/login";
        try {
            ChamadoDTO chamado = chamadoService.concluir(id, admin);
            if (chamado == null) {
                redirectAttributes.addFlashAttribute("mensagemAviso", "Este chamado já foi concluído.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Chamado concluído com sucesso.");
                if (!emails.enviarEmailChamado(chamado)) {
                    redirectAttributes.addFlashAttribute("mensagemAviso",
                            "O chamado foi concluído, mas o e-mail não pôde ser enviado. A resposta continua disponível para o usuário.");
                }
            }
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/admin/chamados/" + id;
    }

    @GetMapping("/admin/chamados/{id}/conversa")
    @ResponseBody
    public ResponseEntity<ChamadoDTO> conversa(@PathVariable Long id, HttpSession session) {
        DadosUsuarioDTO admin = administrador(session);
        if (admin == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chamadoService.buscarParaAdmin(id, admin));
    }

    @PostMapping("/admin/chamados/{id}/mensagens")
    @ResponseBody
    public ResponseEntity<ChamadoDTO> enviarMensagem(@PathVariable Long id, @RequestParam String resposta, HttpSession session) {
        DadosUsuarioDTO admin = administrador(session);
        if (admin == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        chamadoService.responder(id, admin, resposta);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chamadoService.buscarParaAdmin(id, admin));
    }

    private DadosUsuarioDTO administrador(HttpSession session) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        if (usuario != null && !"ADMINISTRADOR".equals(usuario.getPerfil())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return usuario;
    }

    private void carregarDetalhes(Long id, DadosUsuarioDTO admin, Model model) {
        model.addAttribute("chamado", chamadoService.buscarParaAdmin(id, admin));
        model.addAttribute("administrador", true);
    }
}
