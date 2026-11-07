// envio de emails configura a execucao das mensagens em tarefas separadas
package com.example.CampusLink.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

// tarefas de email esta classe define limites de trabalho e mantem o contexto dos registros
@Configuration
public class EmailAsyncConfig {

    // configurar envio paralelo este metodo prepara as tarefas de email e preserva os dados de registro
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
                // executar tarefa de email aplica o contexto de registro e restaura o anterior ao terminar
                try {
                    // preparar contexto do email limpa os dados ausentes ou copia os dados recebidos
                    if (contexto == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(contexto);
                    }
                    tarefa.run();
                } finally {
                    // restaurar contexto anterior limpa o registro vazio ou recupera os dados da tarefa anterior
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
