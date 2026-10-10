package com.example.CampusLink.service;

import com.example.CampusLink.dto.GoogleBookDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.hamcrest.Matchers.containsString;

class GoogleBooksServiceTests {

    @Test
    void buscaLivroEMapeiaDadosBibliograficos() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://books.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(containsString("https://books.test/volumes?")))
                .andExpect(queryParam("q", "engenharia%20de%20software"))
                .andExpect(queryParam("maxResults", "10"))
                .andExpect(queryParam("key", "chave-teste"))
                .andRespond(withSuccess("""
                        {"items":[{"volumeInfo":{"title":"Engenharia de software","authors":["Ana Silva","João Souza"],"publisher":"Editora Acadêmica","publishedDate":"2024-05-12","industryIdentifiers":[{"type":"ISBN_10","identifier":"1234567890"},{"type":"ISBN_13","identifier":"9781234567890"}],"imageLinks":{"thumbnail":"https://img.example/livro.jpg"},"infoLink":"https://books.example/livro"}}]}
                        """, MediaType.APPLICATION_JSON));
        GoogleBooksService service = new GoogleBooksService(builder.build(), "chave-teste");

        List<GoogleBookDTO> livros = service.buscarLivros("engenharia de software");

        assertEquals(1, livros.size());
        GoogleBookDTO livro = livros.getFirst();
        assertEquals("Engenharia de software", livro.getTitulo());
        assertEquals("Ana Silva, João Souza", livro.getAutores());
        assertEquals("Editora Acadêmica", livro.getEditora());
        assertEquals("2024", livro.getAnoPublicacao());
        assertEquals("9781234567890", livro.getIsbn());
        assertEquals("https://img.example/livro.jpg", livro.getThumbnailUrl());
        assertEquals("https://books.example/livro", livro.getLinkGoogleBooks());
        servidor.verify();
    }

    @Test
    void ignoraVolumeSemDadosEAplicaPadroesParaCamposAusentes() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://books.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(containsString("https://books.test/volumes?")))
                .andRespond(withSuccess("""
                        {"items":[{}, {"volumeInfo":{"title":"Livro sem metadados"}}]}
                        """, MediaType.APPLICATION_JSON));
        GoogleBooksService service = new GoogleBooksService(builder.build(), "chave-teste");

        List<GoogleBookDTO> livros = service.buscarLivros("engenharia de software");

        assertEquals(1, livros.size());
        assertEquals("Livro sem metadados", livros.getFirst().getTitulo());
        assertEquals("Autor desconhecido", livros.getFirst().getAutores());
        assertEquals("", livros.getFirst().getAnoPublicacao());
        assertEquals("", livros.getFirst().getIsbn());
        servidor.verify();
    }

    @Test
    void erro403DoGoogleBooksInterrompeBuscaEPropagaExcecao() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://books.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(containsString("https://books.test/volumes?")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"access denied\"}"));
        GoogleBooksService service = new GoogleBooksService(builder.build(), "chave-teste");

        HttpClientErrorException.Forbidden excecao = assertThrows(
                HttpClientErrorException.Forbidden.class,
                () -> service.buscarLivros("engenharia de software"));

        assertEquals(HttpStatus.FORBIDDEN, excecao.getStatusCode());
        servidor.verify();
    }
}
