// regra de link define a validacao aplicavel aos campos de endereco
package com.example.CampusLink.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

// marcacao de endereco valido esta interface associa o campo ao verificador de formato e dominio
@Documented
@Constraint(validatedBy = UrlValidaValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlValida {

    // definir mensagem de link invalido este metodo informa o texto padrao da validacao
    String message() default "URL inválida.";

    // definir grupos da validacao de link este metodo permite escolher conjuntos de regras para conferir o campo
    Class<?>[] groups() default {};

    // definir dados extras da validacao de link este metodo permite associar informacoes adicionais a regra
    Class<? extends Payload>[] payload() default {};
}