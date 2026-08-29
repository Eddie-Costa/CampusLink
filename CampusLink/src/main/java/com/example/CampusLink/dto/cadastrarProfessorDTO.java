package com.example.CampusLink.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter


public class cadastrarProfessorDTO {
    @Size(max = 11, message = "A matricula deve ter no minimo 11 caracteres")
    @NotBlank(message = "A matricula é obrigatória")
    private String matricula;

    @Size(max = 50, message = "O nome deve ter no maximo 50 caracteres")
    @NotBlank(message = "O nome é obrigatório")
    private String nome;

    @Size(max = 100, message = "O e-mail deve ter no maximo 100 caracteres")
    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    private String email;

    @Size(max = 13, message = "O telefone deve ter no maximo 13 caracteres")
    @NotBlank(message = "O telefone é obrigatório")
    private String telefone;

    @Size(max = 10, message = "A Data de nascimento deve ter no maximo 10 caracteres")
    @NotBlank(message = "A Data de nascimento é obrigatória")
    private String dataNasc;

    @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%^&+=!]).*$",
            message = "Senha deve conter maiúscula, minúscula, número e caractere especial"
    )
    private String senha;
}
