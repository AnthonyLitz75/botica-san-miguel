package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.OrdenCompraListadoVista;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.OrdenCompraServicio;
import com.farmacia.service.ProveedorServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Pruebas del controlador de órdenes de compra para spec 018 (CA-3.1, CA-3.2).
 */
@WebMvcTest(OrdenCompraControlador.class)
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class OrdenCompraControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrdenCompraServicio servicio;

    @MockitoBean
    private ProveedorServicio proveedorServicio;

    @MockitoBean
    private MedicamentoServicio medicamentoServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    @Test
    @DisplayName("CA-3.2: ?estado=PENDIENTE filtra por estado en el servicio y pasa estadoSeleccionado al modelo")
    void filtrarPorEstadoPendiente() throws Exception {
        var orden = new OrdenCompraListadoVista(1, "OC-2026-0001", 1, 10, "Droguería Farmaperú",
                LocalDateTime.now(), "PENDIENTE", new BigDecimal("100.00"), null);

        when(servicio.listarVista("PENDIENTE")).thenReturn(List.of(orden));
        when(servicio.contarPorEstado()).thenReturn(Map.of(
                "TODAS", 4,
                "PENDIENTE", 1,
                "PARCIALMENTE_RECIBIDA", 1,
                "RECIBIDA", 1,
                "CANCELADA", 1));

        mvc.perform(get("/ordenes-compra")
                        .param("estado", "PENDIENTE")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ordenes-compra/listado"))
                .andExpect(model().attribute("estadoSeleccionado", "PENDIENTE"))
                .andExpect(model().attributeExists("conteosPorEstado"))
                .andExpect(model().attribute("ordenes", List.of(orden)));

        verify(servicio).listarVista("PENDIENTE");
    }

    @Test
    @DisplayName("CA-3.1: listar sin estado consulta todas las ordenes y entrega conteos por estado")
    void listarSinEstadoConsultaTodas() throws Exception {
        when(servicio.listarVista(null)).thenReturn(List.of());
        when(servicio.contarPorEstado()).thenReturn(Map.of(
                "TODAS", 0,
                "PENDIENTE", 0,
                "PARCIALMENTE_RECIBIDA", 0,
                "RECIBIDA", 0,
                "CANCELADA", 0));

        mvc.perform(get("/ordenes-compra")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ordenes-compra/listado"))
                .andExpect(model().attributeExists("conteosPorEstado"));

        verify(servicio).listarVista(null);
    }

    @Test
    @DisplayName("CA-3.1: listar con estado en blanco normaliza a null y consulta todas las ordenes")
    void listarConEstadoEnBlancoNormalizaANull() throws Exception {
        when(servicio.listarVista(null)).thenReturn(List.of());
        when(servicio.contarPorEstado()).thenReturn(Map.of(
                "TODAS", 0,
                "PENDIENTE", 0,
                "PARCIALMENTE_RECIBIDA", 0,
                "RECIBIDA", 0,
                "CANCELADA", 0));

        mvc.perform(get("/ordenes-compra")
                        .param("estado", "   ")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ordenes-compra/listado"));

        verify(servicio).listarVista(null);
    }
}
