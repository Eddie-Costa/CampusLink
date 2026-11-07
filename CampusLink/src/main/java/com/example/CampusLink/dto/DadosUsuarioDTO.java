// dados pessoais organiza os campos consultados e exportados pelo titular
package com.example.CampusLink.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

// cadastro do titular esta classe guarda contato nascimento perfil e identificador sem incluir a senha
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

    // descrever tipo da conta este metodo traduz o perfil para aluno professor ou administrador
    public String getTipoConta() {

        // descrever conta de aluno traduz o perfil salvo para o nome exibido
        if ("ALUNOS".equals(perfil)) {
            return "Aluno";
        }
        // descrever conta de professor traduz o perfil salvo para o nome exibido
        else if ("PROFESSORES".equals(perfil)) {
            return "Professor";
        }

        return "Administrador";
    }

    // descrever identificador da conta este metodo escolhe entre rgm e matricula conforme o perfil
    public String getRotuloIdentificador() {

        // escolher identificador do aluno usa o rotulo rgm para esse perfil
        if ("ALUNOS".equals(perfil)) {
            return "RGM";
        }

        return "Matrícula";
    }
}
