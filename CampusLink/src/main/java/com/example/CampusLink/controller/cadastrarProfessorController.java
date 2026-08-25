package com.example.CampusLink.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class cadastrarProfessorController {
    
    @GetMapping("/cadastrarProfessor")
    public String CadastrarProfessor() {
        return "cadastrarProfessor";
    }
}
