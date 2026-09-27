package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.YoutubeVideoDTO;
import com.example.CampusLink.service.YoutubeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class YoutubeController {

    private final YoutubeService youtubeService;

    public YoutubeController(YoutubeService youtubeService) {
        this.youtubeService = youtubeService;
    }

    @GetMapping("/youtube/buscar")
    @ResponseBody
    public List<YoutubeVideoDTO> buscar(
            @RequestParam("q") String termo,
            HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {
            return List.of();
        }

        return youtubeService.buscarVideos(termo);
    }
}
