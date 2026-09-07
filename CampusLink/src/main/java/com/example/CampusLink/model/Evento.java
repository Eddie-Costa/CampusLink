package com.example.CampusLink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "\"EVENTOS\"",
        schema = "public"
)
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

    public Evento(
            String nome,
            LocalDateTime inicio,
            LocalDateTime fim,
            String conteudoId
    ) {
        this(nome, inicio, fim, conteudoId, "MEDIA");
    }

    public Evento(
            String nome,
            LocalDateTime inicio,
            LocalDateTime fim,
            String conteudoId,
            String prioridade
    ) {
        this.nome = nome;
        this.inicio = inicio;
        this.fim = fim;
        this.conteudoId = conteudoId;
        this.prioridade = normalizarPrioridade(prioridade);
        this.status = "ATIVO";
    }

    private String normalizarPrioridade(String prioridade) {
        if (prioridade == null || prioridade.isBlank()) {
            return "MEDIA";
        }

        String prioridadeNormalizada =
                prioridade.trim().toUpperCase();

        if (prioridadeNormalizada.equals("ALTA")
                || prioridadeNormalizada.equals("MEDIA")
                || prioridadeNormalizada.equals("BAIXA")) {
            return prioridadeNormalizada;
        }

        return "MEDIA";
    }

    public void setPrioridade(String prioridade) {
        this.prioridade = normalizarPrioridade(prioridade);
    }
}