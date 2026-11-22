package com.farmacia.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            assertEquals(3, contar(esquema, "medicamento"));
            assertEquals(3, contar(esquema, "lote"));
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

    @Test
    @DisplayName("La tabla venta posee columna metodo_pago con CHECK para valores validos")
    void columnaMetodoPagoExisteYValidaValores() {
        Integer existeColumna = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'venta' AND column_name = 'metodo_pago'
                """, Integer.class);
        assertEquals(1, existeColumna, "Debe existir la columna metodo_pago en la tabla venta");

        assertThrows(org.springframework.dao.DataAccessException.class, () -> {
            jdbcTemplate.update("""
                    INSERT INTO venta (id_venta, nro_comprobante, id_usuario, subtotal, descuento, total, metodo_pago)
                    VALUES (99999, 'B001-99999999', 1, 10.00, 0.00, 10.00, 'INVALIDO')
                    """);
        });
    }

    @Test
    @DisplayName("La tabla usuario posee columna ultimo_acceso que admite NULL")
    void columnaUltimoAccesoExisteYAdmiteNull() {
        Integer existeColumna = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'usuario' AND column_name = 'ultimo_acceso'
                """, Integer.class);
        assertEquals(1, existeColumna, "Debe existir la columna ultimo_acceso en la tabla usuario");

        String esNullable = jdbcTemplate.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'usuario' AND column_name = 'ultimo_acceso'
                """, String.class);
        assertEquals("YES", esNullable, "La columna ultimo_acceso debe admitir NULL");
    }

    @Test
    @DisplayName("V8 crea indices unicos parciales para correo y telefono en cliente y proveedor")
    void indicesUnicosContactoExistenYBloqueanDuplicados() {
        List<String> indices = List.of(
                "uq_cliente_correo", "uq_cliente_telefono",
                "uq_proveedor_correo", "uq_proveedor_telefono");

        for (String indice : indices) {
            Integer existe = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE schemaname = 'public' AND indexname = ?
                    """, Integer.class, indice);
            assertEquals(1, existe, "Debe existir el índice " + indice);
        }

        // Permite múltiples registros con teléfono y correo NULL
        jdbcTemplate.update("""
                INSERT INTO cliente (tipo_documento, numero_documento, nombre_completo, telefono, correo)
                VALUES ('DNI', '99000001', 'Cliente Sin Contacto 1', NULL, NULL),
                       ('DNI', '99000002', 'Cliente Sin Contacto 2', NULL, NULL)
                """);

        // Falla si se intenta insertar teléfono duplicado en cliente
        jdbcTemplate.update("""
                INSERT INTO cliente (tipo_documento, numero_documento, nombre_completo, telefono, correo)
                VALUES ('DNI', '99000003', 'Cliente Con Telefono', '999888777', 'contacto1@test.com')
                """);

        assertThrows(org.springframework.dao.DataAccessException.class, () -> {
            jdbcTemplate.update("""
                    INSERT INTO cliente (tipo_documento, numero_documento, nombre_completo, telefono, correo)
                    VALUES ('DNI', '99000004', 'Cliente Telefono Repetido', '999888777', 'contacto2@test.com')
                    """);
        }, "Debe fallar por índice único parcial uq_cliente_telefono");

        // Falla si se intenta insertar correo duplicado en cliente
        assertThrows(org.springframework.dao.DataAccessException.class, () -> {
            jdbcTemplate.update("""
                    INSERT INTO cliente (tipo_documento, numero_documento, nombre_completo, telefono, correo)
                    VALUES ('DNI', '99000005', 'Cliente Correo Repetido', '999888776', 'contacto1@test.com')
                    """);
        }, "Debe fallar por índice único parcial uq_cliente_correo");
    }

    @Test
    @DisplayName("V8 resuelve duplicados existentes conservando el menor id y anulando los posteriores")
    void migracionResuelveDuplicadosPreexistentes() {
        String esquema = "test_v8_duplicados";
        Flyway flywayV7 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(esquema)
                .locations("classpath:db/migration")
                .target("7")
                .cleanDisabled(false)
                .load();

        try {
            flywayV7.clean();
            assertTrue(flywayV7.migrate().success, "Debe migrar hasta V7 exitosamente");

            // Insertar datos con duplicados en cliente y proveedor antes de V8
            jdbcTemplate.update("INSERT INTO " + esquema + ".cliente (tipo_documento, numero_documento, nombre_completo, telefono, correo) VALUES " +
                    "('DNI', '88880001', 'Cliente Antiguo', '+51 958 145 341', 'Juan@Gmail.com '), " +
                    "('DNI', '88880002', 'Cliente Reciente', '958145341', 'juan@gmail.com')");

            jdbcTemplate.update("INSERT INTO " + esquema + ".proveedor (ruc, razon_social, telefono, correo) VALUES " +
                    "('20111111111', 'Proveedor A', '(01) 456-7890', 'VENTAS@PROV.PE '), " +
                    "('20111111112', 'Proveedor B', '014567890', 'ventas@prov.pe')");

            // Ahora aplicar V8
            Flyway flywayV8 = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas(esquema)
                    .locations("classpath:db/migration")
                    .load();

            assertTrue(flywayV8.migrate().success, "V8 debe aplicarse resolviendo duplicados sin error");

            // Verificar cliente: id menor conserva teléfono '958145341' y correo 'juan@gmail.com', el segundo tiene NULL
            Integer idMinCliente = jdbcTemplate.queryForObject(
                    "SELECT MIN(id_cliente) FROM " + esquema + ".cliente", Integer.class);
            Integer idMaxCliente = jdbcTemplate.queryForObject(
                    "SELECT MAX(id_cliente) FROM " + esquema + ".cliente", Integer.class);

            String telMin = jdbcTemplate.queryForObject(
                    "SELECT telefono FROM " + esquema + ".cliente WHERE id_cliente = ?", String.class, idMinCliente);
            String corMin = jdbcTemplate.queryForObject(
                    "SELECT correo FROM " + esquema + ".cliente WHERE id_cliente = ?", String.class, idMinCliente);
            assertEquals("958145341", telMin);
            assertEquals("juan@gmail.com", corMin);

            String telMax = jdbcTemplate.queryForObject(
                    "SELECT telefono FROM " + esquema + ".cliente WHERE id_cliente = ?", String.class, idMaxCliente);
            String corMax = jdbcTemplate.queryForObject(
                    "SELECT correo FROM " + esquema + ".cliente WHERE id_cliente = ?", String.class, idMaxCliente);
            assertEquals(null, telMax, "El cliente más reciente debe tener teléfono NULL");
            assertEquals(null, corMax, "El cliente más reciente debe tener correo NULL");
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + esquema + " CASCADE");
        }
    }

    private int contar(String esquema, String tabla) {
        // Identificadores constantes de esta prueba (no provienen del usuario).
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + esquema + "." + tabla, Integer.class);
    }
}
