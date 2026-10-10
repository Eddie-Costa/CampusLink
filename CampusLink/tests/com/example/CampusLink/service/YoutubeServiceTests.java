package com.example.CampusLink.service;

import com.example.CampusLink.dto.YoutubeVideoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class YoutubeServiceTests {

    @Test
    void buscaVideoEConverteDuracaoISO8601() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://youtube.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("https://youtube.test/search?part=snippet&type=video&maxResults=10&q=engenharia%20de%20software&key=chave-teste"))
                .andRespond(withSuccess("""
                        {"items":[{"id":{"videoId":"video-123"},"snippet":{"title":"Aula de engenharia de software","channelTitle":"CampusLink","thumbnails":{"medium":{"url":"https://img.example/video-123.jpg"}}}}]}
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://youtube.test/videos?part=contentDetails&id=video-123&key=chave-teste"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"video-123","contentDetails":{"duration":"PT12M34S"}}]}
                        """, MediaType.APPLICATION_JSON));
        YoutubeService service = new YoutubeService(builder.build(), "chave-teste");

        List<YoutubeVideoDTO> videos = service.buscarVideos("engenharia de software");

        assertEquals(1, videos.size());
        assertEquals("video-123", videos.getFirst().getVideoId());
        assertEquals("Aula de engenharia de software", videos.getFirst().getTitulo());
        assertEquals("CampusLink", videos.getFirst().getCanal());
        assertEquals("https://img.example/video-123.jpg", videos.getFirst().getThumbnailUrl());
        assertEquals("12:34", videos.getFirst().getDuracao());
        servidor.verify();
    }

    @Test
    void buscaSemResultadosRetornaListaVaziaSemConsultarDuracoes() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://youtube.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("https://youtube.test/search?part=snippet&type=video&maxResults=10&q=engenharia%20de%20software&key=chave-teste"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
        YoutubeService service = new YoutubeService(builder.build(), "chave-teste");

        List<YoutubeVideoDTO> videos = service.buscarVideos("engenharia de software");

        assertTrue(videos.isEmpty());
        servidor.verify();
    }

    @Test
    void erro403DoYoutubeInterrompeBuscaEPropagaExcecao() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://youtube.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("https://youtube.test/search?part=snippet&type=video&maxResults=10&q=engenharia%20de%20software&key=chave-teste"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"quota exceeded\"}"));
        YoutubeService service = new YoutubeService(builder.build(), "chave-teste");

        HttpClientErrorException.Forbidden excecao = assertThrows(
                HttpClientErrorException.Forbidden.class,
                () -> service.buscarVideos("engenharia de software"));

        assertEquals(HttpStatus.FORBIDDEN, excecao.getStatusCode());
        servidor.verify();
    }
}
