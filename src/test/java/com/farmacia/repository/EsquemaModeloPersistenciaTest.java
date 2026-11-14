package com.farmacia.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec 002 (US-2, US-3, US-4): movimiento_inventario en 3FN, coherencia movimiento-detalle
 * garantizada por la base (trigger) e índices. Cada prueba corre en una transacción que se revierte.
 */
@Transactional
class EsquemaModeloPersistenciaTest extends AbstractRepositorioIntegrationTest {

    private int idUsuario;
    private int idOrden;
    private int idDetalleOrden;
    private int idLote;
    private int idLoteOtroMedicamento;
    private int idVenta;

    @BeforeEach
    void crearDatos() {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        idUsuario = jdbcTemplate.queryForObject("""
                INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol)
                VALUES ('Prueba esquema', ?, 'x', 'ALMACENERO') RETURNING id_usuario
                """, Integer.class, "esq-" + sufijo);
        int idProveedor = jdbcTemplate.queryForObject("""
                INSERT INTO proveedor(ruc, razon_social) VALUES (?, 'Proveedor esquema')
                RETURNING id_proveedor
                """, Integer.class,
                "20" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000)));
        int idMedicamento = insertarMedicamento("ESQ-A-" + sufijo);
        int idOtroMedicamento = insertarMedicamento("ESQ-B-" + sufijo);
        idLote = insertarLote(idMedicamento, "L-A-" + sufijo);
        idLoteOtroMedicamento = insertarLote(idOtroMedicamento, "L-B-" + sufijo);
        idOrden = jdbcTemplate.queryForObject("""
                INSERT INTO orden_compra(numero_orden, id_usuario, id_proveedor, total_estimado)
                VALUES (?, ?, ?, 10) RETURNING id_orden
                """, Integer.class, "OC-ESQ-" + sufijo, idUsuario, idProveedor);
        idDetalleOrden = jdbcTemplate.queryForObject("""
                INSERT INTO detalle_orden_compra(id_orden, id_medicamento, cantidad_solicitada,
                                                 costo_unitario, importe_estimado)
                VALUES (?, ?, 10, 1, 10) RETURNING id_detalle_orden
                """, Integer.class, idOrden, idMedicamento);
        idVenta = jdbcTemplate.queryForObject("""
                INSERT INTO venta(id_usuario, subtotal, descuento_total, total, numero_comprobante)
                VALUES (?, 0, 0, 0, ?) RETURNING id_venta
                """, Integer.class, idUsuario, "T-" + sufijo);
    }

    // ---------------------------------------------------------------- US-2: 3FN

    @Test
    @DisplayName("CA-2.1: movimiento_inventario ya no tiene la columna id_proveedor")
    void movimientoSinColumnaIdProveedor() {
        Integer columnas = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'movimiento_inventario'
                  AND column_name = 'id_proveedor'
                """, Integer.class);
        assertEquals(0, columnas);
    }

    @Test
    @DisplayName("CA-2.2: una ENTRADA con id_venta se rechaza por CHECK")
    void entradaConVentaFalla() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO movimiento_inventario(id_usuario, id_venta, tipo, motivo)
                VALUES (?, ?, 'ENTRADA', 'Prueba')
                """, idUsuario, idVenta));
    }

    @Test
    @DisplayName("CA-2.2: una SALIDA con id_orden se rechaza por CHECK")
    void salidaConOrdenFalla() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO movimiento_inventario(id_usuario, id_orden, tipo, motivo)
                VALUES (?, ?, 'SALIDA', 'Prueba')
                """, idUsuario, idOrden));
    }

    @Test
    @DisplayName("CA-2.2: una ENTRADA con id_orden y sin proveedor se acepta")
    void entradaConOrdenSinProveedorPasa() {
        assertNotNull(insertarMovimientoRecepcion());
    }

    // ---------------------------------------------------------------- US-3: trigger

    @Test
    @DisplayName("CA-3.1: un detalle de recepción sin id_detalle_orden se rechaza")
    void detalleRecepcionSinLineaFalla() {
        int idMovimiento = insertarMovimientoRecepcion();
        var error = assertThrows(DataAccessException.class, () -> insertarDetalle(idMovimiento, idLote, null));
        assertTrue(mensajeCompleto(error).contains("exige id_detalle_orden"), mensajeCompleto(error));
    }

    @Test
    @DisplayName("CA-3.1: un detalle de recepción con línea y lote correctos se acepta")
    void detalleRecepcionCorrectoPasa() {
        int idMovimiento = insertarMovimientoRecepcion();
        assertEquals(1, insertarDetalle(idMovimiento, idLote, idDetalleOrden));
    }

    @Test
    @DisplayName("CA-3.1: un detalle de recepción con lote de otro medicamento se rechaza")
    void detalleRecepcionConLoteDeOtroMedicamentoFalla() {
        int idMovimiento = insertarMovimientoRecepcion();
        var error = assertThrows(DataAccessException.class,
                () -> insertarDetalle(idMovimiento, idLoteOtroMedicamento, idDetalleOrden));
        assertTrue(mensajeCompleto(error).contains("no corresponden a la orden"), mensajeCompleto(error));
    }

    @Test
    @DisplayName("CA-3.2: un detalle de salida con id_detalle_orden se rechaza")
    void detalleSalidaConLineaFalla() {
        int idMovimiento = insertarMovimientoSalida();
        var error = assertThrows(DataAccessException.class,
                () -> insertarDetalle(idMovimiento, idLote, idDetalleOrden));
        assertTrue(mensajeCompleto(error).contains("Solo una recepcion"), mensajeCompleto(error));
    }

    @Test
    @DisplayName("CA-3.2: un detalle de salida sin id_detalle_orden se acepta")
    void detalleSalidaSinLineaPasa() {
        int idMovimiento = insertarMovimientoSalida();
        assertEquals(1, insertarDetalle(idMovimiento, idLote, null));
    }

    // ---------------------------------------------------------------- US-4: índices

    @Test
    @DisplayName("CA-4.1: existen los 10 índices idx_ de V2, incluido el parcial idx_lote_fefo")
    void existenLosDiezIndices() {
        List<String> esperados = List.of(
                "idx_lote_fefo", "idx_detalle_venta_venta", "idx_detalle_venta_lote",
                "idx_detalle_movimiento_movimiento", "idx_detalle_movimiento_lote",
                "idx_detalle_movimiento_linea", "idx_movimiento_orden", "idx_movimiento_fecha",
                "idx_venta_fecha", "idx_orden_estado");
        List<String> existentes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public' AND indexname LIKE 'idx\\_%'
                ORDER BY indexname
                """, String.class);
        // Las migraciones posteriores (V4 auditoría, V5 categorías) añaden sus propios índices idx_*:
        // aquí se exige que los 10 de V2 estén presentes, no que sean los únicos.
        assertTrue(existentes.containsAll(esperados), "Faltan índices de V2: " + existentes);

        String definicionFefo = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_lote_fefo'", String.class);
        assertTrue(definicionFefo.contains("WHERE (stock_actual > 0)"), definicionFefo);
    }

    // ---------------------------------------------------------------- auxiliares

    private int insertarMedicamento(String codigo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO medicamento(codigo, nombre, presentacion, unidad_control, precio_venta)
                VALUES (?, 'Medicamento esquema', 'Caja', 'CAJA', 1) RETURNING id_medicamento
                """, Integer.class, codigo);
    }

    private int insertarLote(int idMedicamento, String numeroLote) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO lote(id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
                VALUES (?, ?, ?, 10) RETURNING id_lote
                """, Integer.class, idMedicamento, numeroLote, LocalDate.of(2099, 1, 1));
    }

    private Integer insertarMovimientoRecepcion() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO movimiento_inventario(id_usuario, id_orden, tipo, motivo)
                VALUES (?, ?, 'ENTRADA', 'Recepción de prueba') RETURNING id_movimiento
                """, Integer.class, idUsuario, idOrden);
    }

    private Integer insertarMovimientoSalida() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO movimiento_inventario(id_usuario, tipo, motivo)
                VALUES (?, 'SALIDA', 'Salida de prueba') RETURNING id_movimiento
                """, Integer.class, idUsuario);
    }

    private int insertarDetalle(int idMovimiento, int lote, Integer idLinea) {
        return jdbcTemplate.update("""
                INSERT INTO detalle_movimiento(id_movimiento, id_lote, id_detalle_orden, cantidad)
                VALUES (?, ?, ?, 1)
                """, idMovimiento, lote, idLinea);
    }

    private static String mensajeCompleto(Throwable error) {
        StringBuilder texto = new StringBuilder();
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            texto.append(actual.getMessage()).append(" | ");
        }
        return texto.toString();
    }
}
