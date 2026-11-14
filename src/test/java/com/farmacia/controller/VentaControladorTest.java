package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.LineaPreparacionVentaVista;
import com.farmacia.model.PreparacionVentaVista;
import com.farmacia.model.VentaConfirmadaVista;
import com.farmacia.service.PreparacionVentaServicio;
import com.farmacia.service.StockInsuficienteException;
import com.farmacia.service.UsuarioDetallesServicio;
import com.farmacia.service.VentaServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 003, T310: POST /ventas/confirmar (200, 400, 403, 302) y vaciado de la preparación. */
@WebMvcTest(VentaControlador.class)
@Import(SeguridadConfiguracion.class)
class VentaControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VentaServicio ventaServicio;

    @MockitoBean
    private PreparacionVentaServicio preparacionServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    private final List<LineaPreparacionVentaVista> lineas = List.of(new LineaPreparacionVentaVista(
            5, "MED-001", "Paracetamol", 4, new BigDecimal("8.50"), new BigDecimal("34.00"), List.of()));

    @Test
    @DisplayName("200: confirma con la preparación de la sesión, devuelve id y comprobante y vacía la preparación")
    void confirmaYVaciaLaPreparacion() throws Exception {
        when(preparacionServicio.obtener("vendedor1"))
                .thenReturn(new PreparacionVentaVista(lineas, new BigDecimal("34.00"), true));
        when(ventaServicio.confirmar("vendedor1", 3, lineas))
                .thenReturn(new VentaConfirmadaVista(12, "B001-00000012"));

        mvc.perform(post("/ventas/confirmar").with(user("vendedor1").roles("VENDEDOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idCliente\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idVenta").value(12))
                .andExpect(jsonPath("$.numeroComprobante").value("B001-00000012"));

        verify(preparacionServicio).limpiar("vendedor1");
    }

    @Test
    @DisplayName("200: sin cliente la venta es para Público general (idCliente null)")
    void confirmaSinCliente() throws Exception {
        when(preparacionServicio.obtener("admin"))
                .thenReturn(new PreparacionVentaVista(lineas, new BigDecimal("34.00"), false));
        when(ventaServicio.confirmar("admin", null, lineas)).thenReturn(new VentaConfirmadaVista(1, "B001-00000001"));

        mvc.perform(post("/ventas/confirmar").with(user("admin").roles("ADMINISTRADOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idCliente\": null}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("400: un error de negocio responde {mensaje} y NO vacía la preparación (se conserva tras el rollback)")
    void errorDeNegocioConservaLaPreparacion() throws Exception {
        when(preparacionServicio.obtener("vendedor1"))
                .thenReturn(new PreparacionVentaVista(lineas, new BigDecimal("34.00"), true));
        when(ventaServicio.confirmar(eq("vendedor1"), any(), any()))
                .thenThrow(new StockInsuficienteException("El stock cambió; vuelve a preparar la venta"));

        mvc.perform(post("/ventas/confirmar").with(user("vendedor1").roles("VENDEDOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El stock cambió; vuelve a preparar la venta"));

        verify(preparacionServicio, never()).limpiar(anyString());
    }

    @Test
    @DisplayName("400: un idCliente no positivo se rechaza por validación")
    void validaElCliente() throws Exception {
        mvc.perform(post("/ventas/confirmar").with(user("vendedor1").roles("VENDEDOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idCliente\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Selecciona un cliente válido"));
        verifyNoInteractions(ventaServicio);
    }

    @Test
    @DisplayName("403: Almacenero y Compras no pueden confirmar ventas")
    void rolesSinPermiso() throws Exception {
        for (String rol : List.of("ALMACENERO", "COMPRAS")) {
            mvc.perform(post("/ventas/confirmar").with(user("u").roles(rol)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(ventaServicio, preparacionServicio);
    }

    @Test
    @DisplayName("403: sin token CSRF se rechaza")
    void exigeCsrf() throws Exception {
        mvc.perform(post("/ventas/confirmar").with(user("vendedor1").roles("VENDEDOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ventaServicio);
    }

    @Test
    @DisplayName("302: sin sesión redirige al login")
    void sinSesionRedirigeAlLogin() throws Exception {
        mvc.perform(post("/ventas/confirmar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }
}
