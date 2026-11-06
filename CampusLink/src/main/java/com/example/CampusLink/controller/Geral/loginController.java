// escolha de login apresenta as formas de entrada no campuslink
package com.example.CampusLink.controller.Geral;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// entrada por perfil esta classe abre a selecao de conta quando nao existe usuario conectado
@Controller
public class loginController {

    // abrir escolha de login este metodo mostra os perfis para quem ainda nao entrou
    @GetMapping("/login")
    public String login(HttpSession session) {
        // evitar novo acesso retorna ao inicio quando o usuario ja esta conectado
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        return "Geral/login";
    }
}
