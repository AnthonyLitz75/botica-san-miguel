package com.farmacia.service;

import com.farmacia.dto.SalidaInventarioFormulario;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de pruebas transaccionales de concurrencia para la gestión de inventario y salidas físicas.
 * <p>
 * Verifica el comportamiento de PostgreSQL y {@link MovimientoInventarioServicio} ante operaciones
 * concurrentes sobre el mismo lote físico, asegurando consistencia ACID, aislamiento transaccional
 * y ausencia de inconsistencias (sobreventa o lecturas fantasma).
 * </p>
 * <p>
 * <b>Nota sobre ICT-03 (Concurrencia y política FEFO):</b> Conforme al análisis arquitectónico
 * del hito del 60%, la política FEFO implementada en {@link LotePrioridadServicio} y
 * {@link PreparacionVentaServicio} actúa como un planificador en memoria de sesión HTTP
 * (utilizando la estructura artesanal {@code ColaPrioridadLotes}), mientras que el descuento
 * físico transaccional multihilo se ejecuta puntualmente a nivel de lote en {@link MovimientoInventarioServicio}.
 * La reserva y consumo transaccional concurrente automático de ventas completas corresponde
 * al alcance del 100% (RF-10..RF-13). En consecuencia, para no introducir pruebas artificiales
 * no sustentadas por código productivo, se prueban con rigor absoluto la exclusión mutua por lote
 * en ICT-01 e ICT-02, documentando la frontera arquitectónica de FEFO.
 * </p>
 */
@DisplayName("Pruebas transaccionales de concurrencia del inventario (PostgreSQL Testcontainers)")
class InventarioConcurrenciaTransaccionalTest extends AbstractRepositorioIntegrationTest {

    private static final AtomicLong secuenciaCodigo = new AtomicLong(5000L);

    @Autowired
    private MovimientoInventarioServicio servicio;

    @BeforeEach
    void setUp() {
        limpiarBaseDeDatos();
    }

