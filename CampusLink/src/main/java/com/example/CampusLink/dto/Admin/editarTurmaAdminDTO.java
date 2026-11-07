// formulario de edicao de turma define os dados recebidos pela administracao
package com.example.CampusLink.dto.Admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// dados da turma editada esta classe guarda o identificador e valida nome e responsavel
@Getter
@Setter
public class editarTurmaAdminDTO {

    private Long id;

    @NotBlank(message = "O nome da turma é obrigatório")
    @Size(max = 100, message = "O nome da turma deve ter no máximo 100 caracteres")
    private String nomeTurma;

    private String descricao;

    @NotNull(message = "Selecione o professor responsável")
    private Long idProprietario;
}