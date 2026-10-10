package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.YoutubeVideoDTO;
import com.example.CampusLink.service.YoutubeService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class YoutubeControllerTests {

    @Test
    void visitanteRecebeListaVaziaSemConsultarYoutube() {
        YoutubeService youtubeService = mock(YoutubeService.class);
        YoutubeController controller = new YoutubeController(youtubeService);
        HttpSession session = mock(HttpSession.class);

        assertTrue(controller.buscar("engenharia de software", session).isEmpty());

        verifyNoInteractions(youtubeService);
    }

    @Test
    void usuarioLogadoBuscaVideosERecebeResultadosDoServico() {
        YoutubeService youtubeService = mock(YoutubeService.class);
        YoutubeController controller = new YoutubeController(youtubeService);
        HttpSession session = mock(HttpSession.class);
        YoutubeVideoDTO video = new YoutubeVideoDTO();
        video.setVideoId("video-123");
        video.setTitulo("Aula de cálculo");
        List<YoutubeVideoDTO> resultados = List.of(video);

        when(session.getAttribute("usuarioLogado")).thenReturn(new Object());
        when(youtubeService.buscarVideos("engenharia de software")).thenReturn(resultados);

        assertEquals(resultados, controller.buscar("engenharia de software", session));

        verify(youtubeService).buscarVideos("engenharia de software");
    }
}