// formulario de cadastro de aluno define os dados recebidos na inscricao
package com.example.CampusLink.dto.Aluno;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// dados do aluno inscrito esta classe valida rgm contato nascimento e senha
@Getter
@Setter


public class cadastrarAlunoDTO {
    @Size(min = 11, max = 11, message = "O RGM deve ter exatamente 11 números")
    @Pattern(regexp = "^\\d{11}$", message = "O RGM deve ter exatamente 11 números")
    @NotBlank(message = "O rgm é obrigatório")
    private String rgm;

    @Size(min = 2, max = 50, message = "O nome deve ter entre 2 e 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z\\s]{2,50}$", message = "O nome deve conter apenas letras e espaços")
    @NotBlank(message = "O nome é obrigatório")
    private String nome;

    @Size(min = 5, max = 100, message = "O e-mail deve ter entre 5 e 100 caracteres")
    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    private String email;

    @Size(min = 10, max = 11, message = "O telefone deve ter entre 10 e 11 números")
    @Pattern(regexp = "^\\d{10,11}$", message = "O telefone deve conter apenas números")
    @NotBlank(message = "O telefone é obrigatório")
    private String telefone;

    @Size(min = 10, max = 10, message = "Informe uma data de nascimento válida")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Informe uma data de nascimento válida")
    @NotBlank(message = "A Data de nascimento é obrigatória")
    private String dataNasc;

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%^&+=!]).*$",
            message = "Senha deve conter maiúscula, minúscula, número e caractere especial"
    )
    private String senha;

    @AssertTrue(message = "É necessário aceitar os Termos de Uso")
    private boolean termos;
}