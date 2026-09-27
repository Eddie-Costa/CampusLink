package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.GoogleBookDTO;
import com.example.CampusLink.service.GoogleBooksService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GoogleBooksController {

    private final GoogleBooksService googleBooksService;

    public GoogleBooksController(GoogleBooksService googleBooksService) {
        this.googleBooksService = googleBooksService;
    }

    @GetMapping("/googlebooks/buscar")
    @ResponseBody
    public List<GoogleBookDTO> buscar(
            @RequestParam("q") String termo,
            HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {
            return List.of();
        }

        return googleBooksService.buscarLivros(termo);
    }
}
