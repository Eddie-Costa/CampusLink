package com.example.CampusLink.dto;

import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class YoutubeVideoDTO {

    @Schema(
            description = "Identificador utilizado pela interface para montar o link de reprodução do vídeo no YouTube.",
            example = "EXEMPLO1234"
    )
    private String videoId;
    @Schema(
            description = "Título do vídeo retornado pelo YouTube.",
            example = "Introdução à Engenharia de Software - exemplo didático"
    )
    private String titulo;
    @Schema(
            description = "Nome do canal que publicou o vídeo.",
            example = "Canal Acadêmico Exemplo"
    )
    private String canal;
    @Schema(
            description = "URL da miniatura de tamanho médio do vídeo.",
            format = "uri",
            example = "https://i.ytimg.com/vi/EXEMPLO1234/mqdefault.jpg"
    )
    private String thumbnailUrl;
    @Schema(
            description = "Duração convertida de ISO 8601 para M:SS ou H:MM:SS. Pode ser nula quando faltam detalhes do vídeo ou vazia quando a duração recebida é nula.",
            types = {"string", "null"},
            example = "12:34"
    )
    private String duracao;
}