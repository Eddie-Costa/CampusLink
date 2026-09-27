package com.example.CampusLink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GoogleBooksConfig {

    @Bean
    public RestClient googleBooksRestClient() {

        return RestClient.builder()
                .baseUrl("https://www.googleapis.com/books/v1")
                .build();
    }
}
