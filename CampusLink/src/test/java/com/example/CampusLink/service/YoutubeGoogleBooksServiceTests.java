// verificacao das buscas no youtube e no google books confere paginacao duracao dos videos dados dos livros e novas tentativas
package com.example.CampusLink.service;

import com.example.CampusLink.dto.GoogleBookDTO;
import com.example.CampusLink.dto.YoutubeVideoDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// testes das buscas externas esta classe simula as apis do youtube e do google books sem acessar a internet
class YoutubeGoogleBooksServiceTests {

    private MockRestServiceServer youtubeApi;
    private MockRestServiceServer livrosApi;
    private YoutubeService youtube;
    private GoogleBooksService livros;

    // preparar testes das buscas este metodo liga cada servico a um servidor simulado
    @BeforeEach
    void preparar() {
        RestClient.Builder clienteYoutube = RestClient.builder().baseUrl("http://youtube.teste");
        youtubeApi = MockRestServiceServer.bindTo(clienteYoutube).build();
        youtube = new YoutubeService(clienteYoutube.build(), "chave");
        RestClient.Builder clienteLivros = RestClient.builder().baseUrl("http://livros.teste");
        livrosApi = MockRestServiceServer.bindTo(clienteLivros).build();
        livros = new GoogleBooksService(clienteLivros.build(), "chave");
    }

    // conferir chamadas este metodo garante que todas as chamadas esperadas as apis aconteceram
    @AfterEach
    void conferir() {
        youtubeApi.verify();
        livrosApi.verify();
    }

    // testar busca de videos este teste confere a juncao da busca com os detalhes e a duracao formatada
    @Test
    void buscaVideosComDuracaoFormatada() {
        // preparar busca com dois videos e detalhes com uma hora e quatro minutos
        youtubeApi.expect(requestTo(containsString("/search")))
                .andExpect(queryParam("pageToken", "pagina2"))
                .andExpect(queryParam("q", "derivadas"))
                .andRespond(json("""
                        {"nextPageToken":"pagina3","items":[
                          {"id":{"videoId":"a1"},"snippet":{"title":"Aula 1","channelTitle":"Canal","thumbnails":{"medium":{"url":"http://img/1"}}}},
                          {"id":{"videoId":"b2"},"snippet":{"title":"Aula 2","channelTitle":"Canal","thumbnails":{"medium":{"url":"http://img/2"}}}}]}
                        """));
        youtubeApi.expect(requestTo(containsString("/videos")))
                .andExpect(queryParam("id", "a1,b2"))
                .andRespond(json("""
                        {"items":[{"id":"a1","contentDetails":{"duration":"PT1H2M3S"}},{"id":"b2","contentDetails":{"duration":"PT4M5S"}}]}
                        """));

        // executar busca da segunda pagina
        YoutubeService.PaginaVideos pagina = youtube.buscarVideos("derivadas", "pagina2");

        // verificar videos duracoes e proxima pagina
        assertEquals("1:02:03", pagina.videos().get(0).getDuracao());
        assertEquals("4:05", pagina.videos().get(1).getDuracao());
        assertEquals("http://img/1", pagina.videos().get(0).getThumbnailUrl());
        assertEquals("pagina3", pagina.nextPageToken());
    }

    // testar busca sem videos este teste confere que a api de detalhes nao e chamada sem resultados
    @Test
    void buscaSemVideosNaoConsultaDetalhes() {
        // preparar busca vazia
        youtubeApi.expect(requestTo(containsString("/search"))).andRespond(json("{\"items\":[]}"));

        // executar busca
        List<YoutubeVideoDTO> videos = youtube.buscarVideos("nada");

        // verificar lista vazia
        assertTrue(videos.isEmpty());
    }

    // testar detalhes dos videos este teste confere que videos incompletos sao ignorados e lista vazia nao chama a api
    @Test
    void buscaDetalhesIgnorandoVideosIncompletos() {
        // preparar um video completo e um sem detalhes
        youtubeApi.expect(requestTo(containsString("/videos")))
                .andRespond(json("""
                        {"items":[{"id":"a1","snippet":{"title":"Aula","channelTitle":"Canal"},"contentDetails":{"duration":"PT30S"}},{"id":"b2"}]}
                        """));
        youtubeApi.expect(requestTo(containsString("/videos"))).andRespond(json("{}"));

        // executar consultas
        List<YoutubeVideoDTO> videos = youtube.buscarDetalhesVideos(List.of("a1", "b2"));
        List<YoutubeVideoDTO> semResposta = youtube.buscarDetalhesVideos(List.of("c3"));
        List<YoutubeVideoDTO> semIds = youtube.buscarDetalhesVideos(List.of());

        // verificar apenas o video completo
        assertEquals(1, videos.size());
        assertEquals("0:30", videos.get(0).getDuracao());
        assertTrue(semResposta.isEmpty());
        assertTrue(semIds.isEmpty());
    }

