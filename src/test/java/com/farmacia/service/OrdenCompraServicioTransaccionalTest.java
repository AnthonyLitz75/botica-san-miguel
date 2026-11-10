package com.farmacia.service;

import com.farmacia.dto.DetalleOrdenCompraFormulario;
import com.farmacia.dto.DetalleRecepcionFormulario;
import com.farmacia.dto.OrdenCompraFormulario;
import com.farmacia.dto.RecepcionOrdenFormulario;
import com.farmacia.model.LineaRecepcionVista;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de pruebas transaccionales de integración para {@link OrdenCompraServicio}.
 * Verifica las propiedades ACID, rollback automático, control de estados y exclusión mutua
 * concurrente con {@code SELECT ... FOR UPDATE} sobre PostgreSQL real con Testcontainers.
 */
class OrdenCompraServicioTransaccionalTest extends AbstractRepositorioIntegrationTest {

    private static final AtomicLong rucSecuencia = new AtomicLong(20100000000L);
    private static final AtomicLong medSecuencia = new AtomicLong(10000L);

    @Autowired
    private OrdenCompraServicio servicio;

    @BeforeEach
    void prepararPrueba() {
        limpiarTriggersDePrueba();
        limpiarBaseDeDatos();
    }

    @AfterEach
    void limpiarTrasPrueba() {
        limpiarTriggersDePrueba();
    }

