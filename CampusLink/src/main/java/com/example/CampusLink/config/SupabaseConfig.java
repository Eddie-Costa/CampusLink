// conexao de arquivos configura o acesso ao armazenamento externo
package com.example.CampusLink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// acesso ao armazenamento esta classe prepara o endereco e a chave usados nas chamadas
@Configuration
public class SupabaseConfig {

    // configurar armazenamento este metodo prepara o acesso aos arquivos com a chave informada
    @Bean
    public RestClient supabaseRestClient(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey) {

        return RestClient.builder()
                .baseUrl(supabaseUrl)
                .defaultHeader("apikey", secretKey)
                .defaultHeaders(headers -> {

                    // enviar chave antiga inclui a autorizacao adicional quando a chave usa o formato anterior
                    if (secretKey.startsWith("eyJ")) {
                        headers.setBearerAuth(secretKey);
                    }
                })
                .build();
    }
}
