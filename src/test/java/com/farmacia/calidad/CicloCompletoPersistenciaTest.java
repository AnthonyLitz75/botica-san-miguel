package com.farmacia.calidad;

import com.farmacia.dto.ClienteFormulario;
import com.farmacia.dto.DetalleOrdenCompraFormulario;
import com.farmacia.dto.DetalleRecepcionFormulario;
import com.farmacia.dto.OrdenCompraFormulario;
import com.farmacia.dto.PreparacionVentaCambioFormulario;
import com.farmacia.dto.RecepcionOrdenFormulario;
import com.farmacia.model.ComprobanteVista;
import com.farmacia.model.PanelAlertasVista;
import com.farmacia.model.VentaConfirmadaVista;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import com.farmacia.repository.MedicamentoRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import com.farmacia.service.AlertaInventarioServicio;
import com.farmacia.service.AuditoriaServicio;
import com.farmacia.service.ClienteServicio;
import com.farmacia.service.LotePrioridadServicio;
import com.farmacia.service.OrdenCompraServicio;
import com.farmacia.service.PreparacionVentaServicio;
import com.farmacia.service.ReporteServicio;
import com.farmacia.service.VentaServicio;
import com.farmacia.service.politica.DescuentoImporteFijo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec 010 (US-3 / CA-3.1): Prueba de humo del ciclo completo de persistencia real.
 * Recorre:
 * 1. Crear orden de compra
 * 2. Recibir parcial (actualiza stock y saldo pendiente)
 * 3. Recibir total (cierra la orden y totaliza stock)
 * 4. Alertas de inventario (actualizadas tras recepción)
 * 5. Alta de cliente
 * 6. Preparar venta con Undo / Deshacer (estructura Pila LIFO)
 * 7. Aplicar descuento de línea
 * 8. Confirmar venta (descuenta FEFO, genera comprobante y movimiento)
 * 9. Comprobante generado
 * 10. Historial de ventas
 * 11. Reporte de ventas
 * 12. Auditoría en bitácora (eventos con totales coherentes)
 */
class CicloCompletoPersistenciaTest extends AbstractRepositorioIntegrationTest {

    @Autowired private OrdenCompraServicio ordenCompraServicio;
    @Autowired private AlertaInventarioServicio alertaInventarioServicio;
    @Autowired private ClienteServicio clienteServicio;
    @Autowired private VentaServicio ventaServicio;
    @Autowired private ReporteServicio reporteServicio;
    @Autowired private AuditoriaServicio auditoriaServicio;

    @Autowired private MedicamentoRepositorio medicamentoRepositorio;
    @Autowired private UsuarioRepositorio usuarioRepositorio;
    @Autowired private LotePrioridadServicio lotePrioridadServicio;

    private String usuarioAdmin;
    private String usuarioCompras;
    private String usuarioAlmacen;
    private String usuarioVendedor;

