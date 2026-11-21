package com.example.CampusLink.service;

import org.springframework.stereotype.Component;

// mascara de email esta classe esconde o endereco de outros usuarios nas telas do administrador (anonimizacao lgpd)
@Component("mascaraEmail")
public class MascaraEmail {

    // mascarar email este metodo mantem as duas primeiras letras e o dominio, ex.: an***@gmail.com
    public String mascarar(String email) {
        // tratar email ausente mostra o aviso padrao das telas
        if (email == null || email.isBlank()) {
            return "Não informado";
        }

        int arroba = email.indexOf('@');
        // esconder texto sem arroba nao revela nada quando o valor nao e um email
        if (arroba < 1) {
            return "***";
        }

        String inicio = email.substring(0, Math.min(2, arroba));
        return inicio + "***" + email.substring(arroba);
    }
}
