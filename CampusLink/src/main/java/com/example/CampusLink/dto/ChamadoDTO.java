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
public class ChamadoDTO {

    private Long id;
    private Long idUsuario;

    @NotBlank(message = "O assunto é obrigatório")
    @Size(max = 150, message = "O assunto deve ter no máximo 150 caracteres")
    private String assunto;

    @NotBlank(message = "A descrição é obrigatória")
    @Size(max = 5000, message = "A descrição deve ter no máximo 5000 caracteres")
    private String descricao;

    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime concluidoEm;
    private Long idAdminConclusao;

    private String nomeUsuario;
    private String emailUsuario;
    private List<ChamadoRespostaDTO> respostas = new ArrayList<>();

    public String getStatusDescricao() {
        if ("respondido".equals(status)) return "Respondido";
        if ("concluido".equals(status)) return "Concluído";
        return "Aberto";
    }

    public boolean isPossuiRespostaAdmin() {
        for (ChamadoRespostaDTO resposta : respostas) {
            if ("admin".equals(resposta.getTipoAutor())) return true;
        }
        return false;
    }
}
