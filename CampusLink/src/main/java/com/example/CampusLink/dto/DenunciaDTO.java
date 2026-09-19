package com.example.CampusLink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
public class DenunciaDTO {
    private Long id;
    private Long idConteudo;
    private Long idAluno;
    private Long idProfessor;
    @NotBlank(message = "O motivo da denúncia é obrigatório")
    @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
    private String motivo;
    private String status;
    private OffsetDateTime createdAt;
}