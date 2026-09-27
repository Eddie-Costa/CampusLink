package com.example.CampusLink.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class DadosUsuarioDTO {

    private Long id;

    private String nome;

    private String email;

    private String telefone;

    private LocalDate dataNascimento;

    private String perfil;

    private String identificador;

    private LocalDate dataCadastro;

    public String getTipoConta() {

        if ("ALUNOS".equals(perfil)) {
            return "Aluno";
        } else if ("PROFESSORES".equals(perfil)) {
            return "Professor";
        }

        return "Administrador";
    }

    public String getRotuloIdentificador() {

        if ("ALUNOS".equals(perfil)) {
            return "RGM";
        }

        return "Matrícula";
    }
}
