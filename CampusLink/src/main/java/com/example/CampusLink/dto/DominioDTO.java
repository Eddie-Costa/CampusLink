// dados de dominio organiza os pedidos de liberacao de sites
package com.example.CampusLink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

// registro do dominio esta classe guarda endereco justificativa professor e decisao administrativa
@Getter
@Setter
public class DominioDTO {

    private Long id;

    @NotBlank(message = "Informe o link ou o endereço do site")
    @Size(max = 253, message = "O endereço do site deve ter no máximo 253 caracteres")
    private String dominio;


    private String status;

    private Long idProfessor;

    @Size(max = 500, message = "A justificativa deve ter no máximo 500 caracteres")
    private String justificativa;

    private String comentarioAdmin;

    private OffsetDateTime createdAt;

    private OffsetDateTime analisadoEm;


    private String nomeProfessor;
}