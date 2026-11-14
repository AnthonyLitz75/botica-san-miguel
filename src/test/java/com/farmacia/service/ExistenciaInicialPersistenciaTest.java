package com.farmacia.service;

import com.farmacia.dto.ExistenciaInicialFormulario;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class ExistenciaInicialPersistenciaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MovimientoInventarioServicio servicio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpiarTriggerDeError() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_test_error_detalle ON detalle_movimiento");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fn_test_error_detalle()");
    }

    private record DatosPrueba(Integer idUsuario, String nombreUsuario, Integer idMedicamento) {}

    private DatosPrueba prepararDatos(String prefijo) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String username = "almacen_" + prefijo + "_" + sufijo;

        Integer idUsuario = jdbcTemplate.queryForObject("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES ('Almacenero Test', ?, 'hash', 'ALMACENERO', true)
                RETURNING id_usuario
                """, Integer.class, username);

        Integer idMed = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento (codigo, nombre, concentracion, presentacion, unidad_control, precio_venta, stock_minimo, activo)
                VALUES (?, ?, '500mg', 'TABLETA', 'CAJA', 2.50, 10, true)
                RETURNING id_medicamento
                """, Integer.class, "MED-" + sufijo, "Medicamento " + sufijo);

        return new DatosPrueba(idUsuario, username, idMed);
    }

    @Test
    @DisplayName("CA-1.1: Registrar existencia inicial de lote nuevo persiste lote y movimiento ENTRADA sin orden")
    void registrarExistenciaInicial_loteNuevo_persisteLoteYMovimiento() {
        var datos = prepararDatos("E1");
        LocalDate vencimiento = LocalDate.now().plusMonths(6);
        String numLote = "LOTE-INI-01";

        var form = new ExistenciaInicialFormulario(
                datos.idMedicamento(),
                numLote,
                vencimiento,
                20,
                "Existencias iniciales",
                false);

        Integer idMovimiento = servicio.registrarExistenciaInicial(datos.nombreUsuario(), form);
        assertNotNull(idMovimiento);
        assertTrue(idMovimiento > 0);

        // 1. Verificar lote creado con stock 20
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_medicamento = ? AND numero_lote = ?",
                Integer.class, datos.idMedicamento(), numLote);
        assertEquals(20, stock);

        // 2. Verificar cabecera movimiento ENTRADA sin id_orden
        var mov = jdbcTemplate.queryForMap(
                "SELECT tipo, id_orden, id_venta, motivo FROM movimiento_inventario WHERE id_movimiento = ?",
                idMovimiento);
        assertEquals("ENTRADA", mov.get("tipo"));
        assertEquals(null, mov.get("id_orden"));
        assertEquals(null, mov.get("id_venta"));
        assertEquals("Existencias iniciales", mov.get("motivo"));

        // 3. Verificar detalle_movimiento
        Integer cantidadDetalle = jdbcTemplate.queryForObject(
                "SELECT cantidad FROM detalle_movimiento WHERE id_movimiento = ?",
                Integer.class, idMovimiento);
        assertEquals(20, cantidadDetalle);
    }

    @Test
    @DisplayName("CA-1.2: Lote existente con misma fecha y confirmarAcumulacion=true incrementa existencias")
    void registrarExistenciaInicial_loteExistenteConConfirmacion_acumulaStock() {
        var datos = prepararDatos("E2");
        LocalDate vencimiento = LocalDate.now().plusMonths(4);
        String numLote = "LOTE-ACUM-01";

        // Carga 1: 20 unidades
        var form1 = new ExistenciaInicialFormulario(datos.idMedicamento(), numLote, vencimiento, 20, "Carga 1", false);
        servicio.registrarExistenciaInicial(datos.nombreUsuario(), form1);

        // Carga 2: 15 unidades confirmando acumulación
        var form2 = new ExistenciaInicialFormulario(datos.idMedicamento(), numLote, vencimiento, 15, "Carga complementaria", true);
        Integer idMov2 = servicio.registrarExistenciaInicial(datos.nombreUsuario(), form2);
        assertNotNull(idMov2);

        // Stock debe ser 35
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_medicamento = ? AND numero_lote = ?",
                Integer.class, datos.idMedicamento(), numLote);
        assertEquals(35, stock);

        // Se deben haber registrado 2 movimientos independientes
        Integer countMov = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_usuario = ?",
                Integer.class, datos.idUsuario());
        assertEquals(2, countMov);
    }

    @Test
    @DisplayName("CA-1.5: Atomicidad / Rollback si falla inserción del detalle no deja lote ni cabecera")
    void registrarExistenciaInicial_errorEnDetalle_revierteLoteYCabecera() {
        var datos = prepararDatos("E3");
        LocalDate vencimiento = LocalDate.now().plusMonths(5);
        String numLote = "LOTE-ROLLBACK-01";

        // Instalar trigger que falla cuando cantidad == 9999
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fn_test_error_detalle() RETURNS trigger AS $$
                BEGIN
                    IF NEW.cantidad = 9999 THEN
                        RAISE EXCEPTION 'Fallo forzado de inserción en detalle_movimiento';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;

                CREATE TRIGGER trg_test_error_detalle
                BEFORE INSERT ON detalle_movimiento
                FOR EACH ROW EXECUTE FUNCTION fn_test_error_detalle();
                """);

        var form = new ExistenciaInicialFormulario(datos.idMedicamento(), numLote, vencimiento, 9999, "Fallo forzado", false);

        assertThrows(DataAccessException.class, () ->
                servicio.registrarExistenciaInicial(datos.nombreUsuario(), form));

        // Verificar evidencia de ROLLBACK en PostgreSQL real
        Integer countLotes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lote WHERE id_medicamento = ? AND numero_lote = ?",
                Integer.class, datos.idMedicamento(), numLote);
        assertEquals(0, countLotes, "EVIDENCIA ROLLBACK: El lote no debe existir tras fallar el detalle");

        Integer countMov = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_usuario = ?",
                Integer.class, datos.idUsuario());
        assertEquals(0, countMov, "EVIDENCIA ROLLBACK: No debe quedar cabecera de movimiento huérfana");
    }
}
