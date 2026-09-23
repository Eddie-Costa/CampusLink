package com.example.CampusLink.controller.Geral;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class homeController {

    @GetMapping("/")
    public String redirectHome() {
        return "redirect:/home";
    }

    @GetMapping("/home")
    public ModelAndView home() {
        return new ModelAndView("Geral/home");
    }

    @GetMapping("/funcionalidades")
    public ModelAndView funcionalidades() {
        return new ModelAndView("Geral/funcionalidades");
    }

    @GetMapping("/sobre-nos")
    public ModelAndView sobreNos() {
        return new ModelAndView("Geral/sobreNos");
    }
}