// escolha de cadastro apresenta os tipos de conta disponiveis
package com.example.CampusLink.controller.Geral;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// entrada do cadastro esta classe direciona usuarios conectados e abre a escolha de perfil
@Controller
public class cadastrarController {

    // abrir escolha de cadastro este metodo mostra os perfis disponiveis para quem nao entrou
    @GetMapping("/cadastrar")
    public String Cadastrar(HttpSession session) {
        // evitar cadastro durante acesso encaminha quem ja esta conectado para a pagina inicial
        if (session.getAttribute("usuarioLogado") != null) {
            return "redirect:/home";
        }

        return "Geral/cadastrar";
    }

}
