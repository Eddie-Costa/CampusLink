// resumo administrativo de usuario organiza os dados de gerenciamento da conta
package com.example.CampusLink.dto.Admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// dados da conta na gestao esta classe guarda identificadores perfil situacao e permissao master
@Getter
@Setter
public class usuarioAdminDTO {

    private Long idUsuario;

    private Long idPerfil;

    @NotBlank(message = "Informe o RGM ou matrícula.")
    @Size(min = 11, max = 11, message = "O RGM ou matrícula deve ter exatamente 11 números.")
    @Pattern(regexp = "^\\d{11}$", message = "O RGM ou matrícula deve ter exatamente 11 números.")
    private String identificador;

    @NotBlank(message = "Informe o nome do usuário.")
    @Size(min = 2, max = 50, message = "O nome deve ter entre 2 e 50 caracteres.")
    @Pattern(regexp = "^[a-zA-Z\\s]{2,50}$", message = "O nome deve conter apenas letras e espaços.")
    private String nome;

    @NotBlank(message = "Informe o e-mail.")
    @Size(min = 5, max = 100, message = "O e-mail deve ter entre 5 e 100 caracteres.")
    @Email(message = "Informe um e-mail válido.")
    private String email;

    @NotBlank(message = "Informe o telefone.")
    @Size(min = 10, max = 11, message = "O telefone deve ter entre 10 e 11 números.")
    @Pattern(regexp = "^\\d{10,11}$", message = "O telefone deve conter apenas números.")
    private String telefone;

    @NotBlank(message = "Informe a data de nascimento.")
    @Size(min = 10, max = 10, message = "Informe uma data de nascimento válida.")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Informe uma data de nascimento válida.")
    private String dataNasc;

    private String perfil;

    private boolean status;

    private boolean master;
}