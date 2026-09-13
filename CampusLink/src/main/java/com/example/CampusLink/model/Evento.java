package com.example.CampusLink.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Indica que esta classe representa uma tabela do banco de dados
@Entity
@Table(name = "\"EVENTOS\"", schema = "public")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "id_turma")
    private Long idTurma;

    @Column(name = "id_professor")
    private Long idProfessor;

    @Column(name = "titulo")
    private String nome;

    @Column(name = "tipo")
    private String tipo;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "data_hora")
    private LocalDateTime inicio;

    @Column(name = "data_fim")
    private LocalDateTime fim;

    @Column(name = "status")
    private String status;

    @Column(name = "prioridade")

    @Setter(AccessLevel.NONE)
    private String prioridade;

    @Transient
    private String conteudoId;

    // usado quando a prioridade nao e informada
    public Evento(String nome, LocalDateTime inicio, LocalDateTime fim, String conteudoId) {
        this(nome, inicio, fim, conteudoId, "MEDIA");
    }

    // usado para criar um evento
    public Evento(String nome, LocalDateTime inicio, LocalDateTime fim, String conteudoId, String prioridade
    ) {
        this.nome = nome;
        this.inicio = inicio;
        this.fim = fim;
        this.conteudoId = conteudoId;

        // prioridade tenha um valor permitido
        this.prioridade = normalizarPrioridade(prioridade);

        // Todo evento começa com o status ativo.
        this.status = "ATIVO";
    }

    // prioridade em um formato padrão
    private String normalizarPrioridade(String prioridade) {

        if (prioridade == null || prioridade.isBlank()) {return "MEDIA";}

        String prioridadeNormalizada = prioridade.trim().toUpperCase();

        //  somente os tres niveis definidos pelo sistema
        return switch (prioridadeNormalizada) {
            case "ALTA", "MEDIA", "BAIXA" -> prioridadeNormalizada;

            // Qualquer outro valor recebe MEDIA.
            default -> "MEDIA";
        };
    }

    // altera a prioridade sempre aplicando a validaca
    public void setPrioridade(String prioridade) {
        this.prioridade = normalizarPrioridade(prioridade);
    }
}