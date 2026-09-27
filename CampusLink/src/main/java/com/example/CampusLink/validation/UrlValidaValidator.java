package com.example.CampusLink.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

public class UrlValidaValidator implements ConstraintValidator<UrlValida, String> {

    private static final int TAMANHO_MAXIMO = 2048;

    private static final Set<String> PROTOCOLOS_PERMITIDOS = Set.of("http", "https");

    private static final Pattern CARACTERE_DE_CONTROLE = Pattern.compile("[\\s\\p{Cntrl}]");

    // hosts/faixas que não fazem sentido para um link de conteúdo educacional
    // e que, se o servidor um dia buscar essa URL (ex: preview, integrações),
    // poderiam ser usados para acessar rede interna (SSRF)
    private static final Pattern HOST_BLOQUEADO = Pattern.compile(
            "^(localhost" +
                    "|127(\\.\\d{1,3}){3}" +
                    "|0\\.0\\.0\\.0" +
                    "|::1" +
                    "|10(\\.\\d{1,3}){3}" +
                    "|192\\.168(\\.\\d{1,3}){2}" +
                    "|169\\.254(\\.\\d{1,3}){2}" +
                    "|172\\.(1[6-9]|2\\d|3[0-1])(\\.\\d{1,3}){2}" +
                    ")$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {

        if (valor == null || valor.isBlank()) {
            return true; // campo opcional
        }

        String url = valor.trim();

        if (url.length() > TAMANHO_MAXIMO) {
            return falhar(context, "A URL deve possuir no máximo " + TAMANHO_MAXIMO + " caracteres.");
        }

        if (CARACTERE_DE_CONTROLE.matcher(url).find()) {
            return falhar(context, "A URL não pode conter espaços ou caracteres de controle.");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return falhar(context, "A URL informada não é válida.");
        }

        String protocolo = uri.getScheme();
        if (protocolo == null || !PROTOCOLOS_PERMITIDOS.contains(protocolo.toLowerCase())) {
            return falhar(context, "A URL deve começar com http:// ou https://.");
        }

        if (uri.getUserInfo() != null) {
            return falhar(context, "A URL não pode conter usuário/senha embutidos.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return falhar(context, "A URL deve possuir um endereço válido.");
        }

        if (HOST_BLOQUEADO.matcher(host).matches()) {
            return falhar(context, "Esse endereço não é permitido.");
        }

        return true;
    }

    private boolean falhar(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem)
                .addConstraintViolation();
        return false;
    }
}