    private int idProveedor;
    private int idMedicamento;
    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void inicializarDatos() {
        limpiarBaseDeDatos();

        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        usuarioAdmin = "admin_" + sufijo;
        usuarioCompras = "compras_" + sufijo;
        usuarioAlmacen = "almacen_" + sufijo;
        usuarioVendedor = "vendedor_" + sufijo;

        jdbcTemplate.update("""
                INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol, activo) VALUES
                ('Admin Ciclo', ?, 'hash', 'ADMINISTRADOR', TRUE),
                ('Compras Ciclo', ?, 'hash', 'COMPRAS', TRUE),
                ('Almacen Ciclo', ?, 'hash', 'ALMACENERO', TRUE),
                ('Vendedor Ciclo', ?, 'hash', 'VENDEDOR', TRUE)
                """, usuarioAdmin, usuarioCompras, usuarioAlmacen, usuarioVendedor);

        String ruc = "20" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
        idProveedor = jdbcTemplate.queryForObject("""
                INSERT INTO proveedor(ruc, razon_social, activo)
                VALUES (?, 'Distribuidora Farma SA', TRUE) RETURNING id_proveedor
                """, Integer.class, ruc);

        String codMedicamento = "MED-" + sufijo;
        idMedicamento = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento(codigo, nombre, presentacion, unidad_control, precio_venta, stock_minimo, activo)
                VALUES (?, 'Amoxicilina 500mg', 'Caja x 30', 'CAJA', 10.00, 20, TRUE) RETURNING id_medicamento
                """, Integer.class, codMedicamento);
    }

    @AfterEach
    void limpiarSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(String usuario) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(usuario, null, List.of()));
    }

    @Test
    @DisplayName("CA-3.1: Ciclo completo integrado de persistencia: Compra -> Recepción -> Alertas -> Cliente -> Preparación (Undo) -> Venta -> Comprobante -> Historial -> Reportes -> Auditoría")
    void ejecutarCicloCompleto() {
        // =========================================================================
        // 1. Crear Orden de Compra (50 unidades a S/ 5.00 cada una = S/ 250.00)
        // =========================================================================
        autenticarComo(usuarioCompras);
        var detalleOrden = new DetalleOrdenCompraFormulario(idMedicamento, 50, new BigDecimal("5.00"));
        var ordenFormulario = new OrdenCompraFormulario(idProveedor, "Abastecimiento mensual", List.of(detalleOrden));

        Integer idOrden = ordenCompraServicio.guardar(usuarioCompras, ordenFormulario);
        assertNotNull(idOrden, "La orden de compra debe haberse guardado exitosamente");

        var ordenGuardada = ordenCompraServicio.buscarPorId(idOrden).orElseThrow();
        assertEquals("PENDIENTE", ordenGuardada.estado(), "La orden debe crearse en estado PENDIENTE");
        assertEquals(new BigDecimal("250.00"), ordenGuardada.totalEstimado());

        var lineasRecepcionInicial = ordenCompraServicio.listarLineasRecepcion(idOrden);
        assertEquals(1, lineasRecepcionInicial.size());
        int idDetalleOrden = lineasRecepcionInicial.getFirst().idDetalleOrden();
        assertEquals(50, lineasRecepcionInicial.getFirst().cantidadPendiente());
        assertEquals(0, lineasRecepcionInicial.getFirst().cantidadRecibida());

        // =========================================================================
        // 2. Recepción Parcial (30 unidades en Lote 1 con vencimiento hoy + 180 días)
        // =========================================================================
        autenticarComo(usuarioAlmacen);
        LocalDate vencimientoLote1 = hoy.plusDays(180);
        String numeroLote1 = "LT-PARCIAL-01";
        var recepcionParcial = new RecepcionOrdenFormulario(
                List.of(new DetalleRecepcionFormulario(idDetalleOrden, numeroLote1, vencimientoLote1, 30)),
                "Primera entrega proveedor"
        );
        ordenCompraServicio.registrarRecepcion(idOrden, usuarioAlmacen, recepcionParcial);

        var ordenParcial = ordenCompraServicio.buscarPorId(idOrden).orElseThrow();
        assertEquals("PARCIALMENTE_RECIBIDA", ordenParcial.estado(), "El estado debe ser PARCIALMENTE_RECIBIDA");

        var lineasParcial = ordenCompraServicio.listarLineasRecepcion(idOrden);
        assertEquals(30, lineasParcial.getFirst().cantidadRecibida());
        assertEquals(20, lineasParcial.getFirst().cantidadPendiente());

        int stockLote1 = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_medicamento = ? AND numero_lote = ?",
                Integer.class, idMedicamento, numeroLote1);
        assertEquals(30, stockLote1, "El stock en Lote 1 debe ser exactamente 30");

        // =========================================================================
        // 3. Recepción Total (20 unidades en Lote 2 con vencimiento hoy + 365 días)
        // =========================================================================
        LocalDate vencimientoLote2 = hoy.plusDays(365);
        String numeroLote2 = "LT-TOTAL-02";
        var recepcionTotal = new RecepcionOrdenFormulario(
                List.of(new DetalleRecepcionFormulario(idDetalleOrden, numeroLote2, vencimientoLote2, 20)),
                "Entrega final completada"
        );
        ordenCompraServicio.registrarRecepcion(idOrden, usuarioAlmacen, recepcionTotal);

        var ordenCompleta = ordenCompraServicio.buscarPorId(idOrden).orElseThrow();
        assertEquals("RECIBIDA", ordenCompleta.estado(), "La orden debe culminar en estado RECIBIDA");

        var lineasTotal = ordenCompraServicio.listarLineasRecepcion(idOrden);
        assertEquals(50, lineasTotal.getFirst().cantidadRecibida());
        assertEquals(0, lineasTotal.getFirst().cantidadPendiente());

        int stockLote2 = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_medicamento = ? AND numero_lote = ?",
                Integer.class, idMedicamento, numeroLote2);
        assertEquals(20, stockLote2, "El stock en Lote 2 debe ser exactamente 20");

        int stockTotalMedicamento = jdbcTemplate.queryForObject(
                "SELECT SUM(stock_actual) FROM lote WHERE id_medicamento = ?",
                Integer.class, idMedicamento);
        assertEquals(50, stockTotalMedicamento, "El stock total del medicamento debe ser 50 (30 + 20)");

        // =========================================================================
        // 4. Consulta de Alertas de Inventario
        // =========================================================================
        PanelAlertasVista alertas = alertaInventarioServicio.consultarAlertas();
        boolean medicamentoEnAgotados = alertas.agotados().stream()
                .anyMatch(a -> a.idMedicamento().equals(idMedicamento));
        boolean medicamentoEnBajoStock = alertas.stockBajo().stream()
                .anyMatch(a -> a.idMedicamento().equals(idMedicamento));
        assertFalse(medicamentoEnAgotados, "El medicamento no debe figurar en agotados");
        assertFalse(medicamentoEnBajoStock, "El medicamento no debe figurar en bajo stock (50 > min 20)");

        var necesidades = alertaInventarioServicio.necesidadesReposicion();
        boolean reposicionSugerida = necesidades.stream()
                .anyMatch(a -> a.idMedicamento().equals(idMedicamento) && a.cantidadSugerida() > 0);
        assertFalse(reposicionSugerida, "No debe sugerir reposición cuando el stock supera el mínimo");

        // =========================================================================
        // 5. Alta de Cliente (DNI y nombre completo)
        // =========================================================================
        autenticarComo(usuarioVendedor);
        String dni = "7" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
        var clienteFormulario = new ClienteFormulario("DNI", dni, "Carlos Sanchez Gomez", "987654321", "carlos@example.com");
        Integer idCliente = clienteServicio.guardar(clienteFormulario);
        assertNotNull(idCliente, "El cliente debe haberse registrado con éxito");

        var clienteConsultado = clienteServicio.buscarPorId(idCliente).orElseThrow();
        assertEquals("Carlos Sanchez Gomez", clienteConsultado.nombreCompleto());
        assertEquals(dni, clienteConsultado.numeroDocumento());

        // =========================================================================
        // 6. Preparar Venta en Sesión con Deshacer (Undo LIFO)
        // =========================================================================
        var preparacionServicio = new PreparacionVentaServicio(
                medicamentoRepositorio, usuarioRepositorio, lotePrioridadServicio, new DescuentoImporteFijo());

        // Agregar 10 unidades (precio S/ 10.00 -> Bruto S/ 100.00)
        preparacionServicio.agregar(usuarioVendedor, new PreparacionVentaCambioFormulario(idMedicamento, 10));
        var vista1 = preparacionServicio.obtener(usuarioVendedor);
        assertEquals(1, vista1.detalles().size());
        assertEquals(10, vista1.detalles().getFirst().cantidad());
        assertEquals(new BigDecimal("100.00"), vista1.total());
        assertTrue(vista1.puedeDeshacer(), "Debe poder deshacer tras agregar un ítem");

        // Probar deshacer: la preparación debe quedar vacía
        preparacionServicio.deshacer(usuarioVendedor);
        var vistaVacia = preparacionServicio.obtener(usuarioVendedor);
        assertTrue(vistaVacia.detalles().isEmpty(), "Tras deshacer, la lista de preparación debe quedar vacía");
        assertEquals(new BigDecimal("0.00"), vistaVacia.total());

        // Volver a agregar 5 unidades (precio S/ 10.00 -> Bruto S/ 50.00)
        preparacionServicio.agregar(usuarioVendedor, new PreparacionVentaCambioFormulario(idMedicamento, 5));
        var vista2 = preparacionServicio.obtener(usuarioVendedor);
        assertEquals(1, vista2.detalles().size());
        assertEquals(5, vista2.detalles().getFirst().cantidad());
        assertEquals(new BigDecimal("50.00"), vista2.subtotal());

        // =========================================================================
        // 7. Aplicar Descuento de Línea
        // =========================================================================
        // Descuento de S/ 5.00: Subtotal 50.00, Descuento 5.00, Total Neto 45.00
        preparacionServicio.aplicarDescuento(usuarioVendedor, idMedicamento, new BigDecimal("5.00"));
        var vistaConDescuento = preparacionServicio.obtener(usuarioVendedor);
        assertEquals(new BigDecimal("50.00"), vistaConDescuento.subtotal());
        assertEquals(new BigDecimal("5.00"), vistaConDescuento.descuentoTotal());
        assertEquals(new BigDecimal("45.00"), vistaConDescuento.total());

        // =========================================================================
        // 8. Confirmar Venta
        // =========================================================================
        VentaConfirmadaVista ventaConfirmada = ventaServicio.confirmar(
                usuarioVendedor, idCliente, vistaConDescuento.detalles());
        assertNotNull(ventaConfirmada);
        assertNotNull(ventaConfirmada.idVenta());
        assertTrue(ventaConfirmada.numeroComprobante().matches("B001-\\d{8}"),
                "El número de comprobante debe tener el formato correlativo B001-XXXXXXXX");

        // FEFO: El medicamento tenía 30 en Lote 1 (vence en 180 días) y 20 en Lote 2 (vence en 365 días).
        // Se vendieron 5 unidades -> Debieron descontarse del Lote 1 (vencimiento más cercano).
        int stockLote1Posterior = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_medicamento = ? AND numero_lote = ?",
                Integer.class, idMedicamento, numeroLote1);
        assertEquals(25, stockLote1Posterior, "El Lote 1 debió quedar con 25 unidades (30 - 5)");

        int stockLote2Posterior = jdbcTemplate.queryForObject(
                "SELECT stock_actual FROM lote WHERE id_medicamento = ? AND numero_lote = ?",
                Integer.class, idMedicamento, numeroLote2);
        assertEquals(20, stockLote2Posterior, "El Lote 2 debe conservar sus 20 unidades íntegras");

        // Movimiento de inventario tipo SALIDA asociado a la venta
        int salidas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_venta = ? AND tipo = 'SALIDA'",
                Integer.class, ventaConfirmada.idVenta());
        assertEquals(1, salidas, "Debe registrarse 1 movimiento de SALIDA por la venta");

        // =========================================================================
        // 9. Comprobante Interno
        // =========================================================================
        ComprobanteVista comprobante = ventaServicio.obtenerComprobante(ventaConfirmada.idVenta(), usuarioVendedor)
                .orElseThrow(() -> new AssertionError("Comprobante no encontrado"));
        assertEquals(ventaConfirmada.numeroComprobante(), comprobante.numeroComprobante());
        assertEquals("Carlos Sanchez Gomez", comprobante.cliente());
        assertEquals(new BigDecimal("50.00"), comprobante.subtotal());
        assertEquals(new BigDecimal("5.00"), comprobante.descuentoTotal());
        assertEquals(new BigDecimal("45.00"), comprobante.total());
        assertEquals(1, comprobante.lineas().size());
        assertEquals(5, comprobante.lineas().getFirst().cantidad());

        // =========================================================================
        // 10. Historial de Ventas (Administrador)
        // =========================================================================
        autenticarComo(usuarioAdmin);
        var historial = ventaServicio.listarHistorial(usuarioAdmin, null, 1);
        assertTrue(historial.total() >= 1, "El historial debe contener al menos 1 venta");
        boolean ventaEnHistorial = historial.filas().stream()
                .anyMatch(v -> v.idVenta().equals(ventaConfirmada.idVenta())
                        && v.numeroComprobante().equals(ventaConfirmada.numeroComprobante())
                        && v.total().compareTo(new BigDecimal("45.00")) == 0);
        assertTrue(ventaEnHistorial, "La venta debe figurar en el historial general con su total exacto");

        // =========================================================================
        // 11. Reporte de Ventas
        // =========================================================================
        var ventasPorDia = reporteServicio.ventasPorDia(usuarioAdmin, null);
        assertFalse(ventasPorDia.isEmpty(), "El reporte de ventas por día debe contener datos");
        var hoyVentas = ventasPorDia.stream()
                .filter(v -> v.fecha().equals(hoy))
                .findFirst();
        assertTrue(hoyVentas.isPresent(), "Debe existir registro de ventas para la fecha de hoy");
        assertTrue(hoyVentas.get().cantidadVentas() >= 1);
        assertTrue(hoyVentas.get().total().compareTo(new BigDecimal("45.00")) >= 0);

        var panelResumen = reporteServicio.panelResumen(usuarioAdmin);
        assertTrue(panelResumen.totalMes().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(panelResumen.cantidadVentasMes() >= 1);

        // =========================================================================
        // 12. Auditoría en Bitácora (Coherencia integral de eventos)
        // =========================================================================
        // Comprobar eventos en la tabla de auditoría
        int eventosCrearOrden = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE accion = 'CREAR' AND entidad = 'ORDEN_COMPRA' AND id_entidad = ?",
                Integer.class, String.valueOf(idOrden));
        assertEquals(1, eventosCrearOrden, "Debe existir auditoría de creación de la orden");

        int eventosRecepcionOrden = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE accion = 'RECEPCION' AND entidad = 'ORDEN_COMPRA' AND id_entidad = ?",
                Integer.class, String.valueOf(idOrden));
        assertEquals(2, eventosRecepcionOrden, "Deben existir 2 eventos de recepción (parcial y total)");

        int eventosCrearCliente = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE accion = 'CREAR' AND entidad = 'CLIENTE' AND id_entidad = ?",
                Integer.class, String.valueOf(idCliente));
        assertEquals(1, eventosCrearCliente, "Debe existir auditoría de creación del cliente");

        int eventosConfirmarVenta = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE accion = 'CONFIRMAR_VENTA' AND entidad = 'VENTA' AND id_entidad = ?",
                Integer.class, String.valueOf(ventaConfirmada.idVenta()));
        assertEquals(1, eventosConfirmarVenta, "Debe existir auditoría de confirmación de la venta");

        var auditoriaConsulta = auditoriaServicio.consultar(usuarioAdmin, null, 1);
        assertTrue(auditoriaConsulta.total() >= 4, "La bitácora debe contener al menos los 4 eventos del ciclo");
    }
}
