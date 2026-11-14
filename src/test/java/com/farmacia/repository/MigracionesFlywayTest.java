package com.farmacia.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que Flyway sea la única fuente del esquema: las migraciones versionadas se aplican
 * al iniciar el contexto y los datos de demostración (db/demo) solo se cargan cuando se piden.
 */
class MigracionesFlywayTest extends AbstractRepositorioIntegrationTest {

    private static final List<String> TABLAS = List.of(
            "usuario", "medicamento", "lote", "proveedor", "cliente", "orden_compra",
            "detalle_orden_compra", "venta", "detalle_venta", "movimiento_inventario",
            "detalle_movimiento");

    @Test
    @DisplayName("Flyway aplica V1 con éxito y crea las 11 tablas del modelo")
    void flywayAplicaElEsquemaInicial() {
        Integer v1Exitosa = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success",
                Integer.class);
        assertEquals(1, v1Exitosa, "La migración V1 debe estar registrada como exitosa");

        Integer fallidas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        assertEquals(0, fallidas, "No debe haber migraciones fallidas");

        for (String tabla : TABLAS) {
            Integer existe = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = ?
                    """, Integer.class, tabla);
            assertEquals(1, existe, "Debe existir la tabla " + tabla);
        }
    }

    @Test
    @DisplayName("Sin el perfil demo no se cargan datos ficticios")
    void sinPerfilDemoNoHayMigracionRepetible() {
        Integer repetibles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NULL", Integer.class);
        assertEquals(0, repetibles, "La migración repetible de demo no debe aplicarse por defecto");
    }

    @Test
    @DisplayName("Con db/demo, una base nueva recibe el esquema y los datos demo; repetir no duplica")
    void datosDemoSeCarganEnBaseNuevaYSonIdempotentes() {
        String esquema = "demo_flyway";
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(esquema)
                .locations("classpath:db/migration", "classpath:db/demo")
                .cleanDisabled(false)
                .load();
        try {
            flyway.clean();
            assertTrue(flyway.migrate().success, "La migración con datos demo debe completarse");

            assertEquals(4, contar(esquema, "usuario"));
            assertEquals(2, contar(esquema, "medicamento"));
            assertEquals(1, contar(esquema, "lote"));
            assertEquals(2, contar(esquema, "proveedor"));
            assertEquals(1, contar(esquema, "orden_compra"));

            // Segunda ejecución: no hay nada pendiente y los datos no se duplican.
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(4, contar(esquema, "usuario"));
        } finally {
            flyway.clean();
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + esquema + " CASCADE");
        }
    }

    private int contar(String esquema, String tabla) {
        // Identificadores constantes de esta prueba (no provienen del usuario).
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + esquema + "." + tabla, Integer.class);
    }
}
