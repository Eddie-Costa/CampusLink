package com.example.CampusLink.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.support.TestPropertySourceUtils;

/** Inicia um PostgreSQL local descartável, sem ler a configuração do banco real. */
public class BancoTesteInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final class Banco {
        static final EmbeddedPostgres PG = iniciar();
        private static EmbeddedPostgres iniciar() {
            try {
                EmbeddedPostgres pg = EmbeddedPostgres.builder().setPort(0).start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { pg.close(); } catch (Exception e) { System.err.println("Falha ao encerrar banco de teste: " + e.getMessage()); }
                }, "encerrar-postgres-testes"));
                new ResourceDatabasePopulator(new ClassPathResource("schema-test.sql")).execute(pg.getPostgresDatabase());
                return pg;
            } catch (Exception e) { throw new IllegalStateException("Não foi possível iniciar PostgreSQL descartável", e); }
        }
    }
    @Override public void initialize(ConfigurableApplicationContext context) {
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "spring.datasource.url=" + Banco.PG.getJdbcUrl("postgres", "postgres"),
                "spring.datasource.username=postgres", "spring.datasource.password=postgres");
    }
}
