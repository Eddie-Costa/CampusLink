// formulario administrativo de usuario define os campos de uma nova conta
package com.example.CampusLink.dto.Admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// dados do novo usuario esta classe valida perfil identificador nome contato dados pessoais e senha
@Getter
@Setter
public class cadastrarUsuarioAdminDTO {

    @NotBlank(message = "O perfil é obrigatório")
    @Pattern(regexp = "^(Aluno|Professor)$", message = "Perfil inválido")
    private String perfil;

    // RGM e matricula devem conter exatamente 11 digitos, como nos cadastros de aluno e professor
    @Size(min = 11, max = 11, message = "O RGM ou matrícula deve ter exatamente 11 números")
    @Pattern(regexp = "^\\d{11}$", message = "O RGM ou matrícula deve ter exatamente 11 números")
    @NotBlank(message = "O RGM ou matrícula é obrigatório")
    private String identificador;

    // nome deve conter apenas letras e espacos e ter entre 2 e 50 caracteres
    @Size(min = 2, max = 50, message = "O nome deve ter entre 2 e 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z\\s]{2,50}$", message = "O nome deve conter apenas letras e espaços")
    @NotBlank(message = "O nome é obrigatório")
    private String nome;

    @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres")
    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    private String email;

    // telefone deve conter 10 ou 11 digitos, seguindo o formato dos cadastros existentes
    @Size(min = 10, max = 11, message = "O telefone deve ter entre 10 e 11 números")
    @Pattern(regexp = "^\\d{10,11}$", message = "O telefone deve conter apenas números")
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