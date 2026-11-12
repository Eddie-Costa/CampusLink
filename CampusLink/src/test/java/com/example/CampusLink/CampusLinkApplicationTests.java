// verificacao de inicializacao exercita o carregamento da aplicacao
package com.example.CampusLink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

// teste de inicio esta classe confirma que os componentes do campuslink podem ser criados
@Tag("integration")
@SpringBootTest
class CampusLinkApplicationTests {

	// testar inicio da aplicacao este teste verifica se os componentes carregam sem falha
	@Test
	void contextLoads() {
	}

}
