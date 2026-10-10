package com.example.CampusLink;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.ContextConfiguration(initializers = com.example.CampusLink.support.BancoTesteInitializer.class)
@SpringBootTest
class CampusLinkApplicationTests {
	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.context.ApplicationContext context;

	@Test
	void contextLoads() {
		org.junit.jupiter.api.Assertions.assertNotNull(
				context.getBean(com.example.CampusLink.service.EventoService.class));
	}

}
