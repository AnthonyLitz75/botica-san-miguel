package com.farmacia.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.DetalleOrdenCompraVista;
import com.farmacia.model.LineaRecepcionVista;
import com.farmacia.model.Medicamento;
import com.farmacia.model.OrdenCompra;
import com.farmacia.model.OrdenCompraListadoVista;
import com.farmacia.model.Proveedor;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.OrdenCompraServicio;
import com.farmacia.service.ProveedorServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas TDD para AJUSTE 023 (paso 4A): detalle y confirmación de cancelación con modal compartido.
 */
@WebMvcTest(OrdenCompraControlador.class)
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class OrdenCompraAjuste023VistaTest {

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
    @DisplayName("Detalle: subtítulo nuevo, sin recuadro interno, y hooks de modal-confirmar para cancelar orden")
    void detalleOrdenVisualYModalConfirmar() throws Exception {
        var orden = new OrdenCompra(10, "OC-2026-0010", 1, 5, LocalDateTime.now(), "PENDIENTE", new BigDecimal("150.00"), "Nota de prueba");
        var proveedor = new Proveedor(5, "20555666777", "Droguería Central S.A.C.", "987654321", "contacto@central.pe", "Av. Central 123", true);
        var detalle = new DetalleOrdenCompraVista(1, 2, "MED-002", "Ibuprofeno 400mg", "CAJA", 10, 0, 10, new BigDecimal("15.00"), new BigDecimal("150.00"));

        when(servicio.buscarPorId(10)).thenReturn(Optional.of(orden));
        when(proveedorServicio.buscarPorId(5)).thenReturn(Optional.of(proveedor));
        when(servicio.listarDetallesVista(10)).thenReturn(List.of(detalle));

        mvc.perform(get("/ordenes-compra/10")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                // Subtítulo nuevo
                .andExpect(content().string(containsString("Revisa el proveedor, el estado y lo que pediste.")))
                // Alineación con m-0 en los dd
                .andExpect(content().string(containsString("class=\"m-0\"")))
                // Modal de confirmación compartido: data-confirmar-titulo y mensaje
                .andExpect(content().string(containsString("data-confirmar-titulo=\"Cancelar la orden OC-2026-0010\"")))
                .andExpect(content().string(containsString("La orden pasará a Cancelada y ya no se podrá recibir.")))
                .andExpect(content().string(containsString("data-confirmar-boton=\"Sí, cancelar orden\"")))
                .andExpect(content().string(containsString("data-confirmar-cancelar=\"No, volver\"")))
                // No debe contener el dialog nativo modal-cancelar
                .andExpect(content().string(not(containsString("<dialog id=\"modal-cancelar\""))));
    }

    @Test
    @DisplayName("Nueva orden: títulos normales, nota con texto acordado, tarjeta de resumen y sin Volver a órdenes")
    void nuevaOrdenDisenoDosColumnasYResumen() throws Exception {
        when(proveedorServicio.listarActivos()).thenReturn(List.of(
                new Proveedor(1, "20000000001", "Distribuidora San Roque", "999111222", "ventas@sanroque.pe", "Calle Lima 100", true)));
        when(medicamentoServicio.listarActivos()).thenReturn(List.of(
                new Medicamento(1, "MED-001", "Paracetamol", "500 mg", "Caja", "CAJA", new BigDecimal("8.50"), 10, true)));

        mvc.perform(get("/ordenes-compra/nuevo")
                        .with(user("compras").roles("COMPRAS")))
                .andExpect(status().isOk())
                // Títulos normales (no mayúsculas)
                .andExpect(content().string(containsString("Datos del proveedor")))
                .andExpect(content().string(containsString("Medicamento solicitado")))
                // Nota bajo proveedor
                .andExpect(content().string(containsString("La orden queda como Pendiente y no cambia el stock hasta que registres la recepción.")))
                // Resumen de la orden a la derecha
                .andExpect(content().string(containsString("Resumen de la orden")))
                .andExpect(content().string(containsString("aria-live=\"polite\"")))
                // Sin Volver a órdenes en la cabecera
                .andExpect(content().string(not(containsString("Volver a órdenes"))));
    }

    @Test
    @DisplayName("Recepción: chip Compras, pista neutra, sin Volver al detalle arriba, checkbox sin texto visible y sin role=alert")
    void recepcionOrdenVisualYComportamiento() throws Exception {
        var orden = new OrdenCompra(15, "OC-2026-0015", 1, 3, LocalDateTime.now(), "PENDIENTE", new BigDecimal("300.00"), "Nota");
        var linea = new LineaRecepcionVista(101, 201, "MED-010", "Amoxicilina 500mg", 20, 0, 20);

        when(servicio.buscarPorId(15)).thenReturn(Optional.of(orden));
        when(servicio.listarLineasRecepcion(15)).thenReturn(List.of(linea));
        when(servicio.fechaMinimaVencimientoRecepcion()).thenReturn(LocalDate.now().plusDays(90));

        mvc.perform(get("/ordenes-compra/15/recepcion")
                        .with(user("almacen").roles("ALMACENERO")))
                .andExpect(status().isOk())
                // Chip de cabecera debe decir Compras
                .andExpect(content().string(containsString("Compras")))
                // Sin Volver al detalle en la cabecera
                .andExpect(content().string(not(containsString("Volver al detalle"))))
                // Pista informativa neutra arriba de la tabla
                .andExpect(content().string(containsString("Marca los productos que llegaron para poder guardar la recepción.")))
                // Frase en texto normal
                .andExpect(content().string(containsString("Selecciona solo las líneas que llegaron en esta entrega física.")))
                // Casilla sin texto visible 'Recibir' y con aria-label
                .andExpect(content().string(containsString("aria-label=\"Recibir Amoxicilina 500mg\"")))
                .andExpect(content().string(not(containsString("<span>Recibir</span>"))))
                // Sin role='alert' en la carga inicial
                .andExpect(content().string(not(containsString("role=\"alert\""))));
    }

    @Test
    @DisplayName("Listado: renderiza control de paginación y conserva filtro de estado")
    void listadoOrdenesConPaginacion() throws Exception {
        var orden = new OrdenCompraListadoVista(1, "OC-2026-0001", 1, 10, "Droguería Farmaperú",
                LocalDateTime.now(), "PENDIENTE", new BigDecimal("100.00"), null);

        when(servicio.listarVista("PENDIENTE", 1)).thenReturn(
                new OrdenCompraServicio.OrdenesPaginadas(List.of(orden), 35, 1, 2));
        when(servicio.contarPorEstado()).thenReturn(Map.of(
                "TODAS", 50,
                "PENDIENTE", 35,
                "PARCIALMENTE_RECIBIDA", 5,
                "RECIBIDA", 8,
                "CANCELADA", 2));

        mvc.perform(get("/ordenes-compra")
                        .param("estado", "PENDIENTE")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                // Total badge
                .andExpect(content().string(containsString("35")))
                // Chips con contadores totales
                .andExpect(content().string(containsString("Todas · 50")))
                .andExpect(content().string(containsString("Pendientes · 35")))
                // Control de paginación
                .andExpect(content().string(containsString("paginacion-contenedor")))
                .andExpect(content().string(containsString("Mostrando 1–25 de 35")))
                // Enlace a página 2 con filtro de estado preservado
                .andExpect(content().string(containsString("estado=PENDIENTE")));
    }
}
