package com.farmacia.service;

import com.farmacia.model.LineaPreparacionVentaVista;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 003 con PostgreSQL real: CA-1.1, 1.2, 1.4, 2.1–2.3, 3.1, 3.2. */
class VentaServicioPersistenciaTest extends AbstractRepositorioIntegrationTest {
    private static final BigDecimal PRECIO = new BigDecimal("8.50");

    @Autowired
    private VentaServicio servicio;

    private int idVendedor;
    private int idMedicamento;
    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void preparar() {
        limpiarBaseDeDatos();
        idVendedor = jdbcTemplate.queryForObject("""
                INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol)
                VALUES ('Vendedor Persistencia', 'vendedor_p', 'x', 'VENDEDOR') RETURNING id_usuario
                """, Integer.class);
        idMedicamento = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento(codigo, nombre, presentacion, unidad_control, precio_venta)
                VALUES (?, 'Paracetamol', 'Caja', 'CAJA', 8.50) RETURNING id_medicamento
                """, Integer.class, "VEN-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Test
    @DisplayName("CA-1.1: lote con 30 y venta de 4 → stock 26, 1 venta, 1 detalle, 1 salida con id_venta y su detalle")
    void ventaDeUnLote() {
        int lote = lote("A", hoy.plusDays(60), 30);

        var resultado = servicio.confirmar("vendedor_p", null, List.of(linea(4)));

        assertEquals(26, stock(lote));
        assertEquals(1, contar("SELECT COUNT(*) FROM venta"));
        assertEquals(1, contar("SELECT COUNT(*) FROM detalle_venta WHERE id_venta = ?", resultado.idVenta()));
        assertEquals(1, contar("""
                SELECT COUNT(*) FROM movimiento_inventario
                WHERE id_venta = ? AND tipo = 'SALIDA' AND id_orden IS NULL
                """, resultado.idVenta()));
        assertEquals(1, contar("""
                SELECT COUNT(*) FROM detalle_movimiento d
                JOIN movimiento_inventario m ON m.id_movimiento = d.id_movimiento
                WHERE m.id_venta = ? AND d.id_lote = ? AND d.cantidad = 4
                """, resultado.idVenta(), lote));
        var venta = jdbcTemplate.queryForMap("SELECT * FROM venta WHERE id_venta = ?", resultado.idVenta());
        assertEquals(new BigDecimal("34.00"), venta.get("total"));
        assertEquals(idVendedor, venta.get("id_usuario"));
        assertEquals(null, venta.get("id_cliente"), "Sin cliente: Público general");
        assertTrue(resultado.numeroComprobante().matches("B001-\\d{8}"), resultado.numeroComprobante());        // Spec 014: la venta confirmada queda en la bitácora, en la misma transacción
        assertEquals(1, contar("SELECT COUNT(*) FROM auditoria WHERE accion = 'CONFIRMAR_VENTA' "
                + "AND entidad = 'VENTA' AND id_entidad = ?", String.valueOf(resultado.idVenta())));
    }

    @Test
    @DisplayName("CA-1.2: 15 unidades con lote A (10, vence antes) y B (20) → 10 de A y 5 de B, dos detalles")
    void ventaEnDosLotes() {
        int loteA = lote("A", hoy.plusDays(20), 10);
        int loteB = lote("B", hoy.plusDays(90), 20);

        var resultado = servicio.confirmar("vendedor_p", null, List.of(linea(15)));

        assertEquals(0, stock(loteA));
        assertEquals(15, stock(loteB));
        assertEquals(2, contar("SELECT COUNT(*) FROM detalle_venta WHERE id_venta = ?", resultado.idVenta()));
        assertEquals(10, contar("SELECT cantidad FROM detalle_venta WHERE id_venta = ? AND id_lote = ?",
                resultado.idVenta(), loteA));
        assertEquals(5, contar("SELECT cantidad FROM detalle_venta WHERE id_venta = ? AND id_lote = ?",
                resultado.idVenta(), loteB));
    }

    @Test
    @DisplayName("CA-1.4: con cliente la venta queda a su nombre; un cliente inexistente no guarda nada")
    void clienteDeLaVenta() {
        lote("A", hoy.plusDays(60), 30);
        int idCliente = jdbcTemplate.queryForObject("""
                INSERT INTO cliente(tipo_documento, numero_documento, nombre_completo)
                VALUES ('DNI', '12345678', 'Ana Pérez') RETURNING id_cliente
                """, Integer.class);

        var resultado = servicio.confirmar("vendedor_p", idCliente, List.of(linea(1)));
        assertEquals(idCliente, contar("SELECT id_cliente FROM venta WHERE id_venta = ?", resultado.idVenta()));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.confirmar("vendedor_p", idCliente + 1000, List.of(linea(1))));
        assertEquals(1, contar("SELECT COUNT(*) FROM venta"));
    }

    @Test
    @DisplayName("CA-2.1: si otro usuario vendió y el stock ya no alcanza, se rechaza y nada se guarda")
    void stockCambioEntrePrepararYConfirmar() {
        int lote = lote("A", hoy.plusDays(60), 3);

        assertThrows(StockInsuficienteException.class,
                () -> servicio.confirmar("vendedor_p", null, List.of(linea(4))));

        assertEquals(3, stock(lote));
        assertEquals(0, contar("SELECT COUNT(*) FROM venta"));
        assertEquals(0, contar("SELECT COUNT(*) FROM movimiento_inventario"));
    }

    @Test
    @DisplayName("CA-2.2: un lote vencido nunca se vende, aunque tenga stock")
    void loteVencidoNoSeVende() {
        int vencido = lote("V", hoy.minusDays(1), 50);

        assertThrows(StockInsuficienteException.class,
                () -> servicio.confirmar("vendedor_p", null, List.of(linea(1))));

        assertEquals(50, stock(vencido));
        assertEquals(0, contar("SELECT COUNT(*) FROM venta"));
    }

    @Test
    @DisplayName("CA-2.3: si falla el segundo tramo, ROLLBACK total: el primer lote recupera su stock")
    void rollbackTotalSiFallaElSegundoTramo() {
        int loteA = lote("A", hoy.plusDays(20), 10);
        int loteB = lote("B", hoy.plusDays(90), 20);
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fn_test_falla_detalle_venta() RETURNS trigger AS $$
                BEGIN
                    IF NEW.id_lote = %d THEN
                        RAISE EXCEPTION 'Fallo simulado en el segundo tramo';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """.formatted(loteB));
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_test_falla_detalle_venta BEFORE INSERT ON detalle_venta
                FOR EACH ROW EXECUTE FUNCTION fn_test_falla_detalle_venta()
                """);
        int eventosAntes = contar("SELECT COUNT(*) FROM auditoria WHERE accion = 'CONFIRMAR_VENTA'");
        try {
            assertThrows(RuntimeException.class,
                    () -> servicio.confirmar("vendedor_p", null, List.of(linea(15))));
            assertEquals(eventosAntes, contar("SELECT COUNT(*) FROM auditoria WHERE accion = 'CONFIRMAR_VENTA'"),
                    "Spec 014, CA-3.2: el evento se revierte junto con la venta");

            assertEquals(10, stock(loteA), "El primer tramo debe recuperar su stock");
            assertEquals(20, stock(loteB));
            assertEquals(0, contar("SELECT COUNT(*) FROM venta"));
            assertEquals(0, contar("SELECT COUNT(*) FROM detalle_venta"));
            assertEquals(0, contar("SELECT COUNT(*) FROM movimiento_inventario"));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_test_falla_detalle_venta ON detalle_venta");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fn_test_falla_detalle_venta()");
        }
    }

    @Test
    @DisplayName("CA-3.1/3.2: el comprobante muestra las líneas y dos ventas seguidas tienen números consecutivos")
    void comprobanteYCorrelativos() {
        lote("A", hoy.plusDays(60), 30);

        var primera = servicio.confirmar("vendedor_p", null, List.of(linea(2)));
        var segunda = servicio.confirmar("vendedor_p", null, List.of(linea(1)));

        int n1 = Integer.parseInt(primera.numeroComprobante().substring(5));
        int n2 = Integer.parseInt(segunda.numeroComprobante().substring(5));
        assertEquals(n1 + 1, n2);

        var comprobante = servicio.obtenerComprobante(primera.idVenta(), "vendedor_p").orElseThrow();
        assertEquals(primera.numeroComprobante(), comprobante.numeroComprobante());
        assertEquals("Vendedor Persistencia", comprobante.vendedor());
        assertEquals("Público general", comprobante.clienteVisible());
        assertEquals(1, comprobante.lineas().size());
        assertEquals("LT-A", comprobante.lineas().getFirst().numeroLote());
        assertEquals(new BigDecimal("17.00"), comprobante.total());
    }

    @Test
    @DisplayName("Spec 004 CA-3.1/3.3: descuento repartido por lote, CHECK de importes y total = subtotal − descuento")
    void ventaConDescuento() {
        int loteA = lote("A", hoy.plusDays(20), 4);
        int loteB = lote("B", hoy.plusDays(90), 20);
        var conDescuento = new LineaPreparacionVentaVista(idMedicamento, "MED", "Paracetamol", 10, PRECIO,
                new BigDecimal("5.00"), new BigDecimal("80.00"), List.of());

        var resultado = servicio.confirmar("vendedor_p", null, List.of(conDescuento));

        var venta = jdbcTemplate.queryForMap("SELECT * FROM venta WHERE id_venta = ?", resultado.idVenta());
        assertEquals(new BigDecimal("85.00"), venta.get("subtotal"));
        assertEquals(new BigDecimal("5.00"), venta.get("descuento_total"));
        assertEquals(new BigDecimal("80.00"), venta.get("total"));
        assertEquals(new BigDecimal("2.00"), jdbcTemplate.queryForObject(
                "SELECT descuento_importe FROM detalle_venta WHERE id_venta = ? AND id_lote = ?",
                BigDecimal.class, resultado.idVenta(), loteA));
        assertEquals(new BigDecimal("3.00"), jdbcTemplate.queryForObject(
                "SELECT descuento_importe FROM detalle_venta WHERE id_venta = ? AND id_lote = ?",
                BigDecimal.class, resultado.idVenta(), loteB));
        assertEquals(new BigDecimal("80.00"), jdbcTemplate.queryForObject(
                "SELECT SUM(importe) FROM detalle_venta WHERE id_venta = ?", BigDecimal.class, resultado.idVenta()));

        var comprobante = servicio.obtenerComprobante(resultado.idVenta(), "vendedor_p").orElseThrow();
        assertEquals(new BigDecimal("5.00"), comprobante.descuentoTotal());
    }

    @Test
    @DisplayName("Spec 004 CA-3.2: 0.01 en 3 tramos guarda descuentos que suman exactamente 0.01")
    void descuentoDeUnCentimoEnTresTramos() {
        lote("A", hoy.plusDays(10), 1);
        lote("B", hoy.plusDays(20), 1);
        lote("C", hoy.plusDays(30), 1);
        var conDescuento = new LineaPreparacionVentaVista(idMedicamento, "MED", "Paracetamol", 3, PRECIO,
                new BigDecimal("0.01"), new BigDecimal("25.49"), List.of());

        var resultado = servicio.confirmar("vendedor_p", null, List.of(conDescuento));

        assertEquals(3, contar("SELECT COUNT(*) FROM detalle_venta WHERE id_venta = ?", resultado.idVenta()));
        assertEquals(new BigDecimal("0.01"), jdbcTemplate.queryForObject(
                "SELECT SUM(descuento_importe) FROM detalle_venta WHERE id_venta = ?",
                BigDecimal.class, resultado.idVenta()));
    }

    @Test
    @DisplayName("CA-4.1, CA-4.3: listarMisVentas con persistencia real devuelve únicamente las ventas del vendedor autenticado")
    void listarMisVentas_aislamientoPorVendedor() {
        int idOtroVendedor = jdbcTemplate.queryForObject("""
                INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol)
                VALUES ('Otro Vendedor', 'vendedor_otro', 'x', 'VENDEDOR') RETURNING id_usuario
                """, Integer.class);

        int lote = lote("X", hoy.plusDays(30), 50);

        var ventaP = servicio.confirmar("vendedor_p", null, List.of(linea(2)));
        var ventaOtro = servicio.confirmar("vendedor_otro", null, List.of(linea(3)));

        var misVentas = servicio.listarMisVentas("vendedor_p", hoy, hoy);

        assertEquals(1, misVentas.filas().size());
        assertEquals(ventaP.idVenta(), misVentas.filas().getFirst().idVenta());
        assertEquals("B001-", misVentas.filas().getFirst().numeroComprobante().substring(0, 5));
    }

    private LineaPreparacionVentaVista linea(int cantidad) {
        return new LineaPreparacionVentaVista(idMedicamento, "MED", "Paracetamol", cantidad, PRECIO,
                PRECIO.multiply(BigDecimal.valueOf(cantidad)), List.of());
    }

    private int lote(String sufijo, LocalDate vencimiento, int stock) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO lote(id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
                VALUES (?, ?, ?, ?) RETURNING id_lote
                """, Integer.class, idMedicamento, "LT-" + sufijo, vencimiento, stock);
    }

    private int stock(int idLote) {
        return contar("SELECT stock_actual FROM lote WHERE id_lote = ?", idLote);
    }

    private int contar(String sql, Object... argumentos) {
        return jdbcTemplate.queryForObject(sql, Integer.class, argumentos);
    }
}
