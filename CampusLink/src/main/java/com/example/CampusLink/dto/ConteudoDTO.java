package com.example.CampusLink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ConteudoDTO {

    private Long id;

    private Long idTurma;

    private Long idProfessor;

    private OffsetDateTime createdAt;

    @NotBlank(message = "O nome do conteúdo é obrigatório")
    @Size(max = 150, message = "O nome do conteúdo deve ter no máximo 150 caracteres")
    private String titulo;

    private String descricao;

    private String tipo;

    private String url;

    private String duracaoEstimada;

    private String prioridade;

    private String status;

    private String nomeTurma;

    private String nomeProfessor;

    private boolean revisaoSolicitada;

    private OffsetDateTime revisaoSolicitadaEm;

    private List<String> alunosDenunciaram = new ArrayList<>();
}