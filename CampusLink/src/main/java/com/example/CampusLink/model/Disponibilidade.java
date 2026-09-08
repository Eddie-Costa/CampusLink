package com.example.CampusLink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "\"DISPONIBILIDADES\"",
        schema = "public"
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Disponibilidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "id_aluno", nullable = false)
    private Long idAluno;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "horas_disponiveis", nullable = false)
    private Integer horasDisponiveis;

    public Disponibilidade(
            Long idAluno,
            LocalDate data,
            Integer horasDisponiveis
    ) {
        this.idAluno = idAluno;
        this.data = data;
        this.horasDisponiveis = horasDisponiveis;
    }
}