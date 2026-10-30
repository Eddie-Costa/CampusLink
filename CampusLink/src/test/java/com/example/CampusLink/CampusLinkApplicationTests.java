package com.example.CampusLink;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CampusLinkApplicationTests {

	@Autowired
	private Environment ambiente;

	@Test
	void contexto_comPerfilDeTeste_deveUsarBancoDescartavel() {
		// confere se o contexto usa o banco de teste sem depender do arquivo de desenvolvimento
		assertThat(ambiente.getActiveProfiles()).contains("test");
		assertThat(ambiente.getProperty("spring.datasource.url")).startsWith("jdbc:tc:postgresql:");
	}

}
