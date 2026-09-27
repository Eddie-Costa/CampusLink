package com.example.CampusLink.dto.Admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class cadastrarUsuarioAdminDTO {

    @NotBlank(message = "O perfil é obrigatório")
    @Pattern(regexp = "^(Aluno|Professor)$", message = "Perfil inválido")
    private String perfil;

    @Size(max = 11, message = "O identificador deve ter no máximo 11 caracteres")
    @NotBlank(message = "O RGM ou matrícula é obrigatório")
    private String identificador;

    @Size(max = 50, message = "O nome deve ter no máximo 50 caracteres")
    @NotBlank(message = "O nome é obrigatório")
    private String nome;

    @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres")
    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    private String email;

    @Size(max = 13, message = "O telefone deve ter no máximo 13 caracteres")
    @NotBlank(message = "O telefone é obrigatório")
    private String telefone;

    @Size(max = 10, message = "A data de nascimento deve ter no máximo 10 caracteres")
    @NotBlank(message = "A data de nascimento é obrigatória")
    private String dataNasc;

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%^&+=!]).*$",
            message = "Senha deve conter maiúscula, minúscula, número e caractere especial"
    )
    private String senha;

    @NotBlank(message = "A confirmação da senha é obrigatória")
    private String confirmarSenha;
}