    private Integer crearUsuario(String username, String rol) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES ('Usuario Concurrente', ?, '$2a$10$dummyHash1234567890123456789012', ?, TRUE)
                RETURNING id_usuario
                """, Integer.class, username, rol);
    }

    private Integer crearMedicamento(String nombre) {
        String codigo = "MED-CONC-" + secuenciaCodigo.incrementAndGet();
        return jdbcTemplate.queryForObject("""
                INSERT INTO medicamento (codigo, nombre, concentracion, presentacion, unidad_control, precio_venta, stock_minimo, activo)
                VALUES (?, ?, '500 mg', 'TABLETA', 'CAJA', 15.00, 10, TRUE)
                RETURNING id_medicamento
                """, Integer.class, codigo, nombre);
    }

    private Integer crearLote(Integer idMedicamento, String numeroLote, Integer stockInicial) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO lote (id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
                VALUES (?, ?, ?, ?)
                RETURNING id_lote
                """, Integer.class, idMedicamento, numeroLote, LocalDate.now().plusMonths(12), stockInicial);
    }

    private void cerrarExecutorSeguro(ExecutorService executor) {
        executor.shutdownNow();
        try {
            boolean terminado = executor.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminado) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // ICT-01: Competencia por las últimas existencias (Sobreventa / Sobredescuento)
    // =========================================================================
    @Test
    @DisplayName("ICT-01: Competencia por las últimas existencias — Exactamente una operación tiene éxito y la otra es rechazada")
    void competenciaPorUltimasExistencias_exactamenteUnaOperacionTieneExito() throws InterruptedException {
        // 1. Preparación de datos: lote con 10 unidades
        crearUsuario("almacen_ict1_a", "ALMACENERO");
        crearUsuario("almacen_ict1_b", "ALMACENERO");

        Integer idMed = crearMedicamento("Amoxicilina Concurrente 1");
        Integer idLote = crearLote(idMed, "LOT-ICT-01", 10);

        // 2. Definición de operaciones concurrentes: dos salidas de 8 unidades cada una
        // Ambas demandan 8 + 8 = 16 > 10. Solo una debe completarse.
        var formHilo1 = new SalidaInventarioFormulario(8, "Salida concurrente hilo 1");
        var formHilo2 = new SalidaInventarioFormulario(8, "Salida concurrente hilo 2");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);

        AtomicReference<Exception> errorHilo1 = new AtomicReference<>();
        AtomicReference<Exception> errorHilo2 = new AtomicReference<>();
        AtomicBoolean exitoHilo1 = new AtomicBoolean(false);
        AtomicBoolean exitoHilo2 = new AtomicBoolean(false);

        try {
            executor.submit(() -> {
                try {
                    startGate.await();
                    servicio.registrarSalida(idLote, "almacen_ict1_a", formHilo1);
                    exitoHilo1.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorHilo1.set(e);
                } catch (Exception e) {
                    errorHilo1.set(e);
                } finally {
                    doneGate.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startGate.await();
                    servicio.registrarSalida(idLote, "almacen_ict1_b", formHilo2);
                    exitoHilo2.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorHilo2.set(e);
                } catch (Exception e) {
                    errorHilo2.set(e);
                } finally {
                    doneGate.countDown();
                }
            });

            // Disparo simultáneo de ambos hilos
            startGate.countDown();

            boolean terminaron = doneGate.await(15, TimeUnit.SECONDS);
            assertTrue(terminaron, "Ambas operaciones concurrentes debieron concluir dentro de 15 segundos");
        } finally {
            cerrarExecutorSeguro(executor);
        }

        // 3. Verificación de exclusión mutua
        int exitos = (exitoHilo1.get() ? 1 : 0) + (exitoHilo2.get() ? 1 : 0);
        int fallos = (errorHilo1.get() != null ? 1 : 0) + (errorHilo2.get() != null ? 1 : 0);

        assertEquals(1, exitos, "Exactamente una operación de salida debe completar con éxito");
        assertEquals(1, fallos, "Exactamente una operación de salida debe ser rechazada por stock insuficiente");

        Exception error = errorHilo1.get() != null ? errorHilo1.get() : errorHilo2.get();
        assertNotNull(error, "El hilo perdedor debe haber capturado una excepción");
        assertTrue(error instanceof IllegalArgumentException,
                "La excepción esperada debe ser IllegalArgumentException, pero fue: " + error.getClass());
        assertEquals("No hay existencias suficientes para registrar la salida", error.getMessage());

        // 4. Verificaciones directas en PostgreSQL
        // Stock final del lote: 10 - 8 = 2 unidades
        Integer stockFinal = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_lote = ?", Integer.class, idLote);
        assertEquals(2, stockFinal, "El stock final en PostgreSQL debe ser exactamente 2 unidades");

        // Cantidad de movimientos de salida vinculados a este lote: exactamente 1
        Integer movimientosConfirmados = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT mi.id_movimiento)
                FROM movimiento_inventario mi
                JOIN detalle_movimiento dm ON dm.id_movimiento = mi.id_movimiento
                WHERE mi.tipo = 'SALIDA' AND dm.id_lote = ?
                """, Integer.class, idLote);
        assertEquals(1, movimientosConfirmados, "Debe existir exactamente 1 movimiento de salida vinculado a este lote");

        // Cantidad de detalles de movimiento persistidos: exactamente 1 con cantidad = 8
        Integer totalDetalles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalle_movimiento WHERE id_lote = ?", Integer.class, idLote);
        assertEquals(1, totalDetalles, "Debe existir exactamente 1 detalle de movimiento para el lote");

        Integer cantidadDescontada = jdbcTemplate.queryForObject(
                "SELECT cantidad FROM detalle_movimiento WHERE id_lote = ?", Integer.class, idLote);
        assertEquals(8, cantidadDescontada, "La cantidad registrada en el detalle debe ser exactamente 8");

        // Comprobación de ausencia de movimientos huérfanos sin líneas de detalle
        Integer movimientosHuerfanos = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM movimiento_inventario mi
                LEFT JOIN detalle_movimiento dm ON dm.id_movimiento = mi.id_movimiento
                WHERE dm.id_detalle_movimiento IS NULL
                """, Integer.class);
        assertEquals(0, movimientosHuerfanos, "No deben existir movimientos de inventario huérfanos sin líneas de detalle");
    }

    // =========================================================================
    // ICT-02: Consumo concurrente exacto (Descuentos paralelos sin interferencia)
    // =========================================================================
    @Test
    @DisplayName("ICT-02: Consumo concurrente exacto — Ambas operaciones tienen éxito y stock final es exactamente 0")
    void consumoConcurrenteExacto_ambasOperacionesTienenExito() throws InterruptedException {
        // 1. Preparación de datos: lote con 20 unidades
        crearUsuario("almacen_ict2_a", "ALMACENERO");
        crearUsuario("almacen_ict2_b", "ALMACENERO");

        Integer idMed = crearMedicamento("Paracetamol Concurrente 2");
        Integer idLote = crearLote(idMed, "LOT-ICT-02", 20);

        // 2. Definición de operaciones concurrentes: dos salidas de 10 unidades cada una (10 + 10 = 20)
        var formHilo1 = new SalidaInventarioFormulario(10, "Consumo concurrente exacto 1");
        var formHilo2 = new SalidaInventarioFormulario(10, "Consumo concurrente exacto 2");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);

        AtomicReference<Exception> errorHilo1 = new AtomicReference<>();
        AtomicReference<Exception> errorHilo2 = new AtomicReference<>();
        AtomicBoolean exitoHilo1 = new AtomicBoolean(false);
        AtomicBoolean exitoHilo2 = new AtomicBoolean(false);

        try {
            executor.submit(() -> {
                try {
                    startGate.await();
                    servicio.registrarSalida(idLote, "almacen_ict2_a", formHilo1);
                    exitoHilo1.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorHilo1.set(e);
                } catch (Exception e) {
                    errorHilo1.set(e);
                } finally {
                    doneGate.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startGate.await();
                    servicio.registrarSalida(idLote, "almacen_ict2_b", formHilo2);
                    exitoHilo2.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorHilo2.set(e);
                } catch (Exception e) {
                    errorHilo2.set(e);
                } finally {
                    doneGate.countDown();
                }
            });

            // Disparo simultáneo de ambos hilos
            startGate.countDown();

            boolean terminaron = doneGate.await(15, TimeUnit.SECONDS);
            assertTrue(terminaron, "Ambas operaciones concurrentes debieron concluir dentro de 15 segundos");
        } finally {
            cerrarExecutorSeguro(executor);
        }

        // 3. Verificación de éxito de ambas operaciones
        assertTrue(exitoHilo1.get(), "La operación del hilo 1 debe tener éxito");
        assertTrue(exitoHilo2.get(), "La operación del hilo 2 debe tener éxito");
        assertEquals(null, errorHilo1.get(), "El hilo 1 no debió capturar errores");
        assertEquals(null, errorHilo2.get(), "El hilo 2 no debió capturar errores");

        // 4. Verificaciones directas en PostgreSQL
        // Stock final del lote: 20 - 10 - 10 = 0 unidades
        Integer stockFinal = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_lote = ?", Integer.class, idLote);
        assertEquals(0, stockFinal, "El stock final en PostgreSQL debe ser exactamente 0 unidades");

        // Cantidad de movimientos de salida vinculados a este lote: exactamente 2
        Integer movimientosConfirmados = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT mi.id_movimiento)
                FROM movimiento_inventario mi
                JOIN detalle_movimiento dm ON dm.id_movimiento = mi.id_movimiento
                WHERE mi.tipo = 'SALIDA' AND dm.id_lote = ?
                """, Integer.class, idLote);
        assertEquals(2, movimientosConfirmados, "Deben existir exactamente 2 movimientos de salida vinculados a este lote");

        // Cantidad de detalles de movimiento persistidos: exactamente 2
        Integer totalDetalles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalle_movimiento WHERE id_lote = ?", Integer.class, idLote);
        assertEquals(2, totalDetalles, "Deben existir exactamente 2 detalles de movimiento");

        // Suma total de existencias retiradas: exactamente 20 unidades
        Integer sumaRetirada = jdbcTemplate.queryForObject(
                "SELECT SUM(cantidad) FROM detalle_movimiento WHERE id_lote = ?", Integer.class, idLote);
        assertEquals(20, sumaRetirada, "La suma total de cantidades retiradas debe ser exactamente 20");

        // Comprobación de ausencia de movimientos huérfanos sin líneas de detalle
        Integer movimientosHuerfanos = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM movimiento_inventario mi
                LEFT JOIN detalle_movimiento dm ON dm.id_movimiento = mi.id_movimiento
                WHERE dm.id_detalle_movimiento IS NULL
                """, Integer.class);
        assertEquals(0, movimientosHuerfanos, "No deben existir movimientos de inventario huérfanos sin líneas de detalle");
    }
}
