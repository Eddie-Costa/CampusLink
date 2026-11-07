// liberacao de sites recebe pedidos de novos dominios feitos pelos professores
package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.dto.DominioDTO;
import com.example.CampusLink.service.DominioService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// controle dos pedidos de dominio esta classe valida o formulario e identifica o professor conectado
@Controller
public class ProfessorDominiosController {

    private static final Logger logger = LoggerFactory.getLogger(ProfessorDominiosController.class);

    @Autowired
    private DominioService dominioService;

    @Autowired
    private ProfessorRepository professorRepository;

    // solicitar dominio pela turma este metodo valida o formulario e envia o pedido do professor
    @PostMapping("/turmas/{idTurma}/dominios")
    public String solicitar(@PathVariable Long idTurma,
                            @Valid @ModelAttribute("solicitacao") DominioDTO solicitacao,
                            BindingResult result,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {

        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        // restringir solicitacao de dominio recusa o envio por perfis diferentes de professor
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/turmas/" + idTurma;
        }

        // validar pedido de dominio mostra o primeiro erro de preenchimento antes do envio
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("mensagemErro",
                    result.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/turmas/" + idTurma;
        }

        // enviar pedido de dominio identifica o professor e trata dados invalidos ou falhas de gravacao
        try {
            Long idProfessor = identificarProfessor(session);

            // exigir professor solicitante retorna ao inicio quando a conta nao foi identificada
            if (idProfessor == null) {
                return "redirect:/home";
            }

            dominioService.solicitar(solicitacao, idProfessor);
            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Solicitação enviada. Aguarde a análise do administrador.");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());

        } catch (RuntimeException e) {
            logger.error("erro ao solicitar domínio", e);
            redirectAttributes.addFlashAttribute("mensagemErro",
                    "Não foi possível enviar a solicitação. Tente novamente.");
        }

        return "redirect:/turmas/" + idTurma;
    }

    // buscar professor do pedido de dominio este metodo consulta o identificador pelo email conectado
    private Long identificarProfessor(HttpSession session) {

        String email = (String) session.getAttribute("email2FA");

        // exigir email do solicitante encerra a identificacao quando o email esta vazio
        if (email == null || email.isBlank()) {
            return null;
        }

        // buscar professor solicitante consulta e converte o codigo e retorna vazio em caso de falha
        try {
            String idTexto = professorRepository.buscarPorIDProfessor(email);

            // conferir codigo do solicitante evita converter um codigo ausente ou vazio
            if (idTexto == null || idTexto.isBlank()) {
                return null;
            }

            return Long.parseLong(idTexto);

        } catch (Exception e) {
            logger.error("erro ao identificar professor", e);
            return null;
        }
    }
}