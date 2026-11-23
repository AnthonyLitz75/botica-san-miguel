package com.farmacia.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Simula la aplicación desplegada en un contenedor en UTC: el JVM arranca en UTC, así que el pool de
 * conexiones abre sesiones de PostgreSQL en UTC. Aun así, la fecha_hora que la aplicación guarda en
 * ventas, movimientos y órdenes debe ser la hora de Lima.
 * La propiedad de prueba obliga a crear un contexto propio (no se comparte con otras clases).
 */
@TestPropertySource(properties = "farmacia.prueba.contexto=jvm-en-utc")
class ZonaHorariaPersistenciaTest extends AbstractRepositorioIntegrationTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Duration TOLERANCIA = Duration.ofMinutes(2);
    private static TimeZone zonaOriginal;

    @Autowired
    private VentaRepositorio ventaRepositorio;

    @Autowired
    private MovimientoInventarioRepositorio movimientoRepositorio;

    @Autowired
    private OrdenCompraRepositorio ordenCompraRepositorio;

    private int idUsuario;
    private int idProveedor;

    @BeforeAll
    static void arrancarJvmEnUtc() {
        zonaOriginal = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    static void restaurarZona() {
        TimeZone.setDefault(zonaOriginal);
    }

    @BeforeEach
    void preparar() {
        limpiarBaseDeDatos();
        idUsuario = jdbcTemplate.queryForObject("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol)
                VALUES ('Vendedor Zona', 'vendedor_zona', 'hash', 'VENDEDOR') RETURNING id_usuario
                """, Integer.class);
        idProveedor = jdbcTemplate.queryForObject("""
                INSERT INTO proveedor (ruc, razon_social) VALUES ('20999888771', 'Proveedor Zona')
                RETURNING id_proveedor
                """, Integer.class);
    }

    @AfterEach
    void limpiar() {
        limpiarBaseDeDatos();
    }

    @Test
    @DisplayName("Precondición: la sesión de PostgreSQL de este contexto está en UTC, como en un contenedor")
    void sesionDeBaseEnUtc() {
        assertThat(jdbcTemplate.queryForObject("SHOW TimeZone", String.class)).isIn("UTC", "Etc/UTC");
    }

    @Test
    @DisplayName("(c) La fecha_hora de una venta registrada es la hora actual de Lima")
    void ventaGuardaHoraDeLima() {
        int idVenta = ventaRepositorio.insertar(idUsuario, null, new BigDecimal("10.00"), BigDecimal.ZERO,
                new BigDecimal("10.00"), "B001-99000001");

        assertCercaDeLaHoraDeLima(fechaHora("venta", "id_venta", idVenta));
    }

    @Test
    @DisplayName("(c) La fecha_hora de un movimiento de inventario es la hora actual de Lima")
    void movimientoGuardaHoraDeLima() {
        int idMovimiento = movimientoRepositorio.registrarSalida(idUsuario, "Prueba de zona horaria");

        assertCercaDeLaHoraDeLima(fechaHora("movimiento_inventario", "id_movimiento", idMovimiento));
    }

    @Test
    @DisplayName("(c) La fecha_hora de una orden de compra es la hora actual de Lima")
    void ordenGuardaHoraDeLima() {
        int idOrden = ordenCompraRepositorio.guardar("OC-ZONA-0001", idUsuario, idProveedor,
                new BigDecimal("50.00"), "Prueba de zona horaria");

        assertCercaDeLaHoraDeLima(fechaHora("orden_compra", "id_orden", idOrden));
    }

    private LocalDateTime fechaHora(String tabla, String columnaId, int id) {
        // Identificadores constantes de esta prueba (no provienen del usuario).
        return jdbcTemplate.queryForObject(
                "SELECT fecha_hora FROM " + tabla + " WHERE " + columnaId + " = ?", LocalDateTime.class, id);
    }

    private static void assertCercaDeLaHoraDeLima(LocalDateTime guardada) {
        LocalDateTime ahoraLima = LocalDateTime.now(LIMA);
        assertThat(Duration.between(guardada, ahoraLima).abs())
                .as("fecha_hora guardada %s frente a la hora de Lima %s", guardada, ahoraLima)
                .isLessThan(TOLERANCIA);
    }
}
