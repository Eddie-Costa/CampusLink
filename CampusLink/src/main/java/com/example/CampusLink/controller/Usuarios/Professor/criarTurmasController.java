package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.dao.professorDAO;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.sql.SQLException;

@Controller
public class criarTurmasController {

    @Autowired
    private professorDAO professorDAO;

    @PostMapping("/criarTurmas")
    public void criarTurmas(HttpSession session) {

    }


}
