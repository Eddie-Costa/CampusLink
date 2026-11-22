// resultado de risco organiza a avaliacao do tempo de estudo
package com.example.CampusLink.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// viabilidade do aluno esta classe relaciona evento material carga capacidade nivel e motivo
@Getter
@Setter
public class ViabilidadeDTO {

    private Long idAluno;

    private Long idTurma;

    private Long idEvento;

    private String nomeEvento;

    private LocalDateTime dataEvento;

    private Long idConteudo;

    private String tituloConteudo;

    private String nivel;

    // carga acumulada ate o evento, somando os eventos anteriores que disputam as mesmas horas livres
    private int cargaMin;

    // carga so do evento, preenchida no resumo do evento (sem os eventos anteriores)
    private int cargaEventoMin;

    private int capacidadeMin;

    private String motivo;
}
