package com.example.CampusLink.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
                .addFilterBefore(new SessaoAutenticacaoFilter(), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/documentos/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/", "/home", "/funcionalidades", "/sobre-nos", "/logout").permitAll()
                        .requestMatchers("/login", "/loginAluno", "/loginProfessor", "/admin/login").permitAll()
                        .requestMatchers("/cadastrar", "/cadastrarAluno", "/cadastrarAlunoVerificado",
                                "/cadastrarProfessor", "/cadastrarProfessorVerificado").permitAll()
                        .requestMatchers("/verificar", "/verificar/reenviar", "/verificarAluno",
                                "/verificarProfessor", "/verificarAdmin").permitAll()
                        .requestMatchers("/ResetPassword_Verification", "/ResetVerification", "/ResetPassword", "/reset").permitAll()
                        .requestMatchers(HttpMethod.GET, "/termos").permitAll()

                        .requestMatchers("/admin/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").hasRole("ADMIN")
                        .requestMatchers("/score/**").hasRole("ALUNO")
                        .requestMatchers("/acompanhamento/**", "/meus-conteudos/**", "/criarTurma", "/excluirTurma",
                                "/adicionarPessoas", "/removerPessoas", "/arquivos/upload", "/arquivos/*/excluir",
                                "/turmas/*/dominios").hasRole("PROFESSOR")
                        .requestMatchers("/termos/aceite", "/termos/revogar").hasAnyRole("ALUNO", "PROFESSOR")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint((request, response, excecao) -> {
                            String caminho = request.getRequestURI().substring(request.getContextPath().length());
                            String login = caminho.startsWith("/admin") ? "/admin/login" : "/login";
                            response.sendRedirect(request.getContextPath() + login);
                        })
                );

        return http.build();
    }
}
