package com.example.CampusLink.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = UrlValidaValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlValida {

    String message() default "URL inválida.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}