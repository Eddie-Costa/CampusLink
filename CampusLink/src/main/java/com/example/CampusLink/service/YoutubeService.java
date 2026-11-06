// catalogo de videos consulta o youtube para apoiar a selecao de materiais
package com.example.CampusLink.service;

import com.example.CampusLink.dto.YoutubeVideoDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// busca audiovisual esta classe organiza resultados paginas e duracoes dos videos encontrados
@Service
public class YoutubeService {

    private final RestClient restClient;
    private final String apiKey;

    // preparar busca externa de videos este construtor recebe a conexao e a chave de acesso
    public YoutubeService(
            RestClient youtubeRestClient,
            @Value("${youtube.api.key}") String apiKey) {

        this.restClient = youtubeRestClient;
        this.apiKey = apiKey;
    }

    // buscar primeira pagina de videos este metodo consulta os resultados iniciais do termo informado
    public List<YoutubeVideoDTO> buscarVideos(String termo) {
        return buscarVideos(termo, null).videos();
    }

    // buscar pagina externa de videos este metodo consulta titulos canais capas duracoes e a proxima pagina
    public PaginaVideos buscarVideos(String termo, String pageToken) {
        SearchResponse busca = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("part", "snippet")
                        .queryParam("type", "video")
                        .queryParam("maxResults", 10)
                        .queryParam("q", termo)
                        .queryParamIfPresent("pageToken", java.util.Optional.ofNullable(pageToken))
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(SearchResponse.class);

        // buscar videos retorna uma pagina vazia quando nao existem resultados
        if (busca == null || busca.items == null || busca.items.isEmpty()) {
            return new PaginaVideos(List.of(), null);
        }

        List<String> ids = new ArrayList<>();
        for (SearchItem item : busca.items) {
            ids.add(item.id.videoId);
        }

        VideosResponse detalhes = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/videos")
                        .queryParam("part", "snippet,contentDetails")
                        .queryParam("id", String.join(",", ids))
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(VideosResponse.class);

        Map<String, String> duracaoPorId = new HashMap<>();
        // reunir duracoes dos videos guarda as duracoes quando a consulta retorna detalhes
        if (detalhes != null && detalhes.items != null) {
            for (VideoItem item : detalhes.items) {
                duracaoPorId.put(item.id, formatarDuracao(item.contentDetails.duration));
            }
        }

        List<YoutubeVideoDTO> resultado = new ArrayList<>();
        for (SearchItem item : busca.items) {
            YoutubeVideoDTO video = new YoutubeVideoDTO();
            video.setVideoId(item.id.videoId);
            video.setTitulo(item.snippet.title);
            video.setCanal(item.snippet.channelTitle);
            video.setThumbnailUrl(item.snippet.thumbnails.medium.url);
            video.setDuracao(duracaoPorId.get(item.id.videoId));
            resultado.add(video);
        }

        return new PaginaVideos(resultado, busca.nextPageToken);
    }

    // consultar detalhes externos dos videos este metodo recupera titulo canal e duracao dos codigos informados
    public List<YoutubeVideoDTO> buscarDetalhesVideos(List<String> videoIds) {
        // buscar detalhes dos videos ignora uma lista vazia de codigos
        if (videoIds.isEmpty()) {
            return List.of();
        }

        VideosResponse detalhes = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/videos")
                        .queryParam("part", "snippet,contentDetails")
                        .queryParam("id", String.join(",", videoIds))
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(VideosResponse.class);

        // ler detalhes dos videos retorna uma lista vazia quando a resposta nao possui itens
        if (detalhes == null || detalhes.items == null) {
            return List.of();
        }

        List<YoutubeVideoDTO> resultado = new ArrayList<>();
        for (VideoItem item : detalhes.items) {
            // montar video ignora resultados sem dados basicos ou duracao
            if (item.snippet == null || item.contentDetails == null) {
                continue;
            }

            YoutubeVideoDTO video = new YoutubeVideoDTO();
            video.setVideoId(item.id);
            video.setTitulo(item.snippet.title);
            video.setCanal(item.snippet.channelTitle);
            video.setDuracao(formatarDuracao(item.contentDetails.duration));
            resultado.add(video);
        }

        return resultado;
    }

    // formatar duracao do video este metodo transforma a duracao recebida em horas minutos e segundos
    private String formatarDuracao(String isoDuration) {

        // formatar duracao do video retorna vazio quando a duracao nao foi informada
        if (isoDuration == null) {
            return "";
        }

        Duration duracao = Duration.parse(isoDuration);

        long horas = duracao.toHours();
        long minutos = duracao.toMinutesPart();
        long segundos = duracao.toSecondsPart();

        // mostrar horas do video inclui as horas quando a duracao ultrapassa uma hora
        if (horas > 0) {
            return String.format("%d:%02d:%02d", horas, minutos, segundos);
        }
        return String.format("%d:%02d", minutos, segundos);
    }




    // resposta da busca de videos esta classe recebe os resultados e o codigo da proxima pagina
    private static class SearchResponse {
        public List<SearchItem> items;
        public String nextPageToken;
    }

    // item da busca de videos esta classe recebe o identificador e a apresentacao do video
    private static class SearchItem {
        public Id id;
        public Snippet snippet;
    }

    // identidade externa do video esta classe recebe o codigo usado para consultar e abrir o video
    private static class Id {
        public String videoId;
    }

    // apresentacao externa do video esta classe recebe titulo canal e miniaturas
    private static class Snippet {
        public String title;
        public String channelTitle;
        public Thumbnails thumbnails;
    }

    // opcoes de capa do video esta classe recebe a miniatura de tamanho medio
    private static class Thumbnails {
        public Thumbnail medium;
    }

    // imagem externa do video esta classe recebe o endereco da miniatura
    private static class Thumbnail {
        public String url;
    }

    // resposta dos detalhes de videos esta classe recebe a lista dos videos consultados
    private static class VideosResponse {
        public List<VideoItem> items;
    }

    // detalhes externos do video esta classe recebe identificador apresentacao e duracao
    private static class VideoItem {
        public String id;
        public Snippet snippet;
        public ContentDetails contentDetails;
    }

    // duracao externa do video esta classe recebe o tempo no formato informado pelo provedor
    private static class ContentDetails {
        public String duration;
    }

    // pagina de videos este registro junta os resultados organizados e o codigo da proxima consulta
    public record PaginaVideos(List<YoutubeVideoDTO> videos, String nextPageToken) {
    }
}