    private void limpiarTriggersDePrueba() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_test_error_detalle_orden ON detalle_orden_compra");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fn_test_error_detalle_orden()");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_test_error_detalle_mov ON detalle_movimiento");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fn_test_error_detalle_mov()");
    }

    // -------------------------------------------------------------------------
    // OCT-01: Creación atómica exitosa de orden con múltiples detalles
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("OCT-01: Creación atómica exitosa de orden con múltiples detalles en PostgreSQL")
    void creacionAtomicaExitosaDeOrdenConMultiplesDetalles() {
        var usuarioCompras = crearUsuario("comp_oct1", "COMPRAS");
        var proveedor = crearProveedor("prov_oct1");
        var med1 = crearMedicamento("med_oct1_a", "Paracetamol 500mg");
        var med2 = crearMedicamento("med_oct1_b", "Amoxicilina 500mg");

        var form = new OrdenCompraFormulario(
                proveedor.idProveedor(),
                "Orden de compra con 2 detalles",
                List.of(
                        new DetalleOrdenCompraFormulario(med1.idMedicamento(), 50, new BigDecimal("2.50")),
                        new DetalleOrdenCompraFormulario(med2.idMedicamento(), 30, new BigDecimal("4.00"))
                )
        );

        Integer idOrden = servicio.guardar(usuarioCompras.nombreUsuario(), form);
        assertNotNull(idOrden, "El servicio debe retornar el ID de la orden generada");
        assertTrue(idOrden > 0, "El ID de la orden debe ser un entero positivo");

        // 1. Verificar cabecera en orden_compra
        var orden = jdbcTemplate.queryForMap(
                "SELECT * FROM orden_compra WHERE id_orden = ?", idOrden);
        assertEquals("PENDIENTE", orden.get("estado"));
        assertEquals(usuarioCompras.idUsuario(), orden.get("id_usuario"));
        assertEquals(proveedor.idProveedor(), orden.get("id_proveedor"));
        assertEquals(0, new BigDecimal("245.00").compareTo((BigDecimal) orden.get("total_estimado")),
                "El total estimado debe ser 50*2.50 + 30*4.00 = 245.00");
        assertEquals("Orden de compra con 2 detalles", orden.get("observaciones"));
        assertNotNull(orden.get("numero_orden"));

        // 2. Verificar detalles en detalle_orden_compra
        Integer totalDetalles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalle_orden_compra WHERE id_orden = ?", Integer.class, idOrden);
        assertEquals(2, totalDetalles, "Deben existir exactamente 2 líneas en detalle_orden_compra");

        // 3. Verificar importes estimados en los detalles
        BigDecimal importe1 = jdbcTemplate.queryForObject(
                "SELECT importe_estimado FROM detalle_orden_compra WHERE id_orden = ? AND id_medicamento = ?",
                BigDecimal.class, idOrden, med1.idMedicamento());
        assertEquals(0, new BigDecimal("125.00").compareTo(importe1));

        BigDecimal importe2 = jdbcTemplate.queryForObject(
                "SELECT importe_estimado FROM detalle_orden_compra WHERE id_orden = ? AND id_medicamento = ?",
                BigDecimal.class, idOrden, med2.idMedicamento());
        assertEquals(0, new BigDecimal("120.00").compareTo(importe2));
    }

    // -------------------------------------------------------------------------
    // OCT-02: Rollback completo al fallar la inserción de un detalle intermedio
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("OCT-02: Rollback completo al fallar la inserción de un detalle intermedio")
    void rollbackCompletoAlFallarInsercionDetalleIntermedio() {
        var usuarioCompras = crearUsuario("comp_rb", "COMPRAS");
        var proveedor = crearProveedor("prov_rb");
        var med1 = crearMedicamento("med_rb1", "Medicamento Rollback 1");
        var med2 = crearMedicamento("med_rb2", "Medicamento Rollback 2");

        // Instalar trigger temporal en PostgreSQL que falla deliberadamente en detalle_orden_compra
        try {
            jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fn_test_error_detalle_orden()
                RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'Simulación de fallo forzado en detalle_orden_compra';
                END;
                $$ LANGUAGE plpgsql;

                CREATE TRIGGER trg_test_error_detalle_orden
                BEFORE INSERT ON detalle_orden_compra
                FOR EACH ROW EXECUTE FUNCTION fn_test_error_detalle_orden();
                """);

            var form = new OrdenCompraFormulario(
                    proveedor.idProveedor(),
                    "Orden con fallo inducido",
                    List.of(
                            new DetalleOrdenCompraFormulario(med1.idMedicamento(), 10, new BigDecimal("15.00")),
                            new DetalleOrdenCompraFormulario(med2.idMedicamento(), 20, new BigDecimal("25.00"))
                    )
            );

            assertThrows(DataAccessException.class, () ->
                    servicio.guardar(usuarioCompras.nombreUsuario(), form));

        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_test_error_detalle_orden ON detalle_orden_compra");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fn_test_error_detalle_orden()");
        }

        // Comprobación directa en PostgreSQL post-rollback:
        Integer ordenes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orden_compra WHERE id_usuario = ?", Integer.class, usuarioCompras.idUsuario());
        assertEquals(0, ordenes, "EVIDENCIA ROLLBACK: La cabecera en orden_compra debió revertirse y no existir en BD");

        Integer detalles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalle_orden_compra", Integer.class);
        assertEquals(0, detalles, "EVIDENCIA ROLLBACK: No deben existir detalles huérfanos en detalle_orden_compra");
    }

    // -------------------------------------------------------------------------
    // OCT-03: Recepción parcial atómica con actualización de stock y estado
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("OCT-03: Recepción parcial atómica con actualización correcta de stock, movimiento y estado")
    void recepcionParcialAtomicaConActualizacionDeStockYEstado() {
        var usuarioCompras = crearUsuario("comp_oct3", "COMPRAS");
        var usuarioAlmacen = crearUsuario("alm_oct3", "ALMACENERO");
        var proveedor = crearProveedor("prov_oct3");
        var med = crearMedicamento("med_oct3", "Ibuprofeno 400mg");

        Integer idOrden = servicio.guardar(usuarioCompras.nombreUsuario(), new OrdenCompraFormulario(
                proveedor.idProveedor(),
                "Orden para recepción parcial",
                List.of(new DetalleOrdenCompraFormulario(med.idMedicamento(), 50, new BigDecimal("10.00")))
        ));

        var lineas = servicio.listarLineasRecepcion(idOrden);
        assertEquals(1, lineas.size());
        Integer idDetalle = lineas.get(0).idDetalleOrden();

        LocalDate vencimiento = LocalDate.now().plusMonths(8);
        var formRecepcion = new RecepcionOrdenFormulario(
                List.of(new DetalleRecepcionFormulario(idDetalle, "LOTE-PARCIAL-1", vencimiento, 30)),
                "Primera entrega parcial de 30 unidades"
        );

        servicio.registrarRecepcion(idOrden, usuarioAlmacen.nombreUsuario(), formRecepcion);

        // 1. Verificar estado de la orden en PostgreSQL
        String estado = jdbcTemplate.queryForObject(
                "SELECT estado FROM orden_compra WHERE id_orden = ?", String.class, idOrden);
        assertEquals("PARCIALMENTE_RECIBIDA", estado, "El estado de la orden debe ser PARCIALMENTE_RECIBIDA");

        // 2. Verificar saldos en líneas de recepción
        var lineasActualizadas = servicio.listarLineasRecepcion(idOrden);
        assertEquals(1, lineasActualizadas.size());
        LineaRecepcionVista linea = lineasActualizadas.get(0);
        assertEquals(50, linea.cantidadSolicitada());
        assertEquals(30, linea.cantidadRecibida());
        assertEquals(20, linea.cantidadPendiente());

        // 3. Verificar movimiento_inventario
        var movimiento = jdbcTemplate.queryForMap(
                "SELECT * FROM movimiento_inventario WHERE id_orden = ?", idOrden);
        assertEquals("ENTRADA", movimiento.get("tipo"));
        assertEquals(usuarioAlmacen.idUsuario(), movimiento.get("id_usuario"));
        assertEquals(proveedor.idProveedor(), movimiento.get("id_proveedor"));
        assertEquals("Primera entrega parcial de 30 unidades", movimiento.get("motivo"));

        Integer idMovimiento = (Integer) movimiento.get("id_movimiento");

        // 4. Verificar detalle_movimiento
        var detalleMov = jdbcTemplate.queryForMap(
                "SELECT * FROM detalle_movimiento WHERE id_movimiento = ?", idMovimiento);
        assertEquals(idDetalle, detalleMov.get("id_detalle_orden"));
        assertEquals(30, detalleMov.get("cantidad"));

        Integer idLote = (Integer) detalleMov.get("id_lote");

        // 5. Verificar lote en PostgreSQL
        var lote = jdbcTemplate.queryForMap(
                "SELECT * FROM lote WHERE id_lote = ?", idLote);
        assertEquals("LOTE-PARCIAL-1", lote.get("numero_lote"));
        assertEquals(med.idMedicamento(), lote.get("id_medicamento"));
        assertEquals(30, lote.get("stock_actual"));
    }

    // -------------------------------------------------------------------------
    // OCT-04: Recepción complementaria que completa la orden
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("OCT-04: Recepción complementaria que completa la orden pasando a RECIBIDA y saldo 0")
    void recepcionComplementariaQueCompletaLaOrden() {
        var usuarioCompras = crearUsuario("comp_oct4", "COMPRAS");
        var usuarioAlmacen = crearUsuario("alm_oct4", "ALMACENERO");
        var proveedor = crearProveedor("prov_oct4");
        var med = crearMedicamento("med_oct4", "Cetirizina 10mg");

        Integer idOrden = servicio.guardar(usuarioCompras.nombreUsuario(), new OrdenCompraFormulario(
                proveedor.idProveedor(),
                "Orden para recepción en dos fases",
                List.of(new DetalleOrdenCompraFormulario(med.idMedicamento(), 50, new BigDecimal("5.00")))
        ));

        var lineas = servicio.listarLineasRecepcion(idOrden);
        Integer idDetalle = lineas.get(0).idDetalleOrden();

        // Fase 1: Recepción parcial de 30 unidades en Lote A
        LocalDate vencLoteA = LocalDate.now().plusMonths(6);
        servicio.registrarRecepcion(idOrden, usuarioAlmacen.nombreUsuario(), new RecepcionOrdenFormulario(
                List.of(new DetalleRecepcionFormulario(idDetalle, "LOTE-COMPL-A", vencLoteA, 30)),
                "Entrega inicial de 30"
        ));

        assertEquals("PARCIALMENTE_RECIBIDA", jdbcTemplate.queryForObject(
                "SELECT estado FROM orden_compra WHERE id_orden = ?", String.class, idOrden));

        // Fase 2: Recepción complementaria de las 20 unidades restantes en Lote B
        LocalDate vencLoteB = LocalDate.now().plusMonths(12);
        servicio.registrarRecepcion(idOrden, usuarioAlmacen.nombreUsuario(), new RecepcionOrdenFormulario(
                List.of(new DetalleRecepcionFormulario(idDetalle, "LOTE-COMPL-B", vencLoteB, 20)),
                "Entrega complementaria de 20 para completar"
        ));

        // 1. Verificar estado final en PostgreSQL
        String estadoFinal = jdbcTemplate.queryForObject(
                "SELECT estado FROM orden_compra WHERE id_orden = ?", String.class, idOrden);
        assertEquals("RECIBIDA", estadoFinal, "El estado final de la orden debe ser RECIBIDA");

        // 2. Verificar saldos finales en líneas de recepción
        var lineasFinales = servicio.listarLineasRecepcion(idOrden);
        assertEquals(1, lineasFinales.size());
        LineaRecepcionVista linea = lineasFinales.get(0);
        assertEquals(50, linea.cantidadSolicitada());
        assertEquals(50, linea.cantidadRecibida());
        assertEquals(0, linea.cantidadPendiente(), "El saldo pendiente debe ser exactamente 0");

        // 3. Verificar que existen exactamente 2 movimientos de entrada para esta orden
        Integer cantidadMovimientos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_orden = ? AND tipo = 'ENTRADA'",
                Integer.class, idOrden);
        assertEquals(2, cantidadMovimientos, "Deben existir exactamente 2 movimientos de entrada registrados");

        // 4. Verificar stock acumulado en los lotes
        Integer stockLoteA = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_medicamento = ? AND numero_lote = 'LOTE-COMPL-A'",
                Integer.class, med.idMedicamento());
        assertEquals(30, stockLoteA);

        Integer stockLoteB = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_medicamento = ? AND numero_lote = 'LOTE-COMPL-B'",
                Integer.class, med.idMedicamento());
        assertEquals(20, stockLoteB);

        Integer stockTotal = jdbcTemplate.queryForObject(
                "SELECT SUM(stock_actual) FROM lote WHERE id_medicamento = ?",
                Integer.class, med.idMedicamento());
        assertEquals(50, stockTotal, "El stock total acumulado de los lotes debe ser exactamente 50");

        // 5. Verificar correspondencia en detalle_movimiento
        Integer totalDetallesMov = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalle_movimiento dm " +
                "JOIN movimiento_inventario mi ON mi.id_movimiento = dm.id_movimiento " +
                "WHERE mi.id_orden = ? AND dm.id_detalle_orden = ?",
                Integer.class, idOrden, idDetalle);
        assertEquals(2, totalDetallesMov, "Deben existir 2 registros en detalle_movimiento apuntando al mismo detalle de orden");
    }

    // -------------------------------------------------------------------------
    // OCT-05: Rollback completo de recepción ante fallo intermedio en detalle_movimiento
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("OCT-05: Rollback completo de una recepción ante fallo intermedio en detalle_movimiento")
    void rollbackCompletoDeRecepcionAnteFalloIntermedioEnDetalleMovimiento() {
        var usuarioCompras = crearUsuario("comp_rbrec", "COMPRAS");
        var usuarioAlmacen = crearUsuario("alm_rbrec", "ALMACENERO");
        var proveedor = crearProveedor("prov_rbrec");
        var med = crearMedicamento("med_rbrec", "Medicamento RB Recepción");

        Integer idOrden = servicio.guardar(usuarioCompras.nombreUsuario(), new OrdenCompraFormulario(
                proveedor.idProveedor(),
                "Orden para test de rollback en recepcion",
                List.of(new DetalleOrdenCompraFormulario(med.idMedicamento(), 40, new BigDecimal("20.00")))
        ));

        var lineas = servicio.listarLineasRecepcion(idOrden);
        Integer idDetalle = lineas.get(0).idDetalleOrden();

        try {
            // Instalar trigger temporal en PostgreSQL que falla en la inserción de detalle_movimiento
            jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fn_test_error_detalle_mov()
                RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'Simulación de fallo forzado en detalle_movimiento';
                END;
                $$ LANGUAGE plpgsql;

                CREATE TRIGGER trg_test_error_detalle_mov
                BEFORE INSERT ON detalle_movimiento
                FOR EACH ROW EXECUTE FUNCTION fn_test_error_detalle_mov();
                """);

            var form = new RecepcionOrdenFormulario(
                    List.of(new DetalleRecepcionFormulario(idDetalle, "LOTE-RB", LocalDate.now().plusMonths(8), 25)),
                    "Recepción que debe fallar");

            assertThrows(DataAccessException.class, () ->
                    servicio.registrarRecepcion(idOrden, usuarioAlmacen.nombreUsuario(), form));

        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_test_error_detalle_mov ON detalle_movimiento");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fn_test_error_detalle_mov()");
        }

        // Verificación directa en PostgreSQL:
        // 1. Estado de la orden permanece en PENDIENTE
        String estado = jdbcTemplate.queryForObject(
                "SELECT estado FROM orden_compra WHERE id_orden = ?", String.class, idOrden);
        assertEquals("PENDIENTE", estado, "EVIDENCIA ROLLBACK: El estado de la orden debe permanecer en PENDIENTE");

        // 2. Cero movimientos de inventario creados
        Integer movimientos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_orden = ?", Integer.class, idOrden);
        assertEquals(0, movimientos, "EVIDENCIA ROLLBACK: La cabecera de movimiento debió ser revertida");

        // 3. Cero detalles de movimiento
        Integer detallesMov = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalle_movimiento", Integer.class);
        assertEquals(0, detallesMov, "EVIDENCIA ROLLBACK: No deben existir detalles de movimiento");

        // 4. El lote no debe haberse creado / incrementado
        Integer stockLote = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(stock_actual), 0) FROM lote WHERE id_medicamento = ?", Integer.class, med.idMedicamento());
        assertEquals(0, stockLote, "EVIDENCIA ROLLBACK: El stock de lote debe ser 0, la inserción del lote fue revertida");

        // 5. Líneas de recepción siguen con 0 recibidas y 40 pendientes
        var lineasPostRollback = servicio.listarLineasRecepcion(idOrden);
        assertEquals(0, lineasPostRollback.get(0).cantidadRecibida());
        assertEquals(40, lineasPostRollback.get(0).cantidadPendiente());
    }

    // -------------------------------------------------------------------------
    // OCT-06: Concurrencia real y exclusión mutua con SELECT FOR UPDATE
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("OCT-06: Dos recepciones concurrentes de la misma orden verifican exclusión mutua con SELECT FOR UPDATE")
    void dosRecepcionesConcurrentesVerificanExclusionMutuaYEvitanDuplicacion() throws Exception {
        var usuarioCompras = crearUsuario("comp_conc", "COMPRAS");
        var usuarioAlmacen1 = crearUsuario("alm_conc1", "ALMACENERO");
        var usuarioAlmacen2 = crearUsuario("alm_conc2", "ALMACENERO");
        var proveedor = crearProveedor("prov_conc");
        var med = crearMedicamento("med_conc", "Medicamento Concurrencia");

        Integer idOrden = servicio.guardar(usuarioCompras.nombreUsuario(), new OrdenCompraFormulario(
                proveedor.idProveedor(),
                "Orden para test de concurrencia",
                List.of(new DetalleOrdenCompraFormulario(med.idMedicamento(), 30, new BigDecimal("10.00")))
        ));

        var lineas = servicio.listarLineasRecepcion(idOrden);
        assertEquals(1, lineas.size());
        Integer idDetalle = lineas.get(0).idDetalleOrden();

        // Formularios de recepción compitiendo por la totalidad de la orden (30 unidades cada uno)
        var formHilo1 = new RecepcionOrdenFormulario(
                List.of(new DetalleRecepcionFormulario(idDetalle, "LOTE-CONC-1", LocalDate.now().plusMonths(6), 30)),
                "Recepción concurrente hilo 1");

        var formHilo2 = new RecepcionOrdenFormulario(
                List.of(new DetalleRecepcionFormulario(idDetalle, "LOTE-CONC-2", LocalDate.now().plusMonths(6), 30)),
                "Recepción concurrente hilo 2");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);

        AtomicReference<Throwable> errorHilo1 = new AtomicReference<>();
        AtomicReference<Throwable> errorHilo2 = new AtomicReference<>();
        AtomicBoolean exitoHilo1 = new AtomicBoolean(false);
        AtomicBoolean exitoHilo2 = new AtomicBoolean(false);

        try {
            executor.submit(() -> {
                try {
                    startGate.await();
                    servicio.registrarRecepcion(idOrden, usuarioAlmacen1.nombreUsuario(), formHilo1);
                    exitoHilo1.set(true);
                } catch (Throwable t) {
                    errorHilo1.set(t);
                } finally {
                    doneGate.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startGate.await();
                    servicio.registrarRecepcion(idOrden, usuarioAlmacen2.nombreUsuario(), formHilo2);
                    exitoHilo2.set(true);
                } catch (Throwable t) {
                    errorHilo2.set(t);
                } finally {
                    doneGate.countDown();
                }
            });

            // Liberar a ambos hilos simultáneamente para que compitan en paralelo
            startGate.countDown();

            boolean termino = doneGate.await(15, TimeUnit.SECONDS);
            assertTrue(termino, "Ambos hilos debieron terminar dentro de los 15 segundos");
        } finally {
            executor.shutdownNow();
        }

        // Aserción de exclusión mutua: exactamente un hilo tuvo éxito y exactamente uno falló
        int exitos = (exitoHilo1.get() ? 1 : 0) + (exitoHilo2.get() ? 1 : 0);
        int fallos = (errorHilo1.get() != null ? 1 : 0) + (errorHilo2.get() != null ? 1 : 0);

        assertEquals(1, exitos, "Exactamente una recepción debe completarse con éxito");
        assertEquals(1, fallos, "Exactamente una recepción debe ser rechazada");

        Throwable error = errorHilo1.get() != null ? errorHilo1.get() : errorHilo2.get();
        assertNotNull(error, "El hilo perdedor debe haber capturado una excepción");
        assertTrue(error instanceof IllegalArgumentException,
                "La excepción esperada debe ser IllegalArgumentException, pero fue: " + error.getClass());

        // Auditoría directa en PostgreSQL:
        // 1. Estado final de la orden en PostgreSQL debe ser RECIBIDA
        String estadoFinal = jdbcTemplate.queryForObject(
                "SELECT estado FROM orden_compra WHERE id_orden = ?", String.class, idOrden);
        assertEquals("RECIBIDA", estadoFinal);

        // 2. Exactamente 1 movimiento de entrada registrado para esta orden
        Integer totalMovimientos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_orden = ?", Integer.class, idOrden);
        assertEquals(1, totalMovimientos, "Debe existir exactamente 1 movimiento de entrada registrado en PostgreSQL");

        // 3. Stock total ingresado en lote: exactamente 30 (NO 60)
        Integer stockTotalLotes = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(stock_actual), 0) FROM lote WHERE id_medicamento = ?", Integer.class, med.idMedicamento());
        assertEquals(30, stockTotalLotes, "El stock total en lotes debe ser exactamente 30 sin duplicaciones por concurrencia");

        // 4. Saldo pendiente en líneas debe ser 0 y cantidad recibida 30
        var lineasFinales = servicio.listarLineasRecepcion(idOrden);
        assertEquals(30, lineasFinales.get(0).cantidadRecibida());
        assertEquals(0, lineasFinales.get(0).cantidadPendiente());
    }

    // -------------------------------------------------------------------------
    // OCT-07: Cancelación atómica válida de una orden pendiente
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("OCT-07: Cancelación atómica válida de una orden pendiente")
    void cancelacionAtomicaValidaDeOrdenPendiente() {
        var usuarioCompras = crearUsuario("comp_oct7", "COMPRAS");
        var usuarioAlmacen = crearUsuario("alm_oct7", "ALMACENERO");
        var proveedor = crearProveedor("prov_oct7");
        var med = crearMedicamento("med_oct7", "Omeprazol 20mg");

        Integer idOrden = servicio.guardar(usuarioCompras.nombreUsuario(), new OrdenCompraFormulario(
                proveedor.idProveedor(),
                "Orden para cancelación válida",
                List.of(new DetalleOrdenCompraFormulario(med.idMedicamento(), 15, new BigDecimal("8.00")))
        ));

        // Cancelar la orden
        servicio.cancelarOrden(idOrden, usuarioCompras.nombreUsuario());

        // 1. Verificar estado en PostgreSQL
        String estado = jdbcTemplate.queryForObject(
                "SELECT estado FROM orden_compra WHERE id_orden = ?", String.class, idOrden);
        assertEquals("CANCELADA", estado, "El estado debe cambiar a CANCELADA");

        // 2. Verificar que futuras recepciones son rechazadas
        var lineas = servicio.listarLineasRecepcion(idOrden);
        Integer idDetalle = lineas.get(0).idDetalleOrden();

        var formRecepcion = new RecepcionOrdenFormulario(
                List.of(new DetalleRecepcionFormulario(idDetalle, "LOTE-CANCEL", LocalDate.now().plusMonths(6), 15)),
                "Intento de recepcion en orden cancelada");

        var ex = assertThrows(IllegalArgumentException.class, () ->
                servicio.registrarRecepcion(idOrden, usuarioAlmacen.nombreUsuario(), formRecepcion));
        assertEquals("La orden no admite más recepciones", ex.getMessage());

        // 3. Confirmar que no se crearon movimientos
        Integer movimientos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_orden = ?", Integer.class, idOrden);
        assertEquals(0, movimientos);
    }

    // -------------------------------------------------------------------------
    // OCT-08: Rechazo de cancelación de una orden parcialmente recibida
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("OCT-08: Rechazo de cancelación de una orden parcialmente recibida")
    void rechazoDeCancelacionDeOrdenParcialmenteRecibida() {
        var usuarioCompras = crearUsuario("comp_oct8", "COMPRAS");
        var usuarioAlmacen = crearUsuario("alm_oct8", "ALMACENERO");
        var proveedor = crearProveedor("prov_oct8");
        var med = crearMedicamento("med_oct8", "Naproxeno 500mg");

        Integer idOrden = servicio.guardar(usuarioCompras.nombreUsuario(), new OrdenCompraFormulario(
                proveedor.idProveedor(),
                "Orden para rechazo de cancelación",
                List.of(new DetalleOrdenCompraFormulario(med.idMedicamento(), 20, new BigDecimal("6.50")))
        ));

        var lineas = servicio.listarLineasRecepcion(idOrden);
        Integer idDetalle = lineas.get(0).idDetalleOrden();

        // Registrar recepción parcial de 10 unidades
        servicio.registrarRecepcion(idOrden, usuarioAlmacen.nombreUsuario(), new RecepcionOrdenFormulario(
                List.of(new DetalleRecepcionFormulario(idDetalle, "LOTE-OCT8", LocalDate.now().plusMonths(6), 10)),
                "Recepción parcial"
        ));

        // Intentar cancelar la orden parcialmente recibida
        var ex = assertThrows(IllegalArgumentException.class, () ->
                servicio.cancelarOrden(idOrden, usuarioCompras.nombreUsuario()));
        assertEquals("Solo se pueden cancelar órdenes pendientes sin recepciones", ex.getMessage());

        // 1. Verificar directamente en PostgreSQL que el estado sigue siendo PARCIALMENTE_RECIBIDA
        String estado = jdbcTemplate.queryForObject(
                "SELECT estado FROM orden_compra WHERE id_orden = ?", String.class, idOrden);
        assertEquals("PARCIALMENTE_RECIBIDA", estado,
                "El estado de la orden en PostgreSQL no debe cambiar a CANCELADA");

        // 2. Verificar que el stock del lote se conserva intacto
        Integer stockLote = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_medicamento = ? AND numero_lote = 'LOTE-OCT8'",
                Integer.class, med.idMedicamento());
        assertEquals(10, stockLote, "El stock en lote debe conservarse en 10");

        // 3. Verificar que el movimiento sigue existiendo
        Integer movimientos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_orden = ?", Integer.class, idOrden);
        assertEquals(1, movimientos, "El movimiento de recepción debe conservarse");
    }

    // -------------------------------------------------------------------------
    // Métodos auxiliares de creación de fixtures sintéticos
    // -------------------------------------------------------------------------
    private UsuarioFixture crearUsuario(String prefijo, String rol) {
        String username = "usr_" + prefijo + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Integer id = jdbcTemplate.queryForObject("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES (?, ?, 'hash_dummy', ?, true)
                RETURNING id_usuario
                """, Integer.class, "Usuario " + prefijo, username, rol);
        return new UsuarioFixture(id, username, rol);
    }

    private ProveedorFixture crearProveedor(String prefijo) {
        String ruc = String.valueOf(rucSecuencia.getAndIncrement());
        String razonSocial = "Proveedor " + prefijo + " " + UUID.randomUUID().toString().substring(0, 6);
        Integer id = jdbcTemplate.queryForObject("""
                INSERT INTO proveedor (ruc, razon_social, activo)
                VALUES (?, ?, true)
                RETURNING id_proveedor
                """, Integer.class, ruc, razonSocial);
        return new ProveedorFixture(id, ruc, razonSocial);
    }

    private MedicamentoFixture crearMedicamento(String prefijo, String nombre) {
        String codigo = "MED-" + medSecuencia.getAndIncrement();
        Integer id = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento (codigo, nombre, presentacion, unidad_control, precio_venta, stock_minimo, activo)
                VALUES (?, ?, 'Caja', 'CAJA', 10.00, 5, true)
                RETURNING id_medicamento
                """, Integer.class, codigo, nombre);
        return new MedicamentoFixture(id, codigo, nombre);
    }

    private record UsuarioFixture(Integer idUsuario, String nombreUsuario, String rol) {}
    private record ProveedorFixture(Integer idProveedor, String ruc, String razonSocial) {}
    private record MedicamentoFixture(Integer idMedicamento, String codigo, String nombre) {}
}
