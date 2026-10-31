package com.example.CampusLink.integracao;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Navegador {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*?value=\"([^\"]+)\"");

    private final RestClient client;

    Navegador(int porta) {
        HttpClient http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        client = RestClient.builder()
                .baseUrl("http://localhost:" + porta)
                .requestFactory(new JdkClientHttpRequestFactory(http))
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    public static MultiValueMap<String, String> campos(String... paresChaveValor) {
        MultiValueMap<String, String> campos = new LinkedMultiValueMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            campos.add(paresChaveValor[i], paresChaveValor[i + 1]);
        }
        return campos;
    }

    public ResponseEntity<String> get(String uri) {
        return client.get().uri(uri).retrieve().toEntity(String.class);
    }

    public ResponseEntity<String> enviarFormulario(String paginaDoFormulario, String uri, MultiValueMap<String, String> campos) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>(campos);
        formulario.add("_csrf", csrfDaPagina(paginaDoFormulario));

        return client.post().uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario)
                .retrieve()
                .toEntity(String.class);
    }

    public ResponseEntity<String> enviarSemCsrf(String uri, MultiValueMap<String, String> campos) {
        return client.post().uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(campos)
                .retrieve()
                .toEntity(String.class);
    }

    private String csrfDaPagina(String pagina) {
        String html = get(pagina).getBody();
        Matcher matcher = CSRF.matcher(html == null ? "" : html);
        if (!matcher.find()) {
            throw new IllegalStateException("Token CSRF não encontrado em " + pagina);
        }
        return matcher.group(1);
    }
}
