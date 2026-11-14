package com.farmacia.service;

import com.farmacia.dto.SalidaInventarioFormulario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class MovimientoInventarioServicioTransaccionalTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MovimientoInventarioServicio servicio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // El esquema lo crea Flyway al iniciar el contexto (src/main/resources/db/migration).

    @AfterEach
    void limpiarTriggerDeSeguridad() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_test_error_detalle ON detalle_movimiento");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fn_test_error_detalle()");
    }

    // -------------------------------------------------------------------------
    // Escenario 1: Salida válida con persistencia real y commit en PostgreSQL
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 1: Salida válida persiste cabecera, detalle y descuenta stock en PostgreSQL")
    void salidaValidaPersisteEnPostgresqlYActualizaStock() {
        var datos = prepararDatosPrueba("E1", 30);

        Integer idMovimiento = servicio.registrarSalida(
                datos.idLote(),
                datos.nombreUsuario(),
                new SalidaInventarioFormulario(8, "Ajuste por merma controlada"));

        assertNotNull(idMovimiento, "El servicio debe retornar el identificador del movimiento generado");
        assertTrue(idMovimiento > 0, "El ID del movimiento debe ser un entero positivo");

        // 1. Verificar descuento real en tabla lote
        Integer stockActual = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_lote = ?",
                Integer.class, datos.idLote());
        assertEquals(22, stockActual, "El stock en PostgreSQL debe haberse descontado de 30 a 22");

        // 2. Verificar inserción en cabecera movimiento_inventario
        Integer cabeceras = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_movimiento = ? AND id_usuario = ? AND tipo = 'SALIDA'",
                Integer.class, idMovimiento, datos.idUsuario());
        assertEquals(1, cabeceras, "Debe existir exactamente una cabecera de movimiento registrada");

        // 3. Verificar inserción en detalle_movimiento
        Integer cantidadDetalle = jdbcTemplate.queryForObject(
                "SELECT cantidad FROM detalle_movimiento WHERE id_movimiento = ? AND id_lote = ?",
                Integer.class, idMovimiento, datos.idLote());
        assertEquals(8, cantidadDetalle, "La cantidad registrada en el detalle debe ser 8");
    }

    // -------------------------------------------------------------------------
    // Escenario 2: Stock insuficiente no modifica existencias ni registra movimientos
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 2: Intento de salida con stock insuficiente es rechazado sin escrituras")
    void stockInsuficienteRechazaOperacionYSinModificarBaseDeDatos() {
        var datos = prepararDatosPrueba("E2", 10);

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                servicio.registrarSalida(
                        datos.idLote(),
                        datos.nombreUsuario(),
                        new SalidaInventarioFormulario(15, "Intento de sobre-salida")));

        assertEquals("No hay existencias suficientes para registrar la salida", excepcion.getMessage());

        // 1. Verificar que el stock en PostgreSQL permanece inalterado
        Integer stockActual = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_lote = ?",
                Integer.class, datos.idLote());
        assertEquals(10, stockActual, "El stock del lote debe mantenerse intacto en 10");

        // 2. Verificar que no se insertó ninguna cabecera
        Integer cabeceras = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_usuario = ?",
                Integer.class, datos.idUsuario());
        assertEquals(0, cabeceras, "No debe haberse insertado ninguna cabecera");

        // 3. Verificar que no se insertó ningún detalle
        Integer detalles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalle_movimiento WHERE id_lote = ?",
                Integer.class, datos.idLote());
        assertEquals(0, detalles, "No debe haberse insertado ningún detalle");
    }

    // -------------------------------------------------------------------------
    // Escenarios 3 y 4: Fallo en el tercer paso (detalle) provoca ROLLBACK total
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenarios 3 y 4: Fallo provocado en detalle revierte atómicamente stock y cabecera")
    void falloEnDetalleProvocaRollbackTotalDeStockYCabecera() {
        var datos = prepararDatosPrueba("E3_ROLLBACK", 25);

        try {
            // Instalar trigger temporal que falla deliberadamente en la inserción de detalle_movimiento
            jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fn_test_error_detalle()
                RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'Simulación de error forzado en inserción de detalle_movimiento';
                END;
                $$ LANGUAGE plpgsql;

                CREATE TRIGGER trg_test_error_detalle
                BEFORE INSERT ON detalle_movimiento
                FOR EACH ROW EXECUTE FUNCTION fn_test_error_detalle();
                """);

            // Invocar el servicio a través del proxy de Spring Boot (@Transactional)
            // Paso 1 (loteRepositorio.descontarStock) -> Ejecuta UPDATE en PostgreSQL
            // Paso 2 (repositorio.registrarSalida)   -> Ejecuta INSERT en movimiento_inventario
            // Paso 3 (repositorio.registrarDetalle)  -> Dispara el trigger de PostgreSQL y falla
            assertThrows(DataAccessException.class, () ->
                    servicio.registrarSalida(
                            datos.idLote(),
                            datos.nombreUsuario(),
                            new SalidaInventarioFormulario(7, "Salida con fallo de detalle")));

        } finally {
            // Limpieza inmediata del trigger para auditar la base de datos sin interferencias
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_test_error_detalle ON detalle_movimiento");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fn_test_error_detalle()");
        }

        // =====================================================================
        // AUDITORÍA POST-ROLLBACK CONTRA POSTGRESQL REAL:
        // =====================================================================

        // 1. EVIDENCIA DE ROLLBACK EN STOCK: El UPDATE del paso 1 debió ser deshecho por PostgreSQL
        Integer stockFinal = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_lote = ?",
                Integer.class, datos.idLote());
        assertEquals(25, stockFinal,
                "EVIDENCIA ROLLBACK: El stock de lote debe restaurarse a su valor original (25) tras abortar la transacción");

        // 2. EVIDENCIA DE ROLLBACK EN CABECERA: El INSERT del paso 2 debió ser revertido
        Integer cabeceras = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_usuario = ?",
                Integer.class, datos.idUsuario());
        assertEquals(0, cabeceras,
                "EVIDENCIA ROLLBACK: La cabecera insertada en el paso 2 debe haber sido eliminada por el rollback");

        // 3. EVIDENCIA DE AUSENCIA DE DETALLE: No deben existir filas parciales ni huérfanas
        Integer detalles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalle_movimiento WHERE id_lote = ?",
                Integer.class, datos.idLote());
        assertEquals(0, detalles,
                "EVIDENCIA ROLLBACK: No deben quedar registros parciales en detalle_movimiento");
    }

    // -------------------------------------------------------------------------
    // Escenario 5: Aislamiento estricto de datos entre operaciones consecutivas
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 5: Operaciones independientes mantienen saldos y registros completamente aislados")
    void aislamientoDeDatosEntrePruebasConsecutivas() {
        var datosA = prepararDatosPrueba("E5_A", 40);
        var datosB = prepararDatosPrueba("E5_B", 50);

        Integer idMovA = servicio.registrarSalida(datosA.idLote(), datosA.nombreUsuario(),
                new SalidaInventarioFormulario(10, "Salida lote A"));
        Integer idMovB = servicio.registrarSalida(datosB.idLote(), datosB.nombreUsuario(),
                new SalidaInventarioFormulario(20, "Salida lote B"));

        assertNotNull(idMovA);
        assertNotNull(idMovB);

        // Validar que lote A tiene 30 y lote B tiene 30 sin solapamiento
        assertEquals(30, jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_lote = ?", Integer.class, datosA.idLote()));
        assertEquals(30, jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_lote = ?", Integer.class, datosB.idLote()));

        // Validar conteos independientes por usuario
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_usuario = ?", Integer.class, datosA.idUsuario()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_usuario = ?", Integer.class, datosB.idUsuario()));
    }

    // -------------------------------------------------------------------------
    // Método auxiliar para preparar fixtures sintéticos aislados
    // -------------------------------------------------------------------------
    private DatosPrueba prepararDatosPrueba(String prefijo, int stockInicial) {
        String sufijo = prefijo + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String nombreUsuario = "usr_" + sufijo;
        String codigoMed = "MED_" + sufijo;
        String numLote = "LT_" + sufijo;

        Integer idUsuario = jdbcTemplate.queryForObject("""
                INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES ('Usuario Test', ?, 'hash', 'ALMACENERO', true)
                RETURNING id_usuario
                """, Integer.class, nombreUsuario);

        Integer idMedicamento = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento(codigo, nombre, presentacion, unidad_control, precio_venta, stock_minimo)
                VALUES (?, 'Medicamento Test', 'Caja', 'CAJA', 10.00, 5)
                RETURNING id_medicamento
                """, Integer.class, codigoMed);

        Integer idLote = jdbcTemplate.queryForObject("""
                INSERT INTO lote(id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
                VALUES (?, ?, ?, ?)
                RETURNING id_lote
                """, Integer.class, idMedicamento, numLote, LocalDate.now().plusMonths(12), stockInicial);

        return new DatosPrueba(idUsuario, nombreUsuario, idMedicamento, idLote, stockInicial);
    }

    private record DatosPrueba(
            Integer idUsuario,
            String nombreUsuario,
            Integer idMedicamento,
            Integer idLote,
            int stockInicial) {
    }
}
