package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.config.SecurityConfig;
import com.example.CampusLink.dto.YoutubeVideoDTO;
import com.example.CampusLink.service.YoutubeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(YoutubeController.class)
@Import(SecurityConfig.class)
class YoutubeEndpointIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private YoutubeService youtubeService;

    @Test
    void buscaVideosComSucessoRetornaStatusEJson() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", new Object());
        YoutubeVideoDTO video = new YoutubeVideoDTO();
        video.setVideoId("video-123");
        video.setTitulo("Aula de engenharia de software");
        video.setCanal("CampusLink");
        video.setDuracao("12:34");
        when(youtubeService.buscarVideos("engenharia de software")).thenReturn(List.of(video));

        mvc.perform(get("/youtube/buscar")
                        .param("q", "engenharia de software")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].videoId").value("video-123"))
                .andExpect(jsonPath("$[0].titulo").value("Aula de engenharia de software"))
                .andExpect(jsonPath("$[0].canal").value("CampusLink"))
                .andExpect(jsonPath("$[0].duracao").value("12:34"));
    }

    @Test
    void buscaSemParametroObrigatorioRetornaErro400ComCorpo() throws Exception {
        mvc.perform(get("/youtube/buscar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parâmetro obrigatório ausente."))
                .andExpect(jsonPath("$.parameter").value("q"));
    }
}
