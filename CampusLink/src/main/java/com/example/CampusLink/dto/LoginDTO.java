// dados do login unico guardam email e senha digitados por aluno ou professor
package com.example.CampusLink.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// formulario de login esta classe recebe as credenciais sem exigir o formato de senha forte no acesso
@Getter
@Setter
public class LoginDTO {

    @NotBlank(message = "Informe o e-mail.")
    @Email(message = "Informe um e-mail válido.")
    @Size(max = 100, message = "O e-mail deve ter no máximo 100 caracteres.")
    private String email;

    @NotBlank(message = "Informe a senha.")
    @Size(max = 100, message = "A senha deve ter no máximo 100 caracteres.")
    private String senha;

    // guardar email este metodo remove espacos das pontas antes da validacao do formulario
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}
