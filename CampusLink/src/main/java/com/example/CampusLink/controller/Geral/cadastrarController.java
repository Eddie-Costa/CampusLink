package com.example.CampusLink.controller.Geral;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class cadastrarController {

    @GetMapping("/cadastrar")
    public String Cadastrar(HttpSession session) {
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        return "Geral/cadastrar";
    }

}
