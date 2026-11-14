package com.farmacia.repository;

import com.farmacia.model.DetalleMovimientoInventario;
import com.farmacia.model.MovimientoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Integración de MovimientoInventarioRepositorio (Testcontainers + PostgreSQL)")
class MovimientoInventarioRepositorioTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private MovimientoInventarioRepositorio movimientoRepositorio;

    private static final AtomicLong contador = new AtomicLong(100_000L);

    @BeforeEach
    void setUp() {
        limpiarBaseDeDatos();
    }

    private String sufijoUnico() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Integer fixtureUsuario(String rol) {
        String username = "usr_" + sufijoUnico();
        return jdbcTemplate.queryForObject("""
            INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
            VALUES ('Usuario Test', ?, 'hash123', ?, true)
            RETURNING id_usuario
            """, Integer.class, username, rol);
    }

    private Integer fixtureProveedor() {
        String ruc = "20" + String.format("%09d", contador.incrementAndGet());
        return jdbcTemplate.queryForObject("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'Proveedor Fixture S.A.', true)
            RETURNING id_proveedor
            """, Integer.class, ruc);
    }

    private Integer fixtureMedicamento(String codigo, String nombre) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO medicamento (codigo, nombre, concentracion, presentacion, unidad_control, precio_venta, stock_minimo, activo)
            VALUES (?, ?, '500mg', 'Caja', 'CAJA', 10.50, 5, true)
            RETURNING id_medicamento
            """, Integer.class, codigo, nombre);
    }

    private Integer fixtureLote(Integer idMedicamento, String numeroLote, int stock) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO lote (id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
            VALUES (?, ?, ?, ?)
            RETURNING id_lote
            """, Integer.class, idMedicamento, numeroLote, LocalDate.now().plusMonths(12), stock);
    }

    private Integer fixtureOrdenCompra(Integer idUsuario, Integer idProveedor) {
        String numOrden = "OC-" + sufijoUnico();
        return jdbcTemplate.queryForObject("""
            INSERT INTO orden_compra (numero_orden, id_usuario, id_proveedor, estado, total_estimado)
            VALUES (?, ?, ?, 'PENDIENTE', 100.00)
            RETURNING id_orden
            """, Integer.class, numOrden, idUsuario, idProveedor);
    }

    private Integer fixtureDetalleOrdenCompra(Integer idOrden, Integer idMedicamento, int cantidad, BigDecimal costo) {
        BigDecimal total = costo.multiply(BigDecimal.valueOf(cantidad));
        return jdbcTemplate.queryForObject("""
            INSERT INTO detalle_orden_compra (id_orden, id_medicamento, cantidad_solicitada, costo_unitario, importe_estimado)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id_detalle_orden
            """, Integer.class, idOrden, idMedicamento, cantidad, costo, total);
    }

    @Test
    @DisplayName("MI-01: registrarEntrada y buscarPorId persisten movimiento de ENTRADA con claves foráneas pobladas")
    void registrarEntradaYBuscarPorId_persistenMovimientoConClavesForaneas() {
        int idUsuario = fixtureUsuario("ALMACENERO");
        int idProveedor = fixtureProveedor();
        int idOrden = fixtureOrdenCompra(idUsuario, idProveedor);

        Integer idMovimiento = movimientoRepositorio.registrarEntrada(
                idUsuario, idOrden, "Recepción parcial de orden");

        assertNotNull(idMovimiento);
        assertTrue(idMovimiento > 0);

        Optional<MovimientoInventario> optMov = movimientoRepositorio.buscarPorId(idMovimiento);
        assertTrue(optMov.isPresent(), "El movimiento de entrada debe existir en PostgreSQL");

        MovimientoInventario mov = optMov.get();
        assertEquals(idMovimiento, mov.idMovimiento());
        assertEquals(idUsuario, mov.idUsuario());
        assertEquals(idProveedor, mov.idProveedor(), "El proveedor se obtiene por la orden de compra (3FN)");
        assertEquals(idOrden, mov.idOrden());
        assertNull(mov.idVenta(), "Para movimientos de ENTRADA la venta debe ser NULL");
        assertEquals("ENTRADA", mov.tipo());
        assertEquals("Recepción parcial de orden", mov.motivo());
        assertNotNull(mov.fechaHora());
    }

    @Test
    @DisplayName("MI-02: registrarEntrada rechaza clave foránea inexistente lanzando DataIntegrityViolationException")
    void registrarEntrada_conFkInexistente_lanzaDataIntegrityViolationException() {
        int idUsuario = fixtureUsuario("ALMACENERO");

        assertThrows(DataIntegrityViolationException.class,
                () -> movimientoRepositorio.registrarEntrada(idUsuario, 999_999, "Compra Fallida"),
                "Debe fallar por fk_movimiento_orden");
    }

    @Test
    @DisplayName("MI-03: registrarDetalleEntrada inserta línea con id_detalle_orden poblado")
    void registrarDetalleEntrada_insertaDetalleConLineaDeOrden() {
        int idUsuario = fixtureUsuario("ALMACENERO");
        int idProveedor = fixtureProveedor();
        int idOrden = fixtureOrdenCompra(idUsuario, idProveedor);
        int idMed = fixtureMedicamento("MED-" + sufijoUnico(), "Paracetamol 500mg");
        int idLote = fixtureLote(idMed, "LOTE-" + sufijoUnico(), 0);
        int idDetalleOrden = fixtureDetalleOrdenCompra(idOrden, idMed, 50, new BigDecimal("2.00"));

        int idMov = movimientoRepositorio.registrarEntrada(idUsuario, idOrden, "Entrada mercadería");

        movimientoRepositorio.registrarDetalleEntrada(idMov, idLote, idDetalleOrden, 30);

        Integer cantidadEnBd = jdbcTemplate.queryForObject("""
            SELECT cantidad FROM detalle_movimiento
            WHERE id_movimiento = ? AND id_lote = ? AND id_detalle_orden = ?
            """, Integer.class, idMov, idLote, idDetalleOrden);

        assertEquals(30, cantidadEnBd, "La cantidad del detalle persistido debe ser exactamente 30");
    }

    @Test
    @DisplayName("MI-04: registrarDetalleEntrada rechaza cantidad menor o igual a cero por check constraint")
    void registrarDetalleEntrada_conCantidadInvalida_lanzaDataIntegrityViolationException() {
        int idUsuario = fixtureUsuario("ALMACENERO");
        int idProveedor = fixtureProveedor();
        int idOrden = fixtureOrdenCompra(idUsuario, idProveedor);
        int idMed = fixtureMedicamento("MED-" + sufijoUnico(), "Ibuprofeno 400mg");
        int idLote = fixtureLote(idMed, "LOTE-" + sufijoUnico(), 0);
        int idDetalleOrden = fixtureDetalleOrdenCompra(idOrden, idMed, 20, new BigDecimal("3.00"));
        int idMov = movimientoRepositorio.registrarEntrada(idUsuario, idOrden, "Entrada");

        assertThrows(DataIntegrityViolationException.class,
                () -> movimientoRepositorio.registrarDetalleEntrada(idMov, idLote, idDetalleOrden, 0),
                "chk_detalle_movimiento_cantidad exige cantidad > 0");

        assertThrows(DataIntegrityViolationException.class,
                () -> movimientoRepositorio.registrarDetalleEntrada(idMov, idLote, idDetalleOrden, -5),
                "chk_detalle_movimiento_cantidad exige cantidad > 0");
    }

    @Test
    @DisplayName("MI-05: registrarSalida y buscarPorId persisten SALIDA con claves foráneas estrictamente nulas")
    void registrarSalidaYBuscarPorId_persistenSalidaConFksNulas() {
        int idUsuario = fixtureUsuario("ALMACENERO");

        Integer idMovimiento = movimientoRepositorio.registrarSalida(idUsuario, "Ajuste por merma en almacén");

        assertNotNull(idMovimiento);
        assertTrue(idMovimiento > 0);

        MovimientoInventario mov = movimientoRepositorio.buscarPorId(idMovimiento).orElseThrow();
        assertEquals(idMovimiento, mov.idMovimiento());
        assertEquals(idUsuario, mov.idUsuario());
        assertEquals("SALIDA", mov.tipo());
        assertEquals("Ajuste por merma en almacén", mov.motivo());

        // Verificación crítica: getObject(..., Integer.class) debe preservar null
        assertNull(mov.idProveedor(), "En SALIDA idProveedor debe ser estrictamente null");
        assertNull(mov.idVenta(), "En SALIDA sin venta idVenta debe ser estrictamente null");
        assertNull(mov.idOrden(), "En SALIDA idOrden debe ser estrictamente null");

        // Verificación directa en PostgreSQL
        Boolean fksNulasEnBd = jdbcTemplate.queryForObject("""
            SELECT id_venta IS NULL AND id_orden IS NULL
            FROM movimiento_inventario WHERE id_movimiento = ?
            """, Boolean.class, idMovimiento);
        assertTrue(fksNulasEnBd, "PostgreSQL debe registrar NULL en las columnas de FK opcionales");
    }

    @Test
    @DisplayName("MI-06: registrarDetalle inserta detalle de salida con id_detalle_orden en NULL")
    void registrarDetalle_insertaDetalleConIdDetalleOrdenNull() {
        int idUsuario = fixtureUsuario("ALMACENERO");
        int idMed = fixtureMedicamento("MED-" + sufijoUnico(), "Amoxicilina 500mg");
        int idLote = fixtureLote(idMed, "LOTE-" + sufijoUnico(), 100);
        int idMov = movimientoRepositorio.registrarSalida(idUsuario, "Salida por vencimiento");

        movimientoRepositorio.registrarDetalle(idMov, idLote, 15);

        Boolean detalleOrdenNull = jdbcTemplate.queryForObject("""
            SELECT id_detalle_orden IS NULL FROM detalle_movimiento
            WHERE id_movimiento = ? AND id_lote = ? AND cantidad = 15
            """, Boolean.class, idMov, idLote);

        assertTrue(detalleOrdenNull, "En salidas directas id_detalle_orden debe ser NULL");
    }

    @Test
    @DisplayName("MI-07: registrarDetalle rechaza cantidad menor o igual a cero por check constraint")
    void registrarDetalle_conCantidadNegativa_lanzaDataIntegrityViolationException() {
        int idUsuario = fixtureUsuario("ALMACENERO");
        int idMed = fixtureMedicamento("MED-" + sufijoUnico(), "Alcohol 70%");
        int idLote = fixtureLote(idMed, "LOTE-" + sufijoUnico(), 50);
        int idMov = movimientoRepositorio.registrarSalida(idUsuario, "Salida");

        assertThrows(DataIntegrityViolationException.class,
                () -> movimientoRepositorio.registrarDetalle(idMov, idLote, -10),
                "chk_detalle_movimiento_cantidad exige cantidad > 0");
    }

    @Test
    @DisplayName("MI-08: buscarPorId con identificador inexistente retorna Optional.empty()")
    void buscarPorId_conIdInexistente_retornaOptionalEmpty() {
        Optional<MovimientoInventario> optMov = movimientoRepositorio.buscarPorId(999_999);
        assertTrue(optMov.isEmpty(), "Buscar un ID que no existe debe retornar Optional.empty()");
    }

    @Test
    @DisplayName("MI-09: listar ordena descendentemente por fecha_hora y desempata por id_movimiento DESC")
    void listar_ordenaPorFechaHoraDescYDesempataPorIdMovimientoDesc() {
        int idUsuario = fixtureUsuario("ALMACENERO");

        // Movimiento 1: Fecha antigua (10:00:00)
        Integer idMov1 = jdbcTemplate.queryForObject("""
            INSERT INTO movimiento_inventario (id_usuario, tipo, fecha_hora, motivo)
            VALUES (?, 'SALIDA', TIMESTAMP '2026-06-01 10:00:00', 'Movimiento 1 - 10:00')
            RETURNING id_movimiento
            """, Integer.class, idUsuario);

        // Movimiento 2: Fecha posterior (12:00:00) -> Genera ID menor que Movimiento 3
        Integer idMov2 = jdbcTemplate.queryForObject("""
            INSERT INTO movimiento_inventario (id_usuario, tipo, fecha_hora, motivo)
            VALUES (?, 'SALIDA', TIMESTAMP '2026-06-01 12:00:00', 'Movimiento 2 - 12:00 primer insert')
            RETURNING id_movimiento
            """, Integer.class, idUsuario);

        // Movimiento 3: Misma fecha que Movimiento 2 (12:00:00) -> Genera ID mayor que Movimiento 2
        Integer idMov3 = jdbcTemplate.queryForObject("""
            INSERT INTO movimiento_inventario (id_usuario, tipo, fecha_hora, motivo)
            VALUES (?, 'SALIDA', TIMESTAMP '2026-06-01 12:00:00', 'Movimiento 3 - 12:00 segundo insert')
            RETURNING id_movimiento
            """, Integer.class, idUsuario);

        assertTrue(idMov3 > idMov2, "idMov3 debe ser estrictamente mayor que idMov2 por secuencia autoincremental");

        List<MovimientoInventario> lista = movimientoRepositorio.listar();

        assertEquals(3, lista.size());
        // 1º Movimiento 3 (12:00:00, ID mayor)
        assertEquals(idMov3, lista.get(0).idMovimiento(), "El primero debe ser idMov3 (desempate id_movimiento DESC)");
        // 2º Movimiento 2 (12:00:00, ID menor)
        assertEquals(idMov2, lista.get(1).idMovimiento(), "El segundo debe ser idMov2");
        // 3º Movimiento 1 (10:00:00, fecha menor)
        assertEquals(idMov1, lista.get(2).idMovimiento(), "El tercero debe ser idMov1");
    }

    @Test
    @DisplayName("MI-10: listar en tabla sin movimientos retorna lista vacía")
    void listar_enTablaVacia_retornaListaVacia() {
        List<MovimientoInventario> lista = movimientoRepositorio.listar();
        assertNotNull(lista);
        assertTrue(lista.isEmpty(), "Cuando la tabla no tiene movimientos debe retornar lista vacía");
    }

    @Test
    @DisplayName("MI-11: listarDetalles proyecta datos con JOIN a lote y medicamento ordenados por nombre")
    void listarDetalles_proyectaDatosConJoinALoteYMedicamento() {
        int idUsuario = fixtureUsuario("ALMACENERO");
        int idMov = movimientoRepositorio.registrarSalida(idUsuario, "Salida con múltiples detalles");

        int idMedA = fixtureMedicamento("MED-A" + sufijoUnico(), "Amoxicilina 250mg");
        int idMedB = fixtureMedicamento("MED-B" + sufijoUnico(), "Ciprofloxacino 500mg");

        int idLoteA = fixtureLote(idMedA, "LOTE-A1", 40);
        int idLoteB = fixtureLote(idMedB, "LOTE-B1", 60);

        movimientoRepositorio.registrarDetalle(idMov, idLoteA, 10);
        movimientoRepositorio.registrarDetalle(idMov, idLoteB, 20);

        List<DetalleMovimientoInventario> detalles = movimientoRepositorio.listarDetalles(idMov);

        assertEquals(2, detalles.size());

        // Ordenado por m.nombre ASC: 1º Amoxicilina, 2º Ciprofloxacino
        DetalleMovimientoInventario det1 = detalles.get(0);
        assertEquals(idLoteA, det1.idLote());
        assertEquals("Amoxicilina 250mg", det1.nombreMedicamento());
        assertEquals("LOTE-A1", det1.numeroLote());
        assertEquals("CAJA", det1.unidadControl());
        assertEquals(10, det1.cantidad());

        DetalleMovimientoInventario det2 = detalles.get(1);
        assertEquals(idLoteB, det2.idLote());
        assertEquals("Ciprofloxacino 500mg", det2.nombreMedicamento());
        assertEquals("LOTE-B1", det2.numeroLote());
        assertEquals("CAJA", det2.unidadControl());
        assertEquals(20, det2.cantidad());
    }

    @Test
    @DisplayName("MI-12: listarDetalles para movimiento sin detalles retorna lista vacía")
    void listarDetalles_paraMovimientoSinDetalles_retornaListaVacia() {
        int idUsuario = fixtureUsuario("ALMACENERO");
        int idMov = movimientoRepositorio.registrarSalida(idUsuario, "Movimiento sin ítems");

        List<DetalleMovimientoInventario> detalles = movimientoRepositorio.listarDetalles(idMov);

        assertNotNull(detalles);
        assertTrue(detalles.isEmpty(), "Un movimiento sin detalles debe retornar lista vacía");
    }

    @Test
    @DisplayName("MI-13: buscarPorId mapea correctamente el atributo temporal como LocalDateTime no nulo")
    void buscarPorId_mapeaFechaHoraComoLocalDateTimeNoNulo() {
        int idUsuario = fixtureUsuario("ALMACENERO");
        int idMov = movimientoRepositorio.registrarSalida(idUsuario, "Prueba fecha_hora");

        MovimientoInventario mov = movimientoRepositorio.buscarPorId(idMov).orElseThrow();

        assertNotNull(mov.fechaHora(), "El atributo fechaHora no debe ser nulo");
        assertInstanceOf(LocalDateTime.class, mov.fechaHora(), "Debe ser instancia de java.time.LocalDateTime");
        assertTrue(mov.fechaHora().isBefore(LocalDateTime.now().plusMinutes(1)));
    }
}
