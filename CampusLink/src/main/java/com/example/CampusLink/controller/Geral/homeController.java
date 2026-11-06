// paginas de apresentacao organiza a entrada e as informacoes publicas do site
package com.example.CampusLink.controller.Geral;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

// navegacao publica esta classe abre inicio funcionalidades e apresentacao da equipe
@Controller
public class homeController {

    // abrir endereco inicial este metodo encaminha a raiz do site para a pagina principal
    @GetMapping("/")
    public String redirectHome() {
        return "redirect:/home";
    }

    // abrir pagina principal este metodo mostra a apresentacao do campuslink
    @GetMapping("/home")
    public ModelAndView home() {
        return new ModelAndView("Geral/home");
    }

    // abrir funcionalidades este metodo mostra os recursos apresentados pelo site
    @GetMapping("/funcionalidades")
    public ModelAndView funcionalidades() {
        return new ModelAndView("Geral/funcionalidades");
    }

    // abrir sobre nos este metodo mostra a pagina de apresentacao da equipe
    @GetMapping("/sobre-nos")
    public ModelAndView sobreNos() {
        return new ModelAndView("Geral/sobreNos");
    }
}