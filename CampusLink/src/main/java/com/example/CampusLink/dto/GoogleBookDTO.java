package com.example.CampusLink.dto;

import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class GoogleBookDTO {

    @Schema(
            description = "Título informado pelo Google Books. Pode ser nulo quando ausente.",
            types = {"string", "null"},
            example = "Engenharia de Software - exemplo didático"
    )
    private String titulo;
    @Schema(
            description = "Autores separados por vírgulas. Quando não informados, retorna Autor desconhecido.",
            example = "Ana Exemplo, Bruno Exemplo"
    )
    private String autores;
    @Schema(
            description = "Editora informada pelo Google Books. Pode ser nula.",
            types = {"string", "null"},
            example = "Editora Exemplo"
    )
    private String editora;
    @Schema(
            description = "Quatro primeiros caracteres da data de publicação. Retorna texto vazio quando a data está ausente ou tem menos de quatro caracteres.",
            example = "2024"
    )
    private String anoPublicacao;
    @Schema(
            description = "Prioriza ISBN-13 e usa ISBN-10 como alternativa. Sem esses identificadores, retorna texto vazio; o valor fornecido também pode ser nulo.",
            types = {"string", "null"},
            example = "9780000000002"
    )
    private String isbn;
    @Schema(
            description = "URL da miniatura da capa. Pode ser nula.",
            types = {"string", "null"},
            format = "uri",
            example = "https://books.google.com/books/content?id=EXEMPLO_LIVRO&printsec=frontcover&img=1&zoom=1"
    )
    private String thumbnailUrl;
    @Schema(
            description = "Link de informações do livro, utilizado no formulário de conteúdo. Pode ser nulo.",
            types = {"string", "null"},
            format = "uri",
            example = "https://books.google.com/books?id=EXEMPLO_LIVRO"
    )
    private String linkGoogleBooks;
}
