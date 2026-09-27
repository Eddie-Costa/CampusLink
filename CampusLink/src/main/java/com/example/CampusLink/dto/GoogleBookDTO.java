package com.example.CampusLink.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleBookDTO {

    private String titulo;
    private String autores;
    private String editora;
    private String anoPublicacao;
    private String isbn;
    private String thumbnailUrl;
    private String linkGoogleBooks;
}
