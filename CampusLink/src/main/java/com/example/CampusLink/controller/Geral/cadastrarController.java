package com.example.CampusLink.controller.Geral;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class cadastrarController {

    @GetMapping("/cadastrar")
    public String Cadastrar() {
        return "Geral/cadastrar";
    }

}
