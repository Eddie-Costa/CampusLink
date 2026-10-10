package com.example.CampusLink.service;

import com.example.CampusLink.dto.GoogleBookDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.List;

@Service
public class GoogleBooksService {

    private final RestClient restClient;
    private final String apiKey;

    public GoogleBooksService(
            RestClient googleBooksRestClient,
            @Value("${googlebooks.api.key}") String apiKey) {

        this.restClient = googleBooksRestClient;
        this.apiKey = apiKey;
    }

    public List<GoogleBookDTO> buscarLivros(String termo) {

        VolumesResponse resposta = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/volumes")
                        .queryParam("q", termo)
                        .queryParam("maxResults", 10)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(VolumesResponse.class);

        if (resposta == null || resposta.items == null) {
            return List.of();
        }

        List<GoogleBookDTO> resultado = new ArrayList<>();

        for (VolumeItem item : resposta.items) {

            if (item.volumeInfo == null) {
                continue;
            }

            GoogleBookDTO livro = new GoogleBookDTO();
            livro.setTitulo(item.volumeInfo.title);
            livro.setAutores(formatarAutores(item.volumeInfo.authors));
            livro.setEditora(item.volumeInfo.publisher);
            livro.setAnoPublicacao(extrairAno(item.volumeInfo.publishedDate));
            livro.setIsbn(extrairIsbn(item.volumeInfo.industryIdentifiers));

            if (item.volumeInfo.imageLinks != null) {
                livro.setThumbnailUrl(item.volumeInfo.imageLinks.thumbnail);
            }

            livro.setLinkGoogleBooks(item.volumeInfo.infoLink);

            resultado.add(livro);
        }

        return resultado;
    }

    private String formatarAutores(List<String> autores) {

        if (autores == null || autores.isEmpty()) {
            return "Autor desconhecido";
        }
        return String.join(", ", autores);
    }

    private String extrairAno(String dataPublicacao) {

        if (dataPublicacao == null || dataPublicacao.length() < 4) {
            return "";
        }
        return dataPublicacao.substring(0, 4);
    }

    private String extrairIsbn(List<IndustryIdentifier> identificadores) {

        if (identificadores == null) {
            return "";
        }

        for (IndustryIdentifier id : identificadores) {
            if ("ISBN_13".equals(id.type)) {
                return id.identifier;
            }
        }

        for (IndustryIdentifier id : identificadores) {
            if ("ISBN_10".equals(id.type)) {
                return id.identifier;
            }
        }

        return "";
    }

    // Classes internas só pra mapear a resposta JSON da API do Google Books.

    private static class VolumesResponse {
        public List<VolumeItem> items;
    }

    private static class VolumeItem {
        public VolumeInfo volumeInfo;
    }

    private static class VolumeInfo {
        public String title;
        public List<String> authors;
        public String publisher;
        public String publishedDate;
        public List<IndustryIdentifier> industryIdentifiers;
        public ImageLinks imageLinks;
        public String infoLink;
    }

    private static class IndustryIdentifier {
        public String type;
        public String identifier;
    }

    private static class ImageLinks {
        public String thumbnail;
    }
}