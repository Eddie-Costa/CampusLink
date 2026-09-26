package com.example.CampusLink.dto.Admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class turmaAdminDTO {

    private Long id;

    private String nomeTurma;

    private String descricao;

    private Long idProprietario;

    private String nomeProprietario;

    private Integer quantidadeAlunos;

    private Integer quantidadeProfessores;
}