package com.farmacia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class VerificacionBaseDatos implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificacionBaseDatos.class);

    private final JdbcTemplate jdbcTemplate;

    public VerificacionBaseDatos(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        String baseDatos = jdbcTemplate.queryForObject(
                "SELECT current_database()",
                String.class);

        log.info("Conexion PostgreSQL correcta: {}", baseDatos);
    }
}
