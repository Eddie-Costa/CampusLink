package com.example.CampusLink.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class YoutubeVideoDTO {

    private String videoId;
    private String titulo;
    private String canal;
    private String thumbnailUrl;
    private String duracao;
}