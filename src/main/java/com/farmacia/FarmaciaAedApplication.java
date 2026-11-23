package com.farmacia;

import com.farmacia.util.FormatoUltimoAcceso;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FarmaciaAedApplication {

	public static void main(String[] args) {
		// La botica opera en Lima: fija la zona del JVM antes de abrir conexiones, así la sesión de
		// PostgreSQL y cualquier fecha sin reloj explícito usan la hora de Lima aunque el contenedor esté en UTC.
		TimeZone.setDefault(TimeZone.getTimeZone(FormatoUltimoAcceso.ZONA_LIMA));
		SpringApplication.run(FarmaciaAedApplication.class, args);
	}

}
