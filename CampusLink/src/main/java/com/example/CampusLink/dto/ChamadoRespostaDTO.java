package com.example.CampusLink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ChamadoRespostaDTO {

    private Long id;
    private Long idChamado;
    private Long idAdminUsuario;
    private Long idUsuario;
    private String tipoAutor;
    private String nomeAutor;

    @NotBlank(message = "Escreva uma mensagem")
    @Size(max = 5000, message = "A mensagem deve ter no máximo 5000 caracteres")
    private String resposta;

    private OffsetDateTime createdAt;
}
