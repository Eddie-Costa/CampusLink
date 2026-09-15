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
        ModelAndView mv = new ModelAndView("Geral/home");
        return mv;
    }
}
