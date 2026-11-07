// seguranca dos formularios configura o tratamento dos pedidos recebidos
package com.example.CampusLink.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
