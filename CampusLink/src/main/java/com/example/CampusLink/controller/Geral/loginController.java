package com.example.CampusLink.controller.Geral;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class loginController {

    @GetMapping("/login")
    public String login(HttpSession session) {
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        return "Geral/login";
    }
}
