// dados de turma organiza os campos usados no ambiente e na lista de participantes
package com.example.CampusLink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// registro da turma esta classe guarda nome descricao responsavel e dados da pessoa selecionada
@Getter
@Setter
public class TurmaDTO {

    private Long id;

    private Long idProprietario;

    @NotBlank(message = "O nome da turma é obrigatório")
    @Size(max = 100, message = "O nome da turma deve ter no máximo 100 caracteres")
    private String nomeTurma;

    @Size(max = 1000, message = "A descrição da turma deve ter no máximo 1000 caracteres")
    private String descricao;

    // id do usuario identifica o participante nos formularios para que a tela so precise do email mascarado
    private Long idPessoa;

    private String emailPessoa;

    private String nomePessoa;

    private String tipoUsuario;
}
