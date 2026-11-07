// dados de chamado organiza o pedido de ajuda e sua conversa
package com.example.CampusLink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

// registro do atendimento esta classe valida assunto e descricao e guarda titular situacao datas e respostas
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

    // descrever situacao do chamado este metodo retorna o nome da situacao para exibicao
    public String getStatusDescricao() {
        // descrever chamado respondido usa o rotulo correspondente quando existe resposta
        if ("respondido".equals(status)) return "Respondido";
        // descrever chamado concluido usa o rotulo de atendimento encerrado
        if ("concluido".equals(status)) return "Concluído";
        return "Aberto";
    }

    // verificar resposta administrativa este metodo procura uma mensagem de administrador no chamado
    public boolean isPossuiRespostaAdmin() {
        for (ChamadoRespostaDTO resposta : respostas) {
            // reconhecer resposta da equipe confirma a presenca de uma mensagem do administrador
            if ("admin".equals(resposta.getTipoAutor())) return true;
        }
        return false;
    }
}
