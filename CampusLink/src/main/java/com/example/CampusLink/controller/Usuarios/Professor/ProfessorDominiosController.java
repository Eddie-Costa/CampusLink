package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.dao.professorDAO;
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

@Controller
public class ProfessorDominiosController {

    private static final Logger logger = LoggerFactory.getLogger(ProfessorDominiosController.class);

    @Autowired
    private DominioService dominioService;

    @Autowired
    private professorDAO professorDAO;

    @PostMapping("/turmas/{idTurma}/dominios")
    public String solicitar(@PathVariable Long idTurma,
                            @Valid @ModelAttribute("solicitacao") DominioDTO solicitacao,
                            BindingResult result,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/turmas/" + idTurma;
        }

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("mensagemErro",
                    result.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/turmas/" + idTurma;
        }

        try {
            Long idProfessor = identificarProfessor(session);

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

    private Long identificarProfessor(HttpSession session) {

        String email = (String) session.getAttribute("email2FA");

        if (email == null || email.isBlank()) {
            return null;
        }

        try {
            String idTexto = professorDAO.buscarPorIDProfessor(email);

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