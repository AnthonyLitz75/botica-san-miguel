package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.dto.FiltroVentasFormulario;
import com.farmacia.model.Usuario;
import com.farmacia.model.VentaResumenVista;
import com.farmacia.service.UsuarioDetallesServicio;
import com.farmacia.service.VentaServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Pruebas del controlador del historial de ventas (spec 005, US-1, T504, T505).
 */
@WebMvcTest(HistorialVentasControlador.class)
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class HistorialVentasControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VentaServicio ventaServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    @Test
    @DisplayName("CA-1.1, CA-1.2, CA-1.5: el administrador ve el historial con totales, enlace al comprobante y menú activo")
    void listadoExitoso() throws Exception {
        var fila = new VentaResumenVista(
                10,
                "B001-00000010",
                LocalDateTime.of(2026, 11, 13, 14, 30),
                2,
                "vendedor_demo",
                "Juan Perez",
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("50.00"));

        var vendedor = new Usuario(2, "Vendedor Demo", "vendedor_demo", "hash", "VENDEDOR", true);
        when(ventaServicio.listarVendedores()).thenReturn(List.of(vendedor));

        var historial = new VentaServicio.Historial(
                List.of(fila),
                new BigDecimal("50.00"),
                1,
                1,
                1,
                LocalDate.of(2026, 10, 14),
                LocalDate.of(2026, 11, 13));

        when(ventaServicio.listarHistorial(eq("admin"), any(), eq(1))).thenReturn(historial);

        mvc.perform(get("/ventas").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ventas/historial"))
                .andExpect(model().attribute("puedeConsultarVentas", true))
                .andExpect(model().attributeExists("filas", "totalFiltrado", "pagina", "paginas", "vendedores"))
                .andExpect(content().string(containsString("B001-00000010")))
                .andExpect(content().string(containsString("vendedor_demo")))
                .andExpect(content().string(containsString("Juan Perez")))
                .andExpect(content().string(containsString("/ventas/10")));
    }

    @Test
    @DisplayName("CA-1.3: los filtros se transmiten al servicio")
    void listadoConFiltros() throws Exception {
        when(ventaServicio.listarVendedores()).thenReturn(List.of());
        when(ventaServicio.listarHistorial(eq("admin"), any(FiltroVentasFormulario.class), eq(2)))
                .thenReturn(new VentaServicio.Historial(List.of(), BigDecimal.ZERO, 0, 1, 1, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)));

        mvc.perform(get("/ventas")
                .param("desde", "2026-11-01")
                .param("hasta", "2026-11-10")
                .param("idVendedor", "2")
                .param("comprobante", "B001")
                .param("pagina", "2")
                .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ventas/historial"));

        verify(ventaServicio).listarHistorial(eq("admin"), any(FiltroVentasFormulario.class), eq(2));
    }

    @Test
    @DisplayName("CA-1.4: si hasta < desde muestra mensaje de validación sin error 500")
    void errorRangoInvalido() throws Exception {
        when(ventaServicio.listarVendedores()).thenReturn(List.of());
        when(ventaServicio.listarHistorial(eq("admin"), any(), anyInt()))
                .thenThrow(new IllegalArgumentException("La fecha final no puede ser anterior a la inicial"));

        mvc.perform(get("/ventas")
                .param("desde", "2026-11-10")
                .param("hasta", "2026-11-01")
                .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ventas/historial"))
                .andExpect(model().attribute("error", "La fecha final no puede ser anterior a la inicial"))
                .andExpect(content().string(containsString("La fecha final no puede ser anterior a la inicial")));
    }

    @ParameterizedTest(name = "Rol {0} recibe 403 en GET /ventas")
    @ValueSource(strings = {"VENDEDOR", "ALMACENERO", "COMPRAS"})
    @DisplayName("CA-1.7: VENDEDOR, ALMACENERO y COMPRAS reciben 403 en GET /ventas")
    void rolesNoAutorizadosReciben403(String rol) throws Exception {
        mvc.perform(get("/ventas").with(user("usuario-" + rol).roles(rol)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ventaServicio);
    }

    @Test
    @DisplayName("CA-4.1, CA-4.5: el vendedor ve sus ventas con enlace para reimprimir comprobante")
    void misVentas_exitoso() throws Exception {
        var fila = new VentaResumenVista(
                15,
                "B001-00000015",
                LocalDateTime.of(2026, 11, 13, 16, 0),
                2,
                "vendedor_demo",
                null,
                new BigDecimal("25.00"),
                BigDecimal.ZERO,
                new BigDecimal("25.00"));

        var misVentas = new VentaServicio.MisVentas(
                List.of(fila),
                LocalDate.of(2026, 11, 13),
                LocalDate.of(2026, 11, 13));

        when(ventaServicio.listarMisVentas(eq("vendedor_demo"), any(), any())).thenReturn(misVentas);

        mvc.perform(get("/ventas/mias").with(user("vendedor_demo").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ventas/mis-ventas"))
                .andExpect(model().attribute("puedeVerMisVentas", true))
                .andExpect(model().attributeExists("filas", "desde", "hasta"))
                .andExpect(content().string(containsString("B001-00000015")))
                .andExpect(content().string(containsString("Público general")))
                .andExpect(content().string(containsString("/ventas/15")));
    }

    @Test
    @DisplayName("CA-4.2: rango inválido en mis ventas muestra mensaje sin lanzar 500")
    void misVentas_errorRango() throws Exception {
        when(ventaServicio.listarMisVentas(eq("vendedor_demo"), any(), any()))
                .thenThrow(new IllegalArgumentException("El rango de fechas no puede superar los 30 días hacia atrás"));

        mvc.perform(get("/ventas/mias")
                .param("desde", "2026-10-01")
                .param("hasta", "2026-11-13")
                .with(user("vendedor_demo").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ventas/mis-ventas"))
                .andExpect(model().attribute("error", "El rango de fechas no puede superar los 30 días hacia atrás"))
                .andExpect(content().string(containsString("El rango de fechas no puede superar los 30 días hacia atrás")));
    }

    @ParameterizedTest(name = "Rol {0} recibe 403 en GET /ventas/mias")
    @ValueSource(strings = {"ADMINISTRADOR", "ALMACENERO", "COMPRAS"})
    @DisplayName("CA-4.5: ADMINISTRADOR, ALMACENERO y COMPRAS reciben 403 en GET /ventas/mias")
    void rolesNoAutorizadosReciben403EnMisVentas(String rol) throws Exception {
        mvc.perform(get("/ventas/mias").with(user("usuario-" + rol).roles(rol)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ventaServicio);
    }
}
