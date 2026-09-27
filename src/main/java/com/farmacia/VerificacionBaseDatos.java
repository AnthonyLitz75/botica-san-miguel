package com.farmacia;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class VerificacionBaseDatos implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public VerificacionBaseDatos(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        String baseDatos = jdbcTemplate.queryForObject(
            "SELECT current_database()",
            String.class
        );

        System.out.println("Conexion PostgreSQL correcta: " + baseDatos);
    }
}