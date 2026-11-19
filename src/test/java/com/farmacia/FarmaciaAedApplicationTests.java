package com.farmacia;

import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FarmaciaAedApplicationTests extends AbstractRepositorioIntegrationTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertNotNull(context, "El contexto de Spring Boot debe cargar correctamente");
	}

	@Test
	@DisplayName("Las propiedades de src/main/resources/application.properties están disponibles en el contexto de pruebas")
	void propiedadesPrincipalesDisponiblesEnPruebas() {
		String appName = context.getEnvironment().getProperty("spring.application.name");
		assertEquals("farmacia-aed", appName, "spring.application.name debe cargarse desde application.properties principal");
		String whitelabel = context.getEnvironment().getProperty("server.error.whitelabel.enabled");
		assertEquals("false", whitelabel, "server.error.whitelabel.enabled debe cargarse desde application.properties principal");
	}

}
