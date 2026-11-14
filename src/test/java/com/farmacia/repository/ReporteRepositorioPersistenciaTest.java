package com.farmacia.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.farmacia.model.MedicamentoRotacionVista;
import com.farmacia.model.VentasDiaVista;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ReporteRepositorioPersistenciaTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private ReporteRepositorio reporteRepositorio;

    private Integer idVendedor;
    private Integer idMedA;
    private Integer idMedB;
    private Integer idMedC;
    private Integer idLoteA1;
    private Integer idLoteA2;
    private Integer idLoteB;
    private Integer idLoteC;

    @BeforeEach
    void setUp() {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        // Vendedor
        idVendedor = jdbcTemplate.queryForObject("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES ('Vendedor Reporte', ?, '{bcrypt}$hash', 'VENDEDOR', true)
                RETURNING id_usuario
                """, Integer.class, "vend_" + sufijo);

        // Medicamentos: A (Amoxicilina), B (Bicarbonato), C (Clonazepam)
        idMedA = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento (codigo, nombre, presentacion, stock_minimo, unidad_control, precio_venta, activo)
                VALUES (?, 'Amoxicilina 500mg', 'Caja x 30', 10, 'TABLETA', 2.00, true)
                RETURNING id_medicamento
                """, Integer.class, "MED-A-" + sufijo);

        idMedB = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento (codigo, nombre, presentacion, stock_minimo, unidad_control, precio_venta, activo)
                VALUES (?, 'Bicarbonato 100g', 'Frasco', 5, 'FRASCO', 5.00, true)
                RETURNING id_medicamento
                """, Integer.class, "MED-B-" + sufijo);

        idMedC = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento (codigo, nombre, presentacion, stock_minimo, unidad_control, precio_venta, activo)
                VALUES (?, 'Clonazepam 2mg', 'Caja x 20', 10, 'TABLETA', 1.50, true)
                RETURNING id_medicamento
                """, Integer.class, "MED-C-" + sufijo);

        // Lotes
        idLoteA1 = insertarLote(idMedA, "LA1-" + sufijo, LocalDate.now().plusMonths(6), 100);
        idLoteA2 = insertarLote(idMedA, "LA2-" + sufijo, LocalDate.now().plusMonths(12), 100);
        idLoteB = insertarLote(idMedB, "LB-" + sufijo, LocalDate.now().plusMonths(8), 100);
        idLoteC = insertarLote(idMedC, "LC-" + sufijo, LocalDate.now().plusMonths(9), 100);
    }

    private Integer insertarLote(Integer idMed, String num, LocalDate venc, int stock) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO lote (id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
                VALUES (?, ?, ?, ?)
                RETURNING id_lote
                """, Integer.class, idMed, num, venc, stock);
    }

    private Integer insertarVenta(String comp, LocalDateTime fh, BigDecimal subtotal, BigDecimal desc, BigDecimal total) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO venta (numero_comprobante, fecha_hora, id_usuario, subtotal, descuento_total, total)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id_venta
                """, Integer.class, comp, fh, idVendedor, subtotal, desc, total);
    }

    private void insertarDetalleVenta(Integer idVenta, Integer idLote, int cant, BigDecimal pu, BigDecimal desc, BigDecimal imp) {
        jdbcTemplate.update("""
                INSERT INTO detalle_venta (id_venta, id_lote, cantidad, precio_unitario, descuento_importe, importe)
                VALUES (?, ?, ?, ?, ?, ?)
                """, idVenta, idLote, cant, pu, desc, imp);
    }

    private Integer insertarProveedor(String ruc, String razonSocial) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO proveedor (ruc, razon_social, direccion, telefono, correo, activo)
                VALUES (?, ?, 'Direccion Test', '999888777', 'prov@test.pe', true)
                RETURNING id_proveedor
                """, Integer.class, ruc, razonSocial);
    }

    private Integer insertarOrden(String num, Integer idProv, LocalDateTime fh, String estado, BigDecimal total) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO orden_compra (numero_orden, id_usuario, id_proveedor, fecha_hora, estado, total_estimado)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id_orden
                """, Integer.class, num, idVendedor, idProv, fh, estado, total);
    }

    private Integer insertarDetalleOrden(Integer idOrden, Integer idMed, int cant, BigDecimal costo, BigDecimal importe) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO detalle_orden_compra (id_orden, id_medicamento, cantidad_solicitada, costo_unitario, importe_estimado)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id_detalle_orden
                """, Integer.class, idOrden, idMed, cant, costo, importe);
    }

    private Integer insertarMovimientoEntrada(Integer idOrden, LocalDateTime fh) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO movimiento_inventario (id_usuario, id_orden, tipo, fecha_hora, motivo)
                VALUES (?, ?, 'ENTRADA', ?, 'Recepción orden test')
                RETURNING id_movimiento
                """, Integer.class, idVendedor, idOrden, fh);
    }

    private void insertarDetalleMovimiento(Integer idMov, Integer idLote, Integer idDetalleOrden, int cant) {
        jdbcTemplate.update("""
                INSERT INTO detalle_movimiento (id_movimiento, id_lote, id_detalle_orden, cantidad)
                VALUES (?, ?, ?, ?)
                """, idMov, idLote, idDetalleOrden, cant);
    }

    @Test
    @DisplayName("CA-1.1: ventasPorDia agrupa correctamente por fecha y suma totales exactos, excluyendo bordes fuera de rango")
    void ventasPorDia_agrupaCorrectamenteYRespetaBordes() {
        LocalDate dia1 = LocalDate.of(2026, 11, 10);
        LocalDate dia2 = LocalDate.of(2026, 11, 11);
        LocalDate dia3 = LocalDate.of(2026, 11, 12);

        // Venta 1 día 1 a las 10:00
        insertarVenta("B001-001", dia1.atTime(10, 0), new BigDecimal("20.00"), new BigDecimal("2.00"), new BigDecimal("18.00"));
        // Venta 2 día 1 a las 18:30
        insertarVenta("B001-002", dia1.atTime(18, 30), new BigDecimal("30.00"), BigDecimal.ZERO, new BigDecimal("30.00"));

        // Venta 3 día 2 al borde exacto 23:59:59 (debe incluirse en día 2)
        insertarVenta("B001-003", dia2.atTime(23, 59, 59), new BigDecimal("15.00"), BigDecimal.ZERO, new BigDecimal("15.00"));

        // Venta 4 día 3 a las 00:00:00 (fuera de rango si consultamos [dia1, dia2])
        insertarVenta("B001-004", dia3.atTime(0, 0, 0), new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"));

        List<VentasDiaVista> reporte = reporteRepositorio.ventasPorDia(dia1, dia2);

        assertEquals(2, reporte.size(), "Debe traer exactamente 2 días");

        VentasDiaVista fila1 = reporte.get(0);
        assertEquals(dia1, fila1.fecha());
        assertEquals(2, fila1.cantidadVentas());
        assertEquals(new BigDecimal("50.00"), fila1.subtotal());
        assertEquals(new BigDecimal("2.00"), fila1.descuento());
        assertEquals(new BigDecimal("48.00"), fila1.total());

        VentasDiaVista fila2 = reporte.get(1);
        assertEquals(dia2, fila2.fecha());
        assertEquals(1, fila2.cantidadVentas());
        assertEquals(new BigDecimal("15.00"), fila2.subtotal());
        assertEquals(new BigDecimal("0.00"), fila2.descuento());
        assertEquals(new BigDecimal("15.00"), fila2.total());
    }

    @Test
    @DisplayName("CA-2.1: masVendidos suma unidades vendidas de distintos lotes del mismo medicamento y ordena por unidades DESC")
    void masVendidos_sumaLotesYOrdenaCorrectamente() {
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        LocalDateTime ahora = hoy.atTime(12, 0);

        Integer v1 = insertarVenta("B001-010", ahora, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"));

        // Med A vendido en Lote A1 (5 unidades) y Lote A2 (3 unidades) -> Total 8 unidades
        insertarDetalleVenta(v1, idLoteA1, 5, new BigDecimal("2.00"), BigDecimal.ZERO, new BigDecimal("10.00"));
        insertarDetalleVenta(v1, idLoteA2, 3, new BigDecimal("2.00"), BigDecimal.ZERO, new BigDecimal("6.00"));

        // Med B vendido en Lote B (10 unidades) -> Total 10 unidades
        insertarDetalleVenta(v1, idLoteB, 10, new BigDecimal("5.00"), BigDecimal.ZERO, new BigDecimal("50.00"));

        // Med C vendido en Lote C (8 unidades) -> Total 8 unidades (empate con Med A)
        insertarDetalleVenta(v1, idLoteC, 8, new BigDecimal("1.50"), BigDecimal.ZERO, new BigDecimal("12.00"));

        List<MedicamentoRotacionVista> lista = reporteRepositorio.masVendidos(hoy, hoy, 10);

        assertEquals(3, lista.size());

        // 1ro debe ser Med B con 10 unidades
        assertEquals(idMedB, lista.get(0).idMedicamento());
        assertEquals(10, lista.get(0).unidadesVendidas());

        // 2do y 3ro empatan con 8 unidades; orden alfabético: 'Amoxicilina' antes de 'Clonazepam'
        assertEquals(idMedA, lista.get(1).idMedicamento());
        assertEquals(8, lista.get(1).unidadesVendidas());

        assertEquals(idMedC, lista.get(2).idMedicamento());
        assertEquals(8, lista.get(2).unidadesVendidas());
    }

    @Test
    @DisplayName("CA-3.1: mermas agrupa por medicamento las salidas sin venta y excluye salidas por venta")
    void mermas_agrupaSalidasSinVentaYExcluyeSalidasPorVenta() {
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        LocalDateTime ahora = hoy.atTime(14, 0);

        // Movimiento 1: Salida sin venta (merma / baja) para Med A (Lote A1: 4 unidades, Lote A2: 6 unidades)
        Integer movBaja = jdbcTemplate.queryForObject("""
                INSERT INTO movimiento_inventario (id_usuario, tipo, fecha_hora, motivo)
                VALUES (?, 'SALIDA', ?, 'Baja por vencimiento')
                RETURNING id_movimiento
                """, Integer.class, idVendedor, ahora);

        jdbcTemplate.update("""
                INSERT INTO detalle_movimiento (id_movimiento, id_lote, cantidad)
                VALUES (?, ?, 4), (?, ?, 6)
                """, movBaja, idLoteA1, movBaja, idLoteA2);

        // Movimiento 2: Salida POR VENTA (id_venta no nulo) para Med B (15 unidades) -> NO debe aparecer en mermas
        Integer v = insertarVenta("B001-099", ahora, new BigDecimal("75.00"), BigDecimal.ZERO, new BigDecimal("75.00"));
        Integer movVenta = jdbcTemplate.queryForObject("""
                INSERT INTO movimiento_inventario (id_usuario, id_venta, tipo, fecha_hora, motivo)
                VALUES (?, ?, 'SALIDA', ?, 'Venta B001-099')
                RETURNING id_movimiento
                """, Integer.class, idVendedor, v, ahora);

        jdbcTemplate.update("""
                INSERT INTO detalle_movimiento (id_movimiento, id_lote, cantidad)
                VALUES (?, ?, 15)
                """, movVenta, idLoteB);

        List<com.farmacia.model.MermaVista> mermas = reporteRepositorio.mermas(hoy, hoy);

        assertEquals(1, mermas.size(), "Solo debe listar la baja sin venta");
        assertEquals(idMedA, mermas.get(0).idMedicamento());
        assertEquals(10, mermas.get(0).unidadesBaja(), "4 + 6 = 10 unidades de merma");
    }

    @Test
    @DisplayName("CA-6.1, CA-6.2: reporteInventario clasifica stock disponible, vencido y estado, con desglose de lotes")
    void reporteInventario_clasificaCorrectamente() {
        LocalDate hoy = LocalDate.of(2026, 11, 13);
        String suf = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        // Med 1: Stock mínimo 20. Lote vigente 50 unidades, lote vencido 15 unidades -> Disp: 50, Venc: 15 -> OK
        Integer m1 = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento (codigo, nombre, presentacion, stock_minimo, unidad_control, precio_venta, activo)
                VALUES (?, 'Med Uno', 'Caja', 20, 'TABLETA', 1.00, true)
                RETURNING id_medicamento
                """, Integer.class, "M1-" + suf);
        insertarLote(m1, "L1-VIG-" + suf, hoy.plusDays(30), 50);
        insertarLote(m1, "L1-VENC-" + suf, hoy.minusDays(5), 15);

        // Med 2: Stock mínimo 10. Lote vigente 5 unidades -> Disp: 5, Venc: 0 -> STOCK_BAJO
        Integer m2 = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento (codigo, nombre, presentacion, stock_minimo, unidad_control, precio_venta, activo)
                VALUES (?, 'Med Dos', 'Caja', 10, 'TABLETA', 1.00, true)
                RETURNING id_medicamento
                """, Integer.class, "M2-" + suf);
        insertarLote(m2, "L2-VIG-" + suf, hoy.plusDays(60), 5);

        // Med 3: Stock mínimo 10. Sin lotes -> Disp: 0, Venc: 0 -> AGOTADO
        Integer m3 = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento (codigo, nombre, presentacion, stock_minimo, unidad_control, precio_venta, activo)
                VALUES (?, 'Med Tres', 'Caja', 10, 'TABLETA', 1.00, true)
                RETURNING id_medicamento
                """, Integer.class, "M3-" + suf);

        var todos = reporteRepositorio.reporteInventario(hoy, null);
        var item1 = todos.stream().filter(i -> i.idMedicamento().equals(m1)).findFirst().orElseThrow();
        assertEquals(50, item1.stockDisponible());
        assertEquals(15, item1.stockVencido());
        assertEquals("OK", item1.estado());
        assertEquals(2, item1.lotes().size());

        var item2 = todos.stream().filter(i -> i.idMedicamento().equals(m2)).findFirst().orElseThrow();
        assertEquals(5, item2.stockDisponible());
        assertEquals("STOCK_BAJO", item2.estado());

        var item3 = todos.stream().filter(i -> i.idMedicamento().equals(m3)).findFirst().orElseThrow();
        assertEquals(0, item3.stockDisponible());
        assertEquals("AGOTADO", item3.estado());

        // Filtrar por estado STOCK_BAJO
        var soloBajos = reporteRepositorio.reporteInventario(hoy, "STOCK_BAJO");
        assertTrue(soloBajos.stream().anyMatch(i -> i.idMedicamento().equals(m2)));
        assertFalse(soloBajos.stream().anyMatch(i -> i.idMedicamento().equals(m1)));
        assertFalse(soloBajos.stream().anyMatch(i -> i.idMedicamento().equals(m3)));
    }

    @Test
    @DisplayName("CA-7.1, CA-7.3: reporteCompras calcula unidades solicitadas, recibidas (desde detalle_movimiento) y pendientes, y filtra por estado y proveedor")
    void reporteCompras_calculaUnidadesRecibidasYPendientesYFiltra() {
        LocalDate hoy = LocalDate.of(2026, 11, 13);
        String suf = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        // Proveedores (RUC de 11 dígitos)
        String ruc1 = "20" + String.format("%09d", Math.abs((suf + "1").hashCode()) % 1_000_000_000);
        String ruc2 = "20" + String.format("%09d", Math.abs((suf + "2").hashCode()) % 1_000_000_000);
        Integer prov1 = insertarProveedor(ruc1, "Droguería Alfa " + suf);
        Integer prov2 = insertarProveedor(ruc2, "Laboratorio Beta " + suf);

        // Orden 1: 100 unidades solicitadas de Med A, recibidas 40 unidades
        Integer oc1 = insertarOrden("OC-01-" + suf, prov1, hoy.atTime(10, 0), "PARCIALMENTE_RECIBIDA", new BigDecimal("200.00"));
        Integer doc1 = insertarDetalleOrden(oc1, idMedA, 100, new BigDecimal("2.00"), new BigDecimal("200.00"));

        Integer mov1 = insertarMovimientoEntrada(oc1, hoy.atTime(15, 0));
        insertarDetalleMovimiento(mov1, idLoteA1, doc1, 40);

        // Orden 2: 50 unidades solicitadas de Med B, recibidas 0, proveedor 2
        Integer oc2 = insertarOrden("OC-02-" + suf, prov2, hoy.atTime(11, 0), "PENDIENTE", new BigDecimal("150.00"));
        insertarDetalleOrden(oc2, idMedB, 50, new BigDecimal("3.00"), new BigDecimal("150.00"));

        // Consultar reporte en el rango
        var ordenes = reporteRepositorio.reporteCompras(hoy.minusDays(1), hoy.plusDays(1), null, null);

        var r1 = ordenes.stream().filter(o -> o.idOrden().equals(oc1)).findFirst().orElseThrow();
        assertEquals("OC-01-" + suf, r1.numeroOrden());
        assertEquals("PARCIALMENTE_RECIBIDA", r1.estado());
        assertEquals(new BigDecimal("200.00"), r1.totalEstimado());
        assertEquals(100, r1.unidadesSolicitadas());
        assertEquals(40, r1.unidadesRecibidas(), "Debe sumar unidades desde detalle_movimiento");
        assertEquals(60, r1.unidadesPendientes(), "100 solicitadas - 40 recibidas = 60 pendientes");

        var r2 = ordenes.stream().filter(o -> o.idOrden().equals(oc2)).findFirst().orElseThrow();
        assertEquals("PENDIENTE", r2.estado());
        assertEquals(50, r2.unidadesSolicitadas());
        assertEquals(0, r2.unidadesRecibidas());
        assertEquals(50, r2.unidadesPendientes());

        // Filtrar por estado PENDIENTE
        var pendientes = reporteRepositorio.reporteCompras(hoy.minusDays(1), hoy.plusDays(1), "PENDIENTE", null);
        assertTrue(pendientes.stream().anyMatch(o -> o.idOrden().equals(oc2)));
        assertFalse(pendientes.stream().anyMatch(o -> o.idOrden().equals(oc1)));

        // Filtrar por proveedor prov1
        var porProv1 = reporteRepositorio.reporteCompras(hoy.minusDays(1), hoy.plusDays(1), null, prov1);
        assertTrue(porProv1.stream().anyMatch(o -> o.idOrden().equals(oc1)));
        assertFalse(porProv1.stream().anyMatch(o -> o.idOrden().equals(oc2)));
    }
}
