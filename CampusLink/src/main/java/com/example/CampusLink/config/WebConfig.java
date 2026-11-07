// configuracao web registra os interceptadores que rodam antes das paginas
package com.example.CampusLink.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// controle dos interceptadores esta classe ativa a verificacao do aceite dos termos de uso
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TermosInterceptor termosInterceptor;

    // preparar configuracao web este construtor recebe o interceptador dos termos
    public WebConfig(TermosInterceptor termosInterceptor) {
        this.termosInterceptor = termosInterceptor;
    }

    // registrar interceptadores este metodo faz o bloqueio por termos valer para todas as rotas
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(termosInterceptor);
    }
}
