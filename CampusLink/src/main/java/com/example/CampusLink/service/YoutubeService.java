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

@Service
public class YoutubeService {

    private final RestClient restClient;
    private final String apiKey;

    public YoutubeService(
            RestClient youtubeRestClient,
            @Value("${youtube.api.key}") String apiKey) {

        this.restClient = youtubeRestClient;
        this.apiKey = apiKey;
    }

    public List<YoutubeVideoDTO> buscarVideos(String termo) {

        SearchResponse busca = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("part", "snippet")
                        .queryParam("type", "video")
                        .queryParam("maxResults", 10)
                        .queryParam("q", termo)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(SearchResponse.class);

        if (busca == null || busca.items == null || busca.items.isEmpty()) {
            return List.of();
        }

        List<String> ids = new ArrayList<>();
        for (SearchItem item : busca.items) {
            ids.add(item.id.videoId);
        }

        VideosResponse detalhes = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/videos")
                        .queryParam("part", "contentDetails")
                        .queryParam("id", String.join(",", ids))
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(VideosResponse.class);

        Map<String, String> duracaoPorId = new HashMap<>();
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

        return resultado;
    }

    private String formatarDuracao(String isoDuration) {

        if (isoDuration == null) {
            return "";
        }

        Duration duracao = Duration.parse(isoDuration);

        long horas = duracao.toHours();
        long minutos = duracao.toMinutesPart();
        long segundos = duracao.toSecondsPart();

        if (horas > 0) {
            return String.format("%d:%02d:%02d", horas, minutos, segundos);
        }
        return String.format("%d:%02d", minutos, segundos);
    }

    // Classes internas só pra mapear a resposta JSON da API do YouTube.
    // Não são usadas fora deste service.

    private static class SearchResponse {
        public List<SearchItem> items;
    }

    private static class SearchItem {
        public Id id;
        public Snippet snippet;
    }

    private static class Id {
        public String videoId;
    }

    private static class Snippet {
        public String title;
        public String channelTitle;
        public Thumbnails thumbnails;
    }

    private static class Thumbnails {
        public Thumbnail medium;
    }

    private static class Thumbnail {
        public String url;
    }

    private static class VideosResponse {
        public List<VideoItem> items;
    }

    private static class VideoItem {
        public String id;
        public ContentDetails contentDetails;
    }

    private static class ContentDetails {
        public String duration;
    }
}
