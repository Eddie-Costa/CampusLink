// resumo administrativo de turma organiza os dados exibidos nas listagens
package com.example.CampusLink.dto.Admin;

import lombok.Getter;
import lombok.Setter;

// dados da turma na gestao esta classe guarda nome responsavel e quantidades de participantes
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