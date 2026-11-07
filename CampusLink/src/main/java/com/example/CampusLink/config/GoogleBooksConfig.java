// integracao de livros prepara a comunicacao com o catalogo externo
package com.example.CampusLink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// conexao de livros esta classe fornece o cliente de consultas do google books
@Configuration
public class GoogleBooksConfig {

    // configurar busca de livros este metodo define o endereco usado para consultar livros
    @Bean
    public RestClient googleBooksRestClient() {

        return RestClient.builder()
                .baseUrl("https://www.googleapis.com/books/v1")
                .build();
    }
}
