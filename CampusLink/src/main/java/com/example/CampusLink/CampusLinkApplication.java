package com.example.CampusLink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CampusLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusLinkApplication.class, args);
    }

    @Bean
    CommandLineRunner testarEnv(@Value("${DB_URL:NAO_ENCONTRADO}") String dbUrl) {
        return args -> System.out.println("DB_URL carregada: " + dbUrl);
    }
}
