// validacao de enderecos confere os links informados nos materiais
package com.example.CampusLink.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;
import com.example.CampusLink.service.DominioService;
import org.springframework.beans.factory.annotation.Autowired;

// verificador de links esta classe limita tamanho protocolo e destino aos dominios autorizados
public class UrlValidaValidator implements ConstraintValidator<UrlValida, String> {

    @Autowired
    private DominioService dominioService;

    private static final int TAMANHO_MAXIMO = 2048;

    private static final Set<String> PROTOCOLOS_PERMITIDOS = Set.of("http", "https");

    private static final Pattern CARACTERE_DE_CONTROLE = Pattern.compile("[\\s\\p{Cntrl}]");




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

    // validar link do conteudo este metodo confere formato protocolo e dominio autorizado aceitando campo vazio
    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {

        // validar endereco opcional aceita um valor vazio
        if (valor == null || valor.isBlank()) {
            return true;
        }

        String url = valor.trim();

        // validar tamanho do endereco rejeita enderecos acima do limite permitido
        if (url.length() > TAMANHO_MAXIMO) {
            return falhar(context, "A URL deve possuir no máximo " + TAMANHO_MAXIMO + " caracteres.");
        }

        // validar caracteres do endereco rejeita espacos e caracteres de controle
        if (CARACTERE_DE_CONTROLE.matcher(url).find()) {
            return falhar(context, "A URL não pode conter espaços ou caracteres de controle.");
        }

        URI uri;
        // ler endereco informado tenta interpretar o endereco e informa erros de formato
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return falhar(context, "A URL informada não é válida.");
        }

        String protocolo = uri.getScheme();
        // validar protocolo do endereco exige http ou https
        if (protocolo == null || !PROTOCOLOS_PERMITIDOS.contains(protocolo.toLowerCase())) {
            return falhar(context, "A URL deve começar com http:// ou https://.");
        }

        // validar credenciais no endereco rejeita enderecos com usuario ou senha
        if (uri.getUserInfo() != null) {
            return falhar(context, "A URL não pode conter usuário/senha embutidos.");
        }

        String host = uri.getHost();
        // validar dominio do endereco exige um nome de servidor preenchido
        if (host == null || host.isBlank()) {
            return falhar(context, "A URL deve possuir um endereço válido.");
        }

        // bloquear endereco restrito rejeita servidores que correspondem aos enderecos bloqueados
        if (HOST_BLOQUEADO.matcher(host).matches()) {
            return falhar(context, "Esse endereço não é permitido.");
        }
        boolean autorizado;
        // consultar permissao do dominio tenta verificar se o dominio esta autorizado
        try {
            autorizado = dominioService.hostAutorizado(host);
        } catch (RuntimeException e) {
            return falhar(context, "Não foi possível verificar o site agora. Tente novamente.");
        }

        // exigir dominio autorizado orienta solicitar liberacao quando o dominio nao e permitido
        if (!autorizado) {
            return falhar(context, "O site " + host.toLowerCase()
                    + " ainda não está liberado. Feche e use o botão \"Solicitar site\" para pedir a liberação.");
        }
        return true;
    }

    // informar erro do link este metodo substitui a mensagem padrao pelo motivo da recusa
    private boolean falhar(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem)
                .addConstraintViolation();
        return false;
    }

}
