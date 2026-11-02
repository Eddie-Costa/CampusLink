package com.example.CampusLink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// mapeia so o id de proposito: com ddl-auto=update, mapear colunas text como String faz o hibernate trocar o tipo para varchar(255)
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