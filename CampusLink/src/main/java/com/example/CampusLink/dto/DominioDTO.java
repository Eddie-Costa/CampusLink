package com.example.CampusLink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class DominioDTO {

    private Long id;

    @NotBlank(message = "Informe o domínio que deseja solicitar")
    @Size(max = 253, message = "O domínio deve ter no máximo 253 caracteres")
    private String dominio;

    // pendente | aprovado | rejeitado
    private String status;

    private Long idProfessor;

    @Size(max = 500, message = "A justificativa deve ter no máximo 500 caracteres")
    private String justificativa;

    private String comentarioAdmin;

    private OffsetDateTime createdAt;

    private OffsetDateTime analisadoEm;

    // só preenchido nas listagens do admin (join com USUARIOS)
    private String nomeProfessor;
}