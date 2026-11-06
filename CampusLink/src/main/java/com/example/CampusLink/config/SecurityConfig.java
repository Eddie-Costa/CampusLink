// seguranca dos formularios configura o tratamento dos pedidos recebidos
package com.example.CampusLink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

// protecao dos pedidos esta classe prepara o codigo dos formularios e a pagina de login
@Configuration
public class SecurityConfig {

    // configurar seguranca este metodo prepara a protecao dos formularios e libera o acesso as rotas
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {


        XorCsrfTokenRequestAttributeHandler csrfHandler =
                new XorCsrfTokenRequestAttributeHandler();


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