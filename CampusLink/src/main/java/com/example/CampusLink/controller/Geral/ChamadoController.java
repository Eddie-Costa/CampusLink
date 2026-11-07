// canal de ajuda recebe solicitacoes e mensagens dos usuarios

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

// atendimento do titular esta classe confere a sessao e organiza abertura consulta e resposta dos chamados
@Controller
public class ChamadoController {

    private final ChamadoService chamadoService;
    private final ChamadoAcessoService acessoService;
    private final emailService emails;

    // preparar atendimento este construtor recebe os servicos de chamados acesso e email
    public ChamadoController(ChamadoService chamadoService, ChamadoAcessoService acessoService, emailService emails) {
        this.chamadoService = chamadoService;
        this.acessoService = acessoService;
        this.emails = emails;
    }

    // limitar cadastro de chamado este metodo aceita apenas assunto e descricao do formulario
    @InitBinder("chamado")
    public void limitarCampos(WebDataBinder binder) {
        binder.setAllowedFields("assunto", "descricao");
    }

    // limitar resposta de chamado este metodo aceita apenas o texto da resposta do formulario
    @InitBinder("resposta")
    public void limitarMensagem(WebDataBinder binder) {
        binder.setAllowedFields("resposta");
    }

    // abrir formulario de ajuda este metodo prepara um chamado vazio para o usuario conectado
    @GetMapping({"/ajuda", "/ajuda/chamado"})
    public String formulario(HttpSession session, Model model) {
        // proteger formulario de chamado exige uma sessao reconhecida antes de abrir o atendimento
        if (acessoService.usuarioDaSessao(session) == null) return "redirect:/login";
        model.addAttribute("chamado", new ChamadoDTO());
        return "Geral/abrirChamado";
    }

    // cadastrar chamado pela tela este metodo salva o pedido de ajuda e envia a confirmacao
    @PostMapping({"/ajuda", "/ajuda/chamado"})
    public String abrir(@Valid @ModelAttribute("chamado") ChamadoDTO formulario, BindingResult erros,
                        HttpSession session, RedirectAttributes redirectAttributes) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        // proteger abertura de chamado exige login antes de salvar a solicitacao
        if (usuario == null) return "redirect:/login";
        // validar formulario de chamado reapresenta os campos quando existem erros
        if (erros.hasErrors()) return "Geral/abrirChamado";

        ChamadoDTO chamado = chamadoService.abrir(usuario, formulario);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Chamado #" + chamado.getId() + " registrado com sucesso.");


        // avisar falha de email informa que o chamado foi salvo mesmo sem a confirmacao por email
        if (!emails.enviarEmailChamado(chamado)) {
            redirectAttributes.addFlashAttribute("mensagemAviso",
                    "Seu chamado foi salvo, mas não foi possível enviar o e-mail de confirmação. Acompanhe por esta página.");
        }
        return "redirect:/meus-chamados/" + chamado.getId();
    }

    // listar meus chamados este metodo mostra os pedidos de ajuda do usuario conectado
    @GetMapping("/meus-chamados")
    public String listar(HttpSession session, Model model) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        // proteger lista de chamados exige login para consultar os atendimentos
        if (usuario == null) return "redirect:/login";
        model.addAttribute("chamados", chamadoService.listarDoUsuario(usuario));
        return "Geral/meusChamados";
    }

    // buscar detalhes do chamado este metodo abre o pedido do usuario com o campo de resposta
    @GetMapping("/meus-chamados/{id}")
    public String detalhes(@PathVariable Long id, HttpSession session, Model model) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        // proteger detalhes de chamado exige login para abrir a conversa
        if (usuario == null) return "redirect:/login";
        model.addAttribute("chamado", chamadoService.buscarDoUsuario(id, usuario));
        model.addAttribute("administrador", false);
        model.addAttribute("resposta", new ChamadoRespostaDTO());
        return "Geral/detalheChamado";
    }

    // responder chamado pelo formulario este metodo valida e salva a mensagem do usuario
    @PostMapping("/meus-chamados/{id}/responder")
    public String responder(@PathVariable Long id, @Valid @ModelAttribute("resposta") ChamadoRespostaDTO formulario,
                            BindingResult erros, HttpSession session, Model model) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        // proteger resposta de chamado exige login antes de responder
        if (usuario == null) return "redirect:/login";
        // aceitar resposta valida tenta enviar a mensagem apenas quando o formulario nao possui erros
        if (!erros.hasErrors()) {
            // responder chamado salva a mensagem ou mostra o motivo que impediu o envio
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

    // consultar conversa do usuario este metodo devolve o chamado atualizado sem guardar copia no navegador
    @GetMapping("/meus-chamados/{id}/conversa")
    @ResponseBody
    public ResponseEntity<ChamadoDTO> conversa(@PathVariable Long id, HttpSession session) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        // proteger consulta da conversa recusa o acesso sem usuario autenticado
        if (usuario == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chamadoService.buscarDoUsuario(id, usuario));
    }

    // enviar mensagem do usuario este metodo salva a resposta e devolve a conversa atualizada
    @PostMapping("/meus-chamados/{id}/mensagens")
    @ResponseBody
    public ResponseEntity<ChamadoDTO> enviarMensagem(@PathVariable Long id, @RequestParam String resposta, HttpSession session) {
        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        // proteger envio da conversa recusa mensagens de uma sessao sem usuario reconhecido
        if (usuario == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        chamadoService.enviarMensagemUsuario(id, usuario, resposta);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chamadoService.buscarDoUsuario(id, usuario));
    }
}
