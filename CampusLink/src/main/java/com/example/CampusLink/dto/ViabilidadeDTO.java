package com.example.CampusLink.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ViabilidadeDTO {

    private Long idAluno;

    private Long idTurma;

    private Long idEvento;

    private String nomeEvento;

    private LocalDateTime dataEvento;

    private Long idConteudo;

    private String tituloConteudo;

    private String nivel;

    private int cargaMin;

    private int capacidadeMin;

    private String motivo;
}
