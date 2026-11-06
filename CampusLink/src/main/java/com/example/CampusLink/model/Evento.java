// evento persistido relaciona a agenda academica ao banco
package com.example.CampusLink.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// compromisso academico esta classe guarda horarios turma professor prioridade e detalhes para exibicao
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

    @Transient
    private String nomeTurma;

    @Transient
    private List<String> nomesConteudos = new ArrayList<>();



    // preparar evento com prioridade media este construtor usa a prioridade padrao para o novo evento
    public Evento(String nome, LocalDateTime inicio, LocalDateTime fim, String conteudoId) {

        this(nome, inicio, fim, conteudoId, "MEDIA");
    }



    // preparar evento com prioridade este construtor guarda os dados e inicia o evento como ativo
    public Evento(String nome, LocalDateTime inicio, LocalDateTime fim, String conteudoId, String prioridade) {

        this.nome = nome;
        this.inicio = inicio;
        this.fim = fim;
        this.conteudoId = conteudoId;
        this.prioridade = normalizarPrioridade(prioridade);
        this.status = "ATIVO";
    }




    // normalizar prioridade do evento este metodo usa media quando a opcao nao e reconhecida
    private String normalizarPrioridade(String prioridade) {

        // definir prioridade padrao usa prioridade media quando nenhuma opcao foi preenchida
        if (prioridade == null || prioridade.isBlank()) {

            return "MEDIA";
        }

        String prioridadeNormalizada = prioridade.trim().toUpperCase();

        return switch (prioridadeNormalizada) {

            case "ALTA", "MEDIA", "BAIXA" -> prioridadeNormalizada;
            default -> "MEDIA";
        };
    }


    // alterar prioridade do evento este metodo normaliza a opcao antes de salvar o valor
    public void setPrioridade(String prioridade
    ) {

        this.prioridade = normalizarPrioridade(prioridade);
    }



    // verificar materiais do evento este metodo informa se existem nomes de conteudos associados
    public boolean possuiConteudoAssociado() {

        return nomesConteudos != null && !nomesConteudos.isEmpty();}
}