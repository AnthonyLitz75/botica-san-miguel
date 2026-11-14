package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.Medicamento;
import com.farmacia.model.ComprobanteVista;
import com.farmacia.service.ClienteServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.VentaServicio;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.Optional;
import com.farmacia.service.UsuarioDetallesServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(VentaWebControlador.class)
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class VentaWebControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MedicamentoServicio medicamentoServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    @MockitoBean
    private ClienteServicio clienteServicio;

    @MockitoBean
    private VentaServicio ventaServicio;

    @Test
    @DisplayName("Escenario 1: Solicitud anónima a /ventas/nueva redirige a /login")
    void nuevaVenta_sinAutenticacion_redirigeALogin() throws Exception {
        mvc.perform(get("/ventas/nueva"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @DisplayName("Escenario 2: Usuario con rol COMPRAS recibe 403 Forbidden en /ventas/nueva")
    void nuevaVenta_conRolCompras_deniegaAcceso() throws Exception {
        mvc.perform(get("/ventas/nueva").with(user("compras1").roles("COMPRAS")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Escenario 3: Usuario con rol ALMACENERO recibe 403 Forbidden en /ventas/nueva")
    void nuevaVenta_conRolAlmacenero_deniegaAcceso() throws Exception {
        mvc.perform(get("/ventas/nueva").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Escenario 4: Usuario VENDEDOR accede a /ventas/nueva y carga vista con medicamentos activos")
    void nuevaVenta_conRolVendedor_devuelveVistaYModelo() throws Exception {
        List<Medicamento> medicamentosActivos = List.of(
                new Medicamento(1, "MED-001", "Paracetamol", "500mg", "Tabletas", "Caja",
                        new BigDecimal("5.50"), 10, true),
                new Medicamento(2, "MED-002", "Amoxicilina", "500mg", "Cápsulas", "Caja",
                        new BigDecimal("12.00"), 5, true)
        );
        when(medicamentoServicio.listarActivos()).thenReturn(medicamentosActivos);

        mvc.perform(get("/ventas/nueva").with(user("vendedor1").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ventas/preparacion"))
                .andExpect(model().attributeExists("medicamentos"))
                .andExpect(model().attribute("medicamentos", hasSize(2)))
                .andExpect(model().attribute("puedePrepararVenta", true))
                .andExpect(content().string(containsString("Preparación de venta")))
                .andExpect(content().string(containsString("_csrf")))
                .andExpect(content().string(containsString("select-medicamento")))
                .andExpect(content().string(containsString("input-cantidad")))
                .andExpect(content().string(containsString("Paracetamol")))
                .andExpect(content().string(containsString("Amoxicilina")))
                .andExpect(content().string(containsString("Agregar")))
                .andExpect(content().string(containsString("zona-alertas")))
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("estado-vacio")))
                .andExpect(content().string(containsString("tabla-lineas")))
                .andExpect(content().string(containsString("cuerpo-lineas")))
                .andExpect(content().string(containsString("total-venta")))
                .andExpect(content().string(containsString("boton-deshacer")))
                .andExpect(content().string(containsString("preparacion-venta.js")))
                .andExpect(content().string(containsString("select-cliente")))
                .andExpect(content().string(containsString("Público general")))
                .andExpect(content().string(containsString("boton-confirmar")));

        verify(medicamentoServicio).listarActivos();
    }

    @Test
    @DisplayName("Escenario 5: Usuario ADMINISTRADOR accede a /ventas/nueva y muestra aviso si no hay medicamentos")
    void nuevaVenta_conRolAdministrador_devuelveVistaYModelo() throws Exception {
        when(medicamentoServicio.listarActivos()).thenReturn(List.of());

        mvc.perform(get("/ventas/nueva").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ventas/preparacion"))
                .andExpect(model().attributeExists("medicamentos"))
                .andExpect(model().attribute("puedePrepararVenta", true))
                .andExpect(content().string(containsString("No hay medicamentos activos disponibles para la venta.")));

        verify(medicamentoServicio).listarActivos();
    }

    // ------------------------------------------------------------------ spec 003: comprobante

    private ComprobanteVista comprobante() {
        return new ComprobanteVista(12, "B001-00000012", LocalDateTime.of(2026, 11, 10, 9, 30), 7,
                "Vendedor Demo", null, null,
                List.of(new ComprobanteVista.Linea("MED-001", "Paracetamol", "LT-A", 4,
                        new BigDecimal("8.50"), new BigDecimal("0.00"), new BigDecimal("34.00"))),
                new BigDecimal("34.00"), new BigDecimal("0.00"), new BigDecimal("34.00"));
    }

    @Test
    @DisplayName("CA-3.1: GET /ventas/{id} muestra el comprobante con número, líneas por lote y leyenda interna")
    void comprobante_muestraLaVenta() throws Exception {
        when(ventaServicio.obtenerComprobante(12, "vendedor1")).thenReturn(Optional.of(comprobante()));

        mvc.perform(get("/ventas/12").with(user("vendedor1").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("ventas/comprobante"))
                .andExpect(content().string(containsString("B001-00000012")))
                .andExpect(content().string(containsString("Público general")))
                .andExpect(content().string(containsString("LT-A")))
                .andExpect(content().string(containsString("Comprobante interno")))
                .andExpect(content().string(containsString("S/ 34.00")))
                .andExpect(content().string(containsString("window.print()")));
    }

    @Test
    @DisplayName("CA-3.4: venta de otro vendedor → 403; inexistente → 404")
    void comprobante_permisosY404() throws Exception {
        when(ventaServicio.obtenerComprobante(12, "otro"))
                .thenThrow(new AccessDeniedException("No puedes ver la venta de otro vendedor"));
        when(ventaServicio.obtenerComprobante(99, "admin")).thenReturn(Optional.empty());

        mvc.perform(get("/ventas/12").with(user("otro").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/ventas/99").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CA-3.4: Almacenero y Compras no acceden al comprobante")
    void comprobante_rolesSinPermiso() throws Exception {
        mvc.perform(get("/ventas/12").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/ventas/12").with(user("compras1").roles("COMPRAS")))
                .andExpect(status().isForbidden());
    }
}
