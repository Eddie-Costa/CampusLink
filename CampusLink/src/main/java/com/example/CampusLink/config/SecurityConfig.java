// seguranca das rotas spring security define quem acessa cada pagina e protege os formularios contra csrf
package com.example.CampusLink.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

// controle de acesso das rotas esta classe bloqueia por padrao e libera cada rota conforme o papel aluno professor ou admin
@Configuration
public class SecurityConfig {

    // configurar seguranca este metodo liga o csrf o filtro de sessao as regras de acesso e o destino de quem nao esta conectado
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // proteger formularios contra csrf mantem o tratamento seguro do codigo enviado em cada formulario
        XorCsrfTokenRequestAttributeHandler csrfHandler =
                new XorCsrfTokenRequestAttributeHandler();

        // carregar codigo csrf disponibiliza o codigo em cada requisicao antes de montar o html
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRequestHandler(csrfHandler)
                )
                // reconhecer usuario da sessao informa ao spring security o papel de quem fez login
                .addFilterBefore(new SessaoAutenticacaoFilter(), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // liberar paginas de erro permite exibir erros sem exigir login
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        // liberar arquivos estaticos permite carregar estilos scripts e imagens sem login
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/documentos/**", "/favicon.ico", "/error").permitAll()
                        // liberar paginas publicas permite inicio funcionalidades sobre nos e sair para qualquer visitante
                        .requestMatchers("/", "/home", "/funcionalidades", "/sobre-nos", "/logout").permitAll()
                        // liberar telas de login permite entrar como aluno professor ou administrador
                        .requestMatchers("/login", "/loginAluno", "/loginProfessor", "/admin/login").permitAll()
                        // liberar cadastro permite criar conta de aluno ou professor
                        .requestMatchers("/cadastrar", "/cadastrarAluno", "/cadastrarAlunoVerificado",
                                "/cadastrarProfessor", "/cadastrarProfessorVerificado").permitAll()
                        // liberar verificacao por email permite digitar o codigo de dois fatores antes do login completo
                        .requestMatchers("/verificar", "/verificar/reenviar", "/verificarAluno",
                                "/verificarProfessor", "/verificarAdmin").permitAll()
                        // liberar esqueci a senha permite recuperar a conta sem estar conectado
                        .requestMatchers("/ResetPassword_Verification", "/ResetVerification", "/ResetPassword", "/reset").permitAll()
                        // liberar texto dos termos permite ler os termos de uso antes do cadastro
                        .requestMatchers(HttpMethod.GET, "/termos").permitAll()

                        // restringir ao administrador libera backoffice e documentacao da api apenas para o papel admin
                        .requestMatchers("/admin/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").hasRole("ADMIN")
                        // restringir ao aluno libera o score apenas para o papel aluno
                        .requestMatchers("/score/**").hasRole("ALUNO")
                        // restringir ao professor libera acompanhamento materiais gestao de turmas e arquivos apenas ao papel professor
                        .requestMatchers("/acompanhamento/**", "/meus-conteudos/**", "/criarTurma", "/excluirTurma",
                                "/adicionarPessoas", "/removerPessoas", "/arquivos/upload", "/arquivos/*/excluir",
                                "/turmas/*/dominios").hasRole("PROFESSOR")
                        // restringir aceite dos termos libera aceitar e revogar apenas para alunos e professores
                        .requestMatchers("/termos/aceite", "/termos/revogar").hasAnyRole("ALUNO", "PROFESSOR")

                        // bloquear por padrao exige login em qualquer rota que nao foi liberada acima
                        .anyRequest().authenticated()
                )
                .exceptionHandling(erros -> erros
                        // levar ao login envia visitantes ao login do admin nas rotas administrativas e ao login comum nas demais
                        .authenticationEntryPoint((request, response, excecao) -> {
                            String caminho = request.getRequestURI().substring(request.getContextPath().length());
                            String login = caminho.startsWith("/admin") ? "/admin/login" : "/login";
                            response.sendRedirect(request.getContextPath() + login);
                        })
                );

        return http.build();
    }
}
