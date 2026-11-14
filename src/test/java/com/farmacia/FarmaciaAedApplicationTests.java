package com.farmacia;

import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FarmaciaAedApplicationTests extends AbstractRepositorioIntegrationTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertNotNull(context, "El contexto de Spring Boot debe cargar correctamente");
	}

}
