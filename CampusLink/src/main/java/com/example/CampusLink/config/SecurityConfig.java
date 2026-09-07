package com.example.CampusLink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // Mantém o tratamento seguro do token CSRF.
        XorCsrfTokenRequestAttributeHandler csrfHandler =
                new XorCsrfTokenRequestAttributeHandler();

        // Carrega o token em cada requisição, antes de renderizar o HTML.
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRequestHandler(csrfHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/css/**", "/js/**", "/images/**").permitAll()
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                );

        return http.build();
    }
}