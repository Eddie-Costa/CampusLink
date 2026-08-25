package com.example.CampusLink.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class cadastrarController {

    @GetMapping("/cadastrar")
    public String Cadastrar() {
        return "cadastrar";
    }

    

}
