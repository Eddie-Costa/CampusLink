package com.example.CampusLink.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@Configuration
public class EmailAsyncConfig {

    @Bean(name = "emailTaskExecutor")
    public ThreadPoolTaskExecutor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.setTaskDecorator(tarefa -> {
            Map<String, String> contexto = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> contextoAnterior = MDC.getCopyOfContextMap();
                try {
                    if (contexto == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(contexto);
                    }
                    tarefa.run();
                } finally {
                    if (contextoAnterior == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(contextoAnterior);
                    }
                }
            };
        });
        return executor;
    }
}
