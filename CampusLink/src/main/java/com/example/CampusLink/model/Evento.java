package com.example.CampusLink.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // usado só no codigo e nao vai para a tabela
    @Transient
    private String conteudoId;

    @Transient
    private String nomeTurma;

    @Transient
    private List<String> nomesConteudos = new ArrayList<>();


    // cria o evento com prioridade media
    public Evento(String nome, LocalDateTime inicio, LocalDateTime fim, String conteudoId) {

        this(nome, inicio, fim, conteudoId, "MEDIA");
    }


    // cria o evento com a prioridade escolhida
    public Evento(String nome, LocalDateTime inicio, LocalDateTime fim, String conteudoId, String prioridade) {

        this.nome = nome;
        this.inicio = inicio;
        this.fim = fim;
        this.conteudoId = conteudoId;
        this.prioridade = normalizarPrioridade(prioridade);
        this.status = "ATIVO";
    }


    // deixa a prioridade no formato usado pelo sistema

    private String normalizarPrioridade(String prioridade) {

        if (prioridade == null || prioridade.isBlank()) {

            return "MEDIA";
        }

        String prioridadeNormalizada = prioridade.trim().toUpperCase();

        return switch (prioridadeNormalizada) {

            case "ALTA", "MEDIA", "BAIXA" -> prioridadeNormalizada;
            default -> "MEDIA";
        };
    }


    public void setPrioridade(String prioridade
    ) {

        this.prioridade = normalizarPrioridade(prioridade);
    }


    // verifica se o evento tem algum conteudo
    public boolean possuiConteudoAssociado() {

        return nomesConteudos != null && !nomesConteudos.isEmpty();}
}