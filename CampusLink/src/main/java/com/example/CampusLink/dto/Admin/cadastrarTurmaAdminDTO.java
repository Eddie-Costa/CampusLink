// formulario administrativo de turma define os dados de criacao
package com.example.CampusLink.dto.Admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// dados da nova turma esta classe exige nome e responsavel e permite informar a descricao
@Getter
@Setter
public class cadastrarTurmaAdminDTO {

    @NotBlank(message = "O nome da turma é obrigatório")
    @Size(max = 100, message = "O nome da turma deve ter no máximo 100 caracteres")
    private String nomeTurma;

    private String descricao;

    @NotNull(message = "Selecione o professor responsável")
    private Long idProprietario;
}