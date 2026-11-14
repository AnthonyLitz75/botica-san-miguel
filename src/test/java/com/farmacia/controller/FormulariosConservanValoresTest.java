package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.service.ClienteServicio;
import com.farmacia.service.LotePrioridadServicio;
import com.farmacia.service.LoteServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.MovimientoInventarioServicio;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 013 (CA-4.1, CA-4.2, RNF-07): tras un error de validación, cada formulario CRUD vuelve con los valores
 * válidos que el usuario escribió y el error asociado a su campo; no se guarda nada.
 */
@WebMvcTest({MedicamentoControlador.class, ProveedorControlador.class, ClienteWebControlador.class,
        OrdenCompraControlador.class, SalidaInventarioControlador.class})
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class FormulariosConservanValoresTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private MedicamentoServicio medicamentoServicio;
    @MockitoBean private ProveedorServicio proveedorServicio;
    @MockitoBean private ClienteServicio clienteServicio;
    @MockitoBean private OrdenCompraServicio ordenCompraServicio;
    @MockitoBean private LoteServicio loteServicio;
    @MockitoBean private MovimientoInventarioServicio movimientoServicio;
    @MockitoBean private LotePrioridadServicio prioridadServicio;
    @MockitoBean private com.farmacia.service.CategoriaServicio categoriaServicio;
    @MockitoBean private UsuarioDetallesServicio usuarios;

    @Test
    @DisplayName("Medicamento: precio inválido conserva código, nombre y presentación")
    void medicamento() throws Exception {
        mvc.perform(post("/medicamentos").with(user("admin").roles("ADMINISTRADOR")).with(csrf())
                        .param("codigo", "MED-777").param("nombre", "Paracetamol Forte")
                        .param("concentracion", "750 mg").param("presentacion", "Caja de 30 tabletas")
                        .param("unidadControl", "CAJA").param("precioVenta", "-1").param("stockMinimo", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"MED-777\"")))
                .andExpect(content().string(containsString("value=\"Paracetamol Forte\"")))
                .andExpect(content().string(containsString("value=\"Caja de 30 tabletas\"")))
                .andExpect(content().string(containsString("aria-describedby=\"error-precioVenta\"")));
        verifyNoInteractions(medicamentoServicio);
    }

    @Test
    @DisplayName("Proveedor: RUC inválido conserva razón social y correo")
    void proveedor() throws Exception {
        mvc.perform(post("/proveedores").with(user("compras").roles("COMPRAS")).with(csrf())
                        .param("ruc", "123").param("razonSocial", "Drogueria Andina SAC")
                        .param("telefono", "999111222").param("correo", "ventas@andina.example")
                        .param("direccion", "Av. Los Olivos 123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Drogueria Andina SAC\"")))
                .andExpect(content().string(containsString("value=\"ventas@andina.example\"")))
                .andExpect(content().string(containsString("aria-describedby=\"error-ruc\"")));
        verifyNoInteractions(proveedorServicio);
    }

    @Test
    @DisplayName("Cliente: documento vacío conserva nombre y correo")
    void cliente() throws Exception {
        mvc.perform(post("/clientes").with(user("vendedor").roles("VENDEDOR")).with(csrf())
                        .param("tipoDocumento", "DNI").param("numeroDocumento", "")
                        .param("nombreCompleto", "Ana Torres").param("telefono", "987654321")
                        .param("correo", "ana@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Ana Torres\"")))
                .andExpect(content().string(containsString("value=\"ana@example.com\"")))
                .andExpect(content().string(containsString("aria-describedby=\"error-documento\"")));
        verifyNoInteractions(clienteServicio);
    }

    @Test
    @DisplayName("Orden de compra: cantidad 0 conserva costo y observaciones, con el error junto al campo")
    void ordenCompra() throws Exception {
        mvc.perform(post("/ordenes-compra").with(user("compras").roles("COMPRAS")).with(csrf())
                        .param("idProveedor", "3").param("detalles[0].idMedicamento", "4")
                        .param("detalles[0].cantidadSolicitada", "0").param("detalles[0].costoUnitario", "2.50")
                        .param("observaciones", "Entrega el lunes temprano"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2.50\"")))
                .andExpect(content().string(containsString("Entrega el lunes temprano")))
                .andExpect(content().string(containsString("aria-describedby=\"error-cantidad\"")))
                .andExpect(content().string(containsString("id=\"error-cantidad\"")));
    }

    @Test
    @DisplayName("Salida directa: cantidad 0 conserva el motivo")
    void salidaDirecta() throws Exception {
        // El formulario solo se muestra si hay lotes disponibles.
        org.mockito.Mockito.when(loteServicio.listarDisponiblesConMedicamento()).thenReturn(java.util.List.of(
                new com.farmacia.model.LoteDisponibleVista(8, 1, "MED-001", "Paracetamol", "LT-8",
                        java.time.LocalDate.now().plusMonths(6), 20, "CAJA")));
        mvc.perform(post("/salidas/nueva").with(user("almacen").roles("ALMACENERO")).with(csrf())
                        .param("idLote", "8").param("cantidad", "0").param("motivo", "Merma por rotura de empaque"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Merma por rotura de empaque")));
        verifyNoInteractions(movimientoServicio);
    }
}
