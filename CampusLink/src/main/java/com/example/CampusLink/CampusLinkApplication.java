// projeto campuslink organiza turmas materiais eventos e apoio aos estudos com acesso por perfil
package com.example.CampusLink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

// iniciar aplicacao esta classe carrega os componentes que fazem o campuslink funcionar
@SpringBootApplication
public class CampusLinkApplication {

    // iniciar campuslink este metodo coloca a aplicacao em funcionamento
    public static void main(String[] args) {
        SpringApplication.run(CampusLinkApplication.class, args);
    }

    // preparar inicio este metodo recebe a configuracao do banco e retorna uma tarefa vazia
    @Bean
    CommandLineRunner testarEnv(@Value("${DB_URL:NAO_ENCONTRADO}") String dbUrl) {
        return args -> {};
    }
}
