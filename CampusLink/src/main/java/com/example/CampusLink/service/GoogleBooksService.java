package com.example.CampusLink.service;

import com.example.CampusLink.dto.GoogleBookDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Service
public class GoogleBooksService {

    private static final int MAX_TENTATIVAS = 3;
    private final RestClient restClient;
    private final String apiKey;

    public GoogleBooksService(
            RestClient googleBooksRestClient,
            @Value("${googlebooks.api.key}") String apiKey) {

        this.restClient = googleBooksRestClient;
        this.apiKey = apiKey;
    }

    public List<GoogleBookDTO> buscarLivros(String termo) {
        return buscarLivros(termo, 0).livros();
    }

    public PaginaLivros buscarLivros(String termo, int startIndex) {
        VolumesResponse resposta = buscarVolumes(termo, startIndex);

        if (resposta == null || resposta.items == null) {
            return new PaginaLivros(List.of(), null);
        }

        List<GoogleBookDTO> resultado = new ArrayList<>();

        for (VolumeItem item : resposta.items) {

            if (item.volumeInfo == null) {
                continue;
            }

            resultado.add(converterLivro(item.volumeInfo));
        }

        int proximoIndice = startIndex + resposta.items.size();
        Integer proximoStartIndex = !resposta.items.isEmpty()
                && resposta.totalItems != null
                && proximoIndice < resposta.totalItems
                ? proximoIndice
                : null;
        return new PaginaLivros(resultado, proximoStartIndex);
    }

    public GoogleBookDTO buscarDetalhesLivro(String volumeId) {
        VolumeDetails resposta = repetirSeIndisponivel(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/volumes/{volumeId}")
                        .queryParam("key", apiKey)
                        .build(volumeId))
                .retrieve()
                .body(VolumeDetails.class));

        return resposta == null || resposta.volumeInfo == null
                ? null
                : converterLivro(resposta.volumeInfo);
    }

    private VolumesResponse buscarVolumes(String termo, int startIndex) {
        return repetirSeIndisponivel(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/volumes")
                        .queryParam("q", termo)
                        .queryParam("maxResults", 10)
                        .queryParam("startIndex", startIndex)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(VolumesResponse.class));
    }

    private <T> T repetirSeIndisponivel(Supplier<T> requisicao) {
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                return requisicao.get();
            } catch (RestClientResponseException e) {
                if (!e.getStatusCode().is5xxServerError() || tentativa == MAX_TENTATIVAS) {
                    throw e;
                }

                try {
                    Thread.sleep(250L * tentativa);
                } catch (InterruptedException interrupcao) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Busca no Google Books interrompida.", interrupcao);
                }
            }
        }

        throw new IllegalStateException("Não foi possível concluir a busca no Google Books.");
    }

    private GoogleBookDTO converterLivro(VolumeInfo volumeInfo) {
        GoogleBookDTO livro = new GoogleBookDTO();
        livro.setTitulo(volumeInfo.title);
        livro.setAutores(formatarAutores(volumeInfo.authors));
        livro.setEditora(volumeInfo.publisher);
        livro.setAnoPublicacao(extrairAno(volumeInfo.publishedDate));
        livro.setIsbn(extrairIsbn(volumeInfo.industryIdentifiers));

        if (volumeInfo.imageLinks != null) {
            livro.setThumbnailUrl(volumeInfo.imageLinks.thumbnail);
        }

        livro.setLinkGoogleBooks(volumeInfo.infoLink);
        return livro;
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
        public Integer totalItems;
    }

    private static class VolumeItem {
        public VolumeInfo volumeInfo;
    }

    private static class VolumeDetails {
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

    public record PaginaLivros(List<GoogleBookDTO> livros, Integer nextStartIndex) {
    }
}