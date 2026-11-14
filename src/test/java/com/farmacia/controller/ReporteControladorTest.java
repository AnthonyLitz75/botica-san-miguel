package com.farmacia.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.dto.FiltroReporteFormulario;
import com.farmacia.model.MedicamentoRotacionVista;
import com.farmacia.model.VentasDiaVista;
import com.farmacia.service.ReporteServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReporteControlador.class)
@Import(SeguridadConfiguracion.class)
class ReporteControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReporteServicio reporteServicio;

    @MockitoBean
    private com.farmacia.service.ProveedorServicio proveedorServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    @Test
    @DisplayName("Petición anónima a /reportes/ventas redirige a /login (302)")
    void ventas_anonimo_redirigeALogin() throws Exception {
        mvc.perform(get("/reportes/ventas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @DisplayName("CA-5.2: VENDEDOR, ALMACENERO y COMPRAS reciben 403 Forbidden en /reportes/ventas y /reportes/mas-vendidos")
    void reportes_rolesNoAutorizados_retornan403() throws Exception {
        mvc.perform(get("/reportes/ventas").with(user("vend").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/reportes/mas-vendidos").with(user("vend").roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/reportes/ventas").with(user("almacen").roles("ALMACENERO")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/reportes/mas-vendidos").with(user("almacen").roles("ALMACENERO")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/reportes/ventas").with(user("compras").roles("COMPRAS")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/reportes/mas-vendidos").with(user("compras").roles("COMPRAS")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/reportes/inventario").with(user("almacen").roles("ALMACENERO")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/reportes/compras").with(user("compras").roles("COMPRAS")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /reportes/ventas como ADMINISTRADOR renderiza vista con datos y totales")
    void ventas_administrador_renderizaCorrectamente() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        var fila = new VentasDiaVista(hoy, 3, new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("90.00"));

        when(reporteServicio.normalizarFiltro(any())).thenReturn(new FiltroReporteFormulario(hoy.minusDays(7), hoy, 10, null, null));
        when(reporteServicio.ventasPorDia(eq("admin"), any())).thenReturn(List.of(fila));

        mvc.perform(get("/reportes/ventas").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes/ventas"))
                .andExpect(model().attributeExists("reporte", "totalVentas", "subtotal", "descuento", "total"))
                .andExpect(content().string(containsString("10/11/2026")))
                .andExpect(content().string(containsString("90.00")));
    }

    @Test
    @DisplayName("GET /reportes/mas-vendidos como ADMINISTRADOR renderiza ranking correctamente")
    void masVendidos_administrador_renderizaCorrectamente() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        var rotacion = new MedicamentoRotacionVista(1, "PAR-01", "Paracetamol 500mg", 45, new BigDecimal("90.00"));

        when(reporteServicio.normalizarFiltro(any())).thenReturn(new FiltroReporteFormulario(hoy.minusDays(30), hoy, 10, null, null));
        when(reporteServicio.masVendidos(eq("admin"), any())).thenReturn(List.of(rotacion));

        mvc.perform(get("/reportes/mas-vendidos").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes/mas-vendidos"))
                .andExpect(model().attributeExists("reporte", "totalUnidades", "totalVendido"))
                .andExpect(content().string(containsString("Paracetamol 500mg")))
                .andExpect(content().string(containsString("PAR-01")));
    }

    @Test
    @DisplayName("CA-3.1: GET /reportes/mermas como ADMINISTRADOR renderiza mermas correctamente")
    void mermas_administrador_renderizaCorrectamente() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        var merma = new com.farmacia.model.MermaVista(1, "IBU-01", "Ibuprofeno 400mg", 12);

        when(reporteServicio.normalizarFiltro(any())).thenReturn(new FiltroReporteFormulario(hoy.minusDays(30), hoy, 10, null, null));
        when(reporteServicio.mermas(eq("admin"), any())).thenReturn(List.of(merma));

        mvc.perform(get("/reportes/mermas").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes/mermas"))
                .andExpect(model().attributeExists("reporte", "totalUnidades"))
                .andExpect(content().string(containsString("Ibuprofeno 400mg")))
                .andExpect(content().string(containsString("12")));
    }

    @Test
    @DisplayName("CA-4.1: GET /reportes/panel como ADMINISTRADOR renderiza panel resumen")
    void panel_administrador_renderizaCorrectamente() throws Exception {
        var resumen = new com.farmacia.model.PanelVista(
                new BigDecimal("5000.00"), 100, new BigDecimal("50.00"), 3, 2, 90);

        when(reporteServicio.panelResumen("admin")).thenReturn(resumen);

        mvc.perform(get("/reportes/panel").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes/panel"))
                .andExpect(model().attributeExists("resumen"))
                .andExpect(content().string(containsString("5,000.00")))
                .andExpect(content().string(containsString("50.00")))
                .andExpect(content().string(containsString("100 ventas")));
    }

    @Test
    @DisplayName("CA-6.1, CA-6.2: GET /reportes/inventario como ADMINISTRADOR renderiza inventario con lotes")
    void inventario_administrador_renderizaCorrectamente() throws Exception {
        var lote = new com.farmacia.model.LoteInventarioReporteVista(
                10, "LOT-999", LocalDate.of(2027, 5, 20), 50, 180L
        );
        var item = new com.farmacia.model.InventarioReporteVista(
                1, "AMX-01", "Amoxicilina 500mg", "CAPSULA", 50, 0, 10, "OK", List.of(lote)
        );

        when(reporteServicio.reporteInventario("admin", "OK")).thenReturn(List.of(item));

        mvc.perform(get("/reportes/inventario")
                        .param("estado", "OK")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes/inventario"))
                .andExpect(model().attributeExists("reporte", "totalDisponible", "totalVencido", "totalAlertas", "estadoSeleccionado"))
                .andExpect(content().string(containsString("Amoxicilina 500mg")))
                .andExpect(content().string(containsString("AMX-01")))
                .andExpect(content().string(containsString("LOT-999")));
    }

    @Test
    @DisplayName("CA-7.1, CA-7.2: GET /reportes/compras como ADMINISTRADOR renderiza órdenes y resumen")
    void compras_administrador_renderizaCorrectamente() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        var compra = new com.farmacia.model.CompraReporteVista(
                1, "OC-2026-0001", hoy, "Droguería San Jorge", "PARCIALMENTE_RECIBIDA",
                new BigDecimal("500.00"), 100, 40, 60
        );
        var resumen = new com.farmacia.model.EstadoComprasResumenVista(
                java.util.Map.of(
                        "PENDIENTE", new com.farmacia.model.EstadoComprasResumenVista.CantidadYMonto(0, BigDecimal.ZERO),
                        "PARCIALMENTE_RECIBIDA", new com.farmacia.model.EstadoComprasResumenVista.CantidadYMonto(1, new BigDecimal("500.00")),
                        "RECIBIDA", new com.farmacia.model.EstadoComprasResumenVista.CantidadYMonto(0, BigDecimal.ZERO),
                        "CANCELADA", new com.farmacia.model.EstadoComprasResumenVista.CantidadYMonto(0, BigDecimal.ZERO)
                ),
                1,
                new BigDecimal("500.00")
        );

        when(reporteServicio.normalizarFiltro(any())).thenReturn(new FiltroReporteFormulario(hoy.minusDays(30), hoy, 10, null, null));
        when(reporteServicio.reporteCompras(eq("admin"), any())).thenReturn(List.of(compra));
        when(reporteServicio.resumenCompras(any())).thenReturn(resumen);

        mvc.perform(get("/reportes/compras").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes/compras"))
                .andExpect(model().attributeExists("reporte", "resumen", "filtro", "proveedores"))
                .andExpect(content().string(containsString("OC-2026-0001")))
                .andExpect(content().string(containsString("Droguería San Jorge")))
                .andExpect(content().string(containsString("PARCIAL")))
                .andExpect(content().string(containsString("500.00")));
    }

    @Test
    @DisplayName("CA-5.1: GET /reportes/ventas.csv como ADMINISTRADOR descarga archivo CSV")
    void ventasCsv_administrador_descargaCsv() throws Exception {
        when(reporteServicio.exportarVentasCsv(eq("admin"), any()))
                .thenReturn("fecha,cantidad_ventas,subtotal,descuento,total\r\n2026-11-10,3,100.00,10.00,90.00\r\n");

        mvc.perform(get("/reportes/ventas.csv").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"ventas.csv\"")))
                .andExpect(content().string(containsString("2026-11-10,3,100.00,10.00,90.00")));
    }

    @Test
    @DisplayName("CA-5.1: GET /reportes/mas-vendidos.csv como ADMINISTRADOR descarga archivo CSV")
    void masVendidosCsv_administrador_descargaCsv() throws Exception {
        when(reporteServicio.exportarMasVendidosCsv(eq("admin"), any()))
                .thenReturn("codigo,nombre,unidades_vendidas,total_vendido\r\nPAR-01,Paracetamol,50,100.00\r\n");

        mvc.perform(get("/reportes/mas-vendidos.csv").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"mas_vendidos.csv\"")))
                .andExpect(content().string(containsString("PAR-01,Paracetamol,50,100.00")));
    }

    @Test
    @DisplayName("CA-5.1: GET /reportes/mermas.csv como ADMINISTRADOR descarga archivo CSV")
    void mermasCsv_administrador_descargaCsv() throws Exception {
        when(reporteServicio.exportarMermasCsv(eq("admin"), any()))
                .thenReturn("codigo,nombre,unidades_baja\r\nIBU-01,Ibuprofeno,10\r\n");

        mvc.perform(get("/reportes/mermas.csv").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"mermas.csv\"")))
                .andExpect(content().string(containsString("IBU-01,Ibuprofeno,10")));
    }

    @Test
    @DisplayName("CA-5.1: GET /reportes/inventario.csv como ADMINISTRADOR descarga archivo CSV")
    void inventarioCsv_administrador_descargaCsv() throws Exception {
        when(reporteServicio.exportarInventarioCsv(eq("admin"), eq("OK")))
                .thenReturn("codigo,nombre,unidad_control,stock_disponible,stock_vencido,stock_minimo,estado\r\nAMX-01,Amoxicilina,CAPSULA,50,0,10,OK\r\n");

        mvc.perform(get("/reportes/inventario.csv")
                        .param("estado", "OK")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"inventario.csv\"")))
                .andExpect(content().string(containsString("AMX-01,Amoxicilina,CAPSULA,50,0,10,OK")));
    }

    @Test
    @DisplayName("CA-5.1: GET /reportes/compras.csv como ADMINISTRADOR descarga archivo CSV")
    void comprasCsv_administrador_descargaCsv() throws Exception {
        when(reporteServicio.exportarComprasCsv(eq("admin"), any()))
                .thenReturn("numero_orden,fecha_emision,proveedor,estado,total_estimado,unidades_solicitadas,unidades_recibidas,unidades_pendientes\r\nOC-001,2026-11-10,Droguería San Jorge,PENDIENTE,500.00,100,0,100\r\n");

        mvc.perform(get("/reportes/compras.csv").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"compras.csv\"")))
                .andExpect(content().string(containsString("OC-001,2026-11-10,Droguería San Jorge,PENDIENTE,500.00,100,0,100")));
    }
}
