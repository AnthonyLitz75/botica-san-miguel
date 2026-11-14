package com.farmacia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farmacia.dto.FiltroReporteFormulario;
import com.farmacia.model.MedicamentoRotacionVista;
import com.farmacia.model.Usuario;
import com.farmacia.model.VentasDiaVista;
import com.farmacia.repository.ReporteRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ReporteServicioTest {

    @Mock
    private ReporteRepositorio reporteRepositorio;

    @Mock
    private UsuarioRepositorio usuarioRepositorio;

    @Mock
    private AlertaInventarioServicio alertaInventarioServicio;

    @Mock
    private AuditoriaServicio auditoriaServicio;

    private Clock clock;
    private ReporteServicio reporteServicio;

    private final LocalDate hoy = LocalDate.of(2026, 11, 13);

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneId.of("UTC"));
        reporteServicio = new ReporteServicio(
                reporteRepositorio, usuarioRepositorio, clock, alertaInventarioServicio, auditoriaServicio);
    }

    private void simularAdmin(String username) {
        var admin = new Usuario(1, "Admin", username, "h", "ADMINISTRADOR", true);
        when(usuarioRepositorio.buscarPorNombreUsuario(username)).thenReturn(Optional.of(admin));
    }

    private void simularVendedor(String username) {
        var vend = new Usuario(2, "Vendedor", username, "h", "VENDEDOR", true);
        when(usuarioRepositorio.buscarPorNombreUsuario(username)).thenReturn(Optional.of(vend));
    }

    @Test
    @DisplayName("CA-5.2: Usuario no administrador recibe AccessDeniedException")
    void ventasPorDia_rolNoAdmin_lanzaAccessDenied() {
        simularVendedor("vendedor");
        var filtro = FiltroReporteFormulario.porDefecto(clock);

        assertThrows(AccessDeniedException.class, () ->
                reporteServicio.ventasPorDia("vendedor", filtro));
    }

    @Test
    @DisplayName("CA-1.3: Rango invertido lanza IllegalArgumentException")
    void ventasPorDia_rangoInvertido_lanzaIllegalArgument() {
        simularAdmin("admin");
        var filtro = new FiltroReporteFormulario(
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 5),
                10, null, null);

        assertThrows(IllegalArgumentException.class, () ->
                reporteServicio.ventasPorDia("admin", filtro));
    }

    @Test
    @DisplayName("CA-1.3: Rango > 366 días lanza IllegalArgumentException")
    void ventasPorDia_rangoExcesivo_lanzaIllegalArgument() {
        simularAdmin("admin");
        var filtro = new FiltroReporteFormulario(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 5),
                10, null, null);

        assertThrows(IllegalArgumentException.class, () ->
                reporteServicio.ventasPorDia("admin", filtro));
    }

    @Test
    @DisplayName("CA-1.1: Filtro sin fechas aplica por defecto últimos 30 días calculados con Clock")
    void ventasPorDia_sinFechas_aplicaUltimos30Dias() {
        simularAdmin("admin");
        var filtroVacio = new FiltroReporteFormulario(null, null, null, null, null);

        when(reporteRepositorio.ventasPorDia(hoy.minusDays(30), hoy))
                .thenReturn(List.of(new VentasDiaVista(hoy, 1, new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"))));

        List<VentasDiaVista> resultado = reporteServicio.ventasPorDia("admin", filtroVacio);

        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(reporteRepositorio).ventasPorDia(hoy.minusDays(30), hoy);
    }

    @Test
    @DisplayName("CA-2.1: masVendidos usa límite 10 por defecto y consulta al repositorio")
    void masVendidos_limitePorDefecto() {
        simularAdmin("admin");
        var filtroSinLimite = new FiltroReporteFormulario(hoy.minusDays(5), hoy, null, null, null);

        when(reporteRepositorio.masVendidos(hoy.minusDays(5), hoy, 10))
                .thenReturn(List.of(new MedicamentoRotacionVista(1, "PAR", "Paracetamol", 20, new BigDecimal("40.00"))));

        List<MedicamentoRotacionVista> resultado = reporteServicio.masVendidos("admin", filtroSinLimite);

        assertEquals(1, resultado.size());
        verify(reporteRepositorio).masVendidos(hoy.minusDays(5), hoy, 10);
    }

    @Test
    @DisplayName("CA-4.1: panelResumen calcula ventas del mes, ticket promedio y alertas")
    void panelResumen_calculaMetricasCorrectamente() {
        simularAdmin("admin");
        LocalDate inicioMes = hoy.withDayOfMonth(1);

        var v1 = new VentasDiaVista(inicioMes, 2, new BigDecimal("60.00"), BigDecimal.ZERO, new BigDecimal("60.00"));
        var v2 = new VentasDiaVista(hoy, 3, new BigDecimal("90.00"), BigDecimal.ZERO, new BigDecimal("90.00"));

        when(reporteRepositorio.ventasPorDia(inicioMes, hoy)).thenReturn(List.of(v1, v2));
        when(alertaInventarioServicio.alertasStock()).thenReturn(List.of(
                new com.farmacia.model.AlertaStockVista(1, "A1", "Med 1", "TAB", 0, 10, 0, 10, "AGOTADO")
        ));
        when(alertaInventarioServicio.listarPorVencer()).thenReturn(List.of(
                new com.farmacia.model.AlertaVencimientoVista(1, 1, "A1", "Med 1", "L1", hoy.plusDays(10), 5, "TAB", 10L, "POR_VENCER")
        ));
        when(alertaInventarioServicio.diasPorVencer()).thenReturn(90);

        var panel = reporteServicio.panelResumen("admin");

        assertNotNull(panel);
        // Total = 60 + 90 = 150.00
        assertEquals(new BigDecimal("150.00"), panel.totalMes());
        // Cantidad ventas = 2 + 3 = 5
        assertEquals(5, panel.cantidadVentasMes());
        // Ticket promedio = 150 / 5 = 30.00
        assertEquals(new BigDecimal("30.00"), panel.ticketPromedio());
        assertEquals(1, panel.medicamentosAlertaStock());
        assertEquals(1, panel.lotesPorVencer());
        assertEquals(90, panel.diasPorVencer());
    }

    @Test
    @DisplayName("CA-6.1: reporteInventario delega al repositorio con fecha de corte de Clock")
    void reporteInventario_admin_consultaRepositorioConFechaHoy() {
        simularAdmin("admin");
        var item = new com.farmacia.model.InventarioReporteVista(
                1, "M1", "Med 1", "TAB", 20, 0, 10, "OK", List.of()
        );
        when(reporteRepositorio.reporteInventario(hoy, "OK")).thenReturn(List.of(item));

        var res = reporteServicio.reporteInventario("admin", "OK");

        assertEquals(1, res.size());
        verify(reporteRepositorio).reporteInventario(hoy, "OK");
    }

    @Test
    @DisplayName("CA-5.2: reporteInventario por usuario no admin lanza AccessDeniedException")
    void reporteInventario_noAdmin_lanzaAccessDenied() {
        simularVendedor("vendedor");

        assertThrows(AccessDeniedException.class, () ->
                reporteServicio.reporteInventario("vendedor", null));
    }

    @Test
    @DisplayName("CA-7.1: reporteCompras delega al repositorio con filtro normalizado")
    void reporteCompras_admin_consultaRepositorioConFiltroNormalizado() {
        simularAdmin("admin");
        var filtro = new FiltroReporteFormulario(null, null, null, "PENDIENTE", 5);
        var compra = new com.farmacia.model.CompraReporteVista(
                1, "OC-001", hoy, "Droguería Test", "PENDIENTE", new BigDecimal("100.00"), 50, 0, 50
        );

        when(reporteRepositorio.reporteCompras(hoy.minusDays(30), hoy, "PENDIENTE", 5))
                .thenReturn(List.of(compra));

        var res = reporteServicio.reporteCompras("admin", filtro);

        assertEquals(1, res.size());
        verify(reporteRepositorio).reporteCompras(hoy.minusDays(30), hoy, "PENDIENTE", 5);
    }

    @Test
    @DisplayName("CA-5.2: reporteCompras por usuario no admin lanza AccessDeniedException")
    void reporteCompras_noAdmin_lanzaAccessDenied() {
        simularVendedor("vendedor");
        var filtro = FiltroReporteFormulario.porDefecto(clock);

        assertThrows(AccessDeniedException.class, () ->
                reporteServicio.reporteCompras("vendedor", filtro));
    }

    @Test
    @DisplayName("CA-7.2: resumenCompras calcula totales por estado y consolidado general")
    void resumenCompras_calculaTotalesPorEstadoYGeneral() {
        var c1 = new com.farmacia.model.CompraReporteVista(
                1, "OC-01", hoy, "P1", "PENDIENTE", new BigDecimal("100.00"), 50, 0, 50
        );
        var c2 = new com.farmacia.model.CompraReporteVista(
                2, "OC-02", hoy, "P1", "PENDIENTE", new BigDecimal("50.00"), 25, 0, 25
        );
        var c3 = new com.farmacia.model.CompraReporteVista(
                3, "OC-03", hoy, "P2", "PARCIALMENTE_RECIBIDA", new BigDecimal("300.00"), 100, 40, 60
        );

        var resumen = reporteServicio.resumenCompras(List.of(c1, c2, c3));

        assertEquals(3, resumen.totalOrdenes());
        assertEquals(new BigDecimal("450.00"), resumen.montoTotal());

        var totPendiente = resumen.totalesPorEstado().get("PENDIENTE");
        assertEquals(2, totPendiente.cantidad());
        assertEquals(new BigDecimal("150.00"), totPendiente.monto());

        var totParcial = resumen.totalesPorEstado().get("PARCIALMENTE_RECIBIDA");
        assertEquals(1, totParcial.cantidad());
        assertEquals(new BigDecimal("300.00"), totParcial.monto());

        var totRecibida = resumen.totalesPorEstado().get("RECIBIDA");
        assertEquals(0, totRecibida.cantidad());
        assertEquals(BigDecimal.ZERO, totRecibida.monto());
    }

    @Test
    @DisplayName("CA-5.1: exportarVentasCsv genera cabecera, coincide fila a fila y registra evento EXPORTAR")
    void exportarVentasCsv_generaCsvYRegistraAuditoria() {
        simularAdmin("admin");
        var fila = new VentasDiaVista(hoy, 2, new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("90.00"));
        when(reporteRepositorio.ventasPorDia(hoy.minusDays(30), hoy)).thenReturn(List.of(fila));

        String csv = reporteServicio.exportarVentasCsv("admin", null);

        assertNotNull(csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith("fecha,cantidad_ventas,subtotal,descuento,total\r\n"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("2026-11-13,2,100.00,10.00,90.00\r\n"));
        verify(auditoriaServicio).registrarCambio(eq(AccionAuditoria.EXPORTAR), eq("REPORTE_VENTAS"), any(), any());
    }

    @Test
    @DisplayName("CA-5.1: exportarMasVendidosCsv genera cabecera, coincide fila a fila y registra evento EXPORTAR")
    void exportarMasVendidosCsv_generaCsvYRegistraAuditoria() {
        simularAdmin("admin");
        var rot = new MedicamentoRotacionVista(1, "PAR-01", "Paracetamol, 500mg", 50, new BigDecimal("100.00"));
        when(reporteRepositorio.masVendidos(hoy.minusDays(30), hoy, 10)).thenReturn(List.of(rot));

        String csv = reporteServicio.exportarMasVendidosCsv("admin", null);

        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith("codigo,nombre,unidades_vendidas,total_vendido\r\n"));
        // El nombre contiene una coma, por lo que debe ir entrecomillado según RFC 4180
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("PAR-01,\"Paracetamol, 500mg\",50,100.00\r\n"));
        verify(auditoriaServicio).registrarCambio(eq(AccionAuditoria.EXPORTAR), eq("REPORTE_MAS_VENDIDOS"), any(), any());
    }

    @Test
    @DisplayName("CA-5.1: exportarMermasCsv genera cabecera, coincide fila a fila y registra evento EXPORTAR")
    void exportarMermasCsv_generaCsvYRegistraAuditoria() {
        simularAdmin("admin");
        var merma = new com.farmacia.model.MermaVista(1, "IBU-01", "Ibuprofeno", 8);
        when(reporteRepositorio.mermas(hoy.minusDays(30), hoy)).thenReturn(List.of(merma));

        String csv = reporteServicio.exportarMermasCsv("admin", null);

        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith("codigo,nombre,unidades_baja\r\n"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("IBU-01,Ibuprofeno,8\r\n"));
        verify(auditoriaServicio).registrarCambio(eq(AccionAuditoria.EXPORTAR), eq("REPORTE_MERMAS"), any(), any());
    }

    @Test
    @DisplayName("CA-5.1: exportarInventarioCsv genera cabecera, coincide fila a fila y registra evento EXPORTAR")
    void exportarInventarioCsv_generaCsvYRegistraAuditoria() {
        simularAdmin("admin");
        var item = new com.farmacia.model.InventarioReporteVista(
                1, "MED-01", "Amoxicilina", "CAPSULA", 40, 5, 10, "OK", List.of()
        );
        when(reporteRepositorio.reporteInventario(hoy, "OK")).thenReturn(List.of(item));

        String csv = reporteServicio.exportarInventarioCsv("admin", "OK");

        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith("codigo,nombre,unidad_control,stock_disponible,stock_vencido,stock_minimo,estado\r\n"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("MED-01,Amoxicilina,CAPSULA,40,5,10,OK\r\n"));
        verify(auditoriaServicio).registrarCambio(eq(AccionAuditoria.EXPORTAR), eq("REPORTE_INVENTARIO"), any(), any());
    }

    @Test
    @DisplayName("CA-5.1: exportarComprasCsv genera cabecera, coincide fila a fila y registra evento EXPORTAR")
    void exportarComprasCsv_generaCsvYRegistraAuditoria() {
        simularAdmin("admin");
        var compra = new com.farmacia.model.CompraReporteVista(
                1, "OC-001", hoy, "Droguería Alfa", "PENDIENTE", new BigDecimal("350.00"), 100, 0, 100
        );
        when(reporteRepositorio.reporteCompras(hoy.minusDays(30), hoy, null, null)).thenReturn(List.of(compra));

        String csv = reporteServicio.exportarComprasCsv("admin", null);

        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith("numero_orden,fecha_emision,proveedor,estado,total_estimado,unidades_solicitadas,unidades_recibidas,unidades_pendientes\r\n"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("OC-001,2026-11-13,Droguería Alfa,PENDIENTE,350.00,100,0,100\r\n"));
        verify(auditoriaServicio).registrarCambio(eq(AccionAuditoria.EXPORTAR), eq("REPORTE_COMPRAS"), any(), any());
    }
}
