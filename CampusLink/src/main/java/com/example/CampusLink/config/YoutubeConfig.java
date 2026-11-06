// integracao de videos prepara a comunicacao com a busca externa
package com.example.CampusLink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// conexao de videos esta classe fornece o cliente de consultas do youtube
@Configuration
public class YoutubeConfig {

    // configurar busca de videos este metodo define o endereco usado para consultar videos
    @Bean
    public RestClient youtubeRestClient() {

        return RestClient.builder()
                .baseUrl("https://www.googleapis.com/youtube/v3")
                .build();
    }
}
