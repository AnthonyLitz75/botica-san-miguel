package com.farmacia.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.dto.FiltroReporteFormulario;
import com.farmacia.model.BarraGraficoVista;
import com.farmacia.model.CompraReporteVista;
import com.farmacia.model.EstadoComprasResumenVista;
import com.farmacia.model.PanelVista;
import com.farmacia.model.VentasDiaVista;
import com.farmacia.service.ReporteServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReporteControlador.class)
@Import(SeguridadConfiguracion.class)
class AccesibilidadVistaTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReporteServicio reporteServicio;

    @MockitoBean
    private com.farmacia.service.ProveedorServicio proveedorServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarioDetallesServicio;

    @Test
    @DisplayName("A11Y-03: Wrapper de tabla de compras tiene tabindex='0', role='region' y aria-label descriptivo")
    void tablaCompras_wrapperTieneTabindexYRoleRegionYAriaLabel() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        var compra = new CompraReporteVista(
                1, "OC-2026-0001", hoy, "Proveedor Test", "PENDIENTE",
                new BigDecimal("100.00"), 10, 0, 10
        );
        var vacio = new EstadoComprasResumenVista.CantidadYMonto(0, BigDecimal.ZERO);
        var resumen = new EstadoComprasResumenVista(
                Map.of(
                        "PENDIENTE", new EstadoComprasResumenVista.CantidadYMonto(1, new BigDecimal("100.00")),
                        "PARCIALMENTE_RECIBIDA", vacio,
                        "RECIBIDA", vacio,
                        "CANCELADA", vacio
                ),
                1, new BigDecimal("100.00")
        );
        when(reporteServicio.normalizarFiltro(any())).thenReturn(new FiltroReporteFormulario(hoy, hoy, 10, null, null));
        when(reporteServicio.reporteCompras(eq("admin"), any())).thenReturn(List.of(compra));
        when(reporteServicio.resumenCompras(any())).thenReturn(resumen);

        mvc.perform(get("/reportes/compras").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tabindex=\"0\"")))
                .andExpect(content().string(containsString("role=\"region\"")))
                .andExpect(content().string(containsString("aria-label=\"Tabla de compras\"")));
    }

    @Test
    @DisplayName("A11Y-02: Cada barra del gráfico de ventas tiene role='img'")
    void graficoBarras_cadaBarraTieneRoleImg() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        var filtro = new FiltroReporteFormulario(hoy.minusDays(6), hoy, 10, null, null);
        var fila = new VentasDiaVista(hoy, 1, new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"));
        var barras = List.of(new BarraGraficoVista(hoy, new BigDecimal("50.00"), 100));

        when(reporteServicio.normalizarFiltro(any())).thenReturn(filtro);
        when(reporteServicio.ventasPorDia(eq("admin"), eq(filtro))).thenReturn(List.of(fila));
        when(reporteServicio.calcularBarrasGrafico(any(), any(), any())).thenReturn(barras);
        when(reporteServicio.generarResumenGrafico(any(), any(), any())).thenReturn("Resumen gráfico de prueba");

        mvc.perform(get("/reportes/ventas")
                .param("desde", hoy.minusDays(6).toString())
                .param("hasta", hoy.toString())
                .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"barra-item\"")))
                .andExpect(content().string(containsString("role=\"img\"")));
    }

    @Test
    @DisplayName("A11Y-04: Panel resumen usa <h2> para 'Reportes detallados' sin salto de nivel h1->h3")
    void panelResumen_encabezadoReportesEsH2SinSalto() throws Exception {
        var resumen = new PanelVista(new BigDecimal("1000.00"), 10, new BigDecimal("100.00"), 1, 0, 90);
        when(reporteServicio.panelResumen("admin")).thenReturn(resumen);

        mvc.perform(get("/reportes/panel").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h2 class=\"text-base font-bold mb-1 text-white\">Reportes detallados para la toma de decisiones</h2>")))
                .andExpect(content().string(not(containsString("<h3 class=\"text-base font-bold mb-1 text-white\">Reportes detallados para la toma de decisiones</h3>"))));
    }

    @Test
    @DisplayName("A11Y-05: Logo en la navegación lateral tiene alt='' (decorativo)")
    void navegacionLateral_logoTieneAltVacio() throws Exception {
        var resumen = new PanelVista(new BigDecimal("1000.00"), 10, new BigDecimal("100.00"), 1, 0, 90);
        when(reporteServicio.panelResumen("admin")).thenReturn(resumen);

        mvc.perform(get("/reportes/panel").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alt=\"\"")));
    }

    @Test
    @DisplayName("A11Y-06: Descuentos en tabla de ventas usan clase text-rose-700 para contraste >= 4.5:1")
    void reporteVentas_descuentosUsanTextRose700() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 11, 10);
        var filtro = new FiltroReporteFormulario(hoy.minusDays(6), hoy, 10, null, null);
        var fila = new VentasDiaVista(hoy, 1, new BigDecimal("50.00"), new BigDecimal("5.00"), new BigDecimal("45.00"));

        when(reporteServicio.normalizarFiltro(any())).thenReturn(filtro);
        when(reporteServicio.ventasPorDia(eq("admin"), eq(filtro))).thenReturn(List.of(fila));
        when(reporteServicio.calcularBarrasGrafico(any(), any(), any())).thenReturn(List.of());
        when(reporteServicio.generarResumenGrafico(any(), any(), any())).thenReturn("Resumen gráfico de prueba");

        mvc.perform(get("/reportes/ventas")
                .param("desde", hoy.minusDays(6).toString())
                .param("hasta", hoy.toString())
                .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"text-rose-700\"")));
    }
}
