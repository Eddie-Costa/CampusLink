// referencia de conteudo permite usar os materiais nas operacoes de revisao
package com.example.CampusLink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


// identidade do material esta classe representa o identificador da tabela de conteudos
@Entity
@Table(name = "\"CONTEUDOS\"", schema = "public")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conteudo {

    @Id
    @Column(name = "id")
    private Long id;
}