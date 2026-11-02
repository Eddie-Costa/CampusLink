package com.example.CampusLink.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class SessaoEstudoDTO {

    private LocalDate data;

    private Long idTurma;

    private Long idEvento;

    private String nomeEvento;

    private LocalDateTime dataEvento;

    private Long idConteudo;

    private String tituloConteudo;

    private int minutos;

    private boolean sugestao;
}