    // testar busca de livros este teste confere autores ano isbn preferido e indice da proxima pagina
    @Test
    void buscaLivrosComDadosEPaginacao() {
        // preparar dois livros de um total de vinte e cinco
        livrosApi.expect(requestTo(containsString("/volumes")))
                .andExpect(queryParam("startIndex", "10"))
                .andRespond(json("""
                        {"totalItems":25,"items":[
                          {"volumeInfo":{"title":"Calculo","authors":["Stewart","Thomas"],"publishedDate":"2013-05-01",
                            "industryIdentifiers":[{"type":"ISBN_10","identifier":"111"},{"type":"ISBN_13","identifier":"9781"}],
                            "imageLinks":{"thumbnail":"http://capa"},"infoLink":"http://livro"}},
                          {"volumeInfo":{"title":"Sem autor","publishedDate":"20","industryIdentifiers":[{"type":"ISBN_10","identifier":"222"}]}},
                          {}]}
                        """));

        // executar busca a partir do decimo livro
        GoogleBooksService.PaginaLivros pagina = livros.buscarLivros("calculo", 10);

        // verificar dados dos livros e proxima pagina
        GoogleBookDTO primeiro = pagina.livros().get(0);
        assertEquals("Stewart, Thomas", primeiro.getAutores());
        assertEquals("2013", primeiro.getAnoPublicacao());
        assertEquals("9781", primeiro.getIsbn());
        assertEquals("http://capa", primeiro.getThumbnailUrl());
        assertEquals("Autor desconhecido", pagina.livros().get(1).getAutores());
        assertEquals("", pagina.livros().get(1).getAnoPublicacao());
        assertEquals("222", pagina.livros().get(1).getIsbn());
        assertEquals(2, pagina.livros().size());
        assertEquals(13, pagina.nextStartIndex());
    }

    // testar ultima pagina este teste confere que nao existe proxima pagina no fim da lista ou sem resultados
    @Test
    void ultimaPaginaNaoTemProximoIndice() {
        // preparar ultima pagina e resposta vazia
        livrosApi.expect(requestTo(containsString("/volumes")))
                .andRespond(json("{\"totalItems\":1,\"items\":[{\"volumeInfo\":{\"title\":\"Unico\"}}]}"));
        livrosApi.expect(requestTo(containsString("/volumes"))).andRespond(json("{\"totalItems\":0}"));

        // executar buscas
        GoogleBooksService.PaginaLivros ultima = livros.buscarLivros("unico", 0);
        List<GoogleBookDTO> vazia = livros.buscarLivros("nada");

        // verificar fim da paginacao e isbn vazio
        assertNull(ultima.nextStartIndex());
        assertEquals("", ultima.livros().get(0).getIsbn());
        assertTrue(vazia.isEmpty());
    }

    // testar api instavel este teste confere nova tentativa quando o google books responde erro temporario
    @Test
    void tentaDeNovoQuandoApiEstaInstavel() {
        // preparar uma falha temporaria seguida do livro
        livrosApi.expect(requestTo(containsString("/volumes/abc"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        livrosApi.expect(requestTo(containsString("/volumes/abc")))
                .andRespond(json("{\"volumeInfo\":{\"title\":\"Fisica\",\"authors\":[\"Halliday\"]}}"));

        // executar busca dos detalhes
        GoogleBookDTO livro = livros.buscarDetalhesLivro("abc");

        // verificar livro obtido na segunda tentativa
        assertEquals("Fisica", livro.getTitulo());
    }

    // testar erros da api este teste confere que erro definitivo nao repete e que tres falhas temporarias desistem
    @Test
    void desisteEmErroDefinitivoOuAposTresTentativas() {
        // preparar livro inexistente e api fora do ar
        livrosApi.expect(requestTo(containsString("/volumes/nao-existe"))).andRespond(withStatus(HttpStatus.NOT_FOUND));
        livrosApi.expect(times(3), requestTo(containsString("/volumes/fora"))).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        livrosApi.expect(requestTo(containsString("/volumes/vazio"))).andRespond(json("{}"));

        // executar buscas
        RestClientResponseException inexistente = assertThrows(RestClientResponseException.class, () -> livros.buscarDetalhesLivro("nao-existe"));
        RestClientResponseException foraDoAr = assertThrows(RestClientResponseException.class, () -> livros.buscarDetalhesLivro("fora"));
        GoogleBookDTO vazio = livros.buscarDetalhesLivro("vazio");

        // verificar codigos e livro sem dados
        assertEquals(404, inexistente.getStatusCode().value());
        assertEquals(502, foraDoAr.getStatusCode().value());
        assertNull(vazio);
    }

    // montar resposta json este metodo cria a resposta de sucesso da api simulada
    private org.springframework.test.web.client.ResponseCreator json(String corpo) {
        return withSuccess(corpo, MediaType.APPLICATION_JSON);
    }
}
