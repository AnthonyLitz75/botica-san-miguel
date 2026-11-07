package com.farmacia.controller;

import com.farmacia.dto.RecepcionOrdenFormulario;
import com.farmacia.model.LineaRecepcionVista;
import com.farmacia.model.OrdenCompra;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.OrdenCompraServicio;
import com.farmacia.service.ProveedorServicio;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OrdenCompraRecepcionControladorTest {
    @Test
    void preparaLaVistaConLasLineasPendientesDeRecepcion() {
        var ordenes = mock(OrdenCompraServicio.class);
        var controlador = controlador(ordenes);
        when(ordenes.buscarPorId(4)).thenReturn(Optional.of(orden("PENDIENTE")));
        var linea = new LineaRecepcionVista(8, 3, "MED-3", "Paracetamol", 10, 4, 6);
        when(ordenes.listarLineasRecepcion(4)).thenReturn(List.of(linea));

        var modelo = new ExtendedModelMap();
        assertEquals("ordenes-compra/recepcion", controlador.prepararRecepcion(4, modelo));
        assertEquals(List.of(linea), modelo.get("lineas"));
        assertEquals(1, ((RecepcionOrdenFormulario) modelo.get("formulario")).detalles().size());
        verify(ordenes).listarLineasRecepcion(4);
    }

    @Test
    void delegaLaRecepcionYRedirigeAlDetalle() {
        var ordenes = mock(OrdenCompraServicio.class);
        var controlador = controlador(ordenes);
        var auth = UsernamePasswordAuthenticationToken.authenticated("almacen", null, List.of());
        var flash = new RedirectAttributesModelMap();

        String destino = controlador.registrarRecepcion(4, new RecepcionOrdenFormulario(List.of(), null),
                new org.springframework.validation.BeanPropertyBindingResult(new RecepcionOrdenFormulario(List.of(), null), "formulario"),
                auth, flash);

        assertEquals("redirect:/ordenes-compra/4", destino);
        verify(ordenes).registrarRecepcion(eq(4), eq("almacen"), any());
        assertEquals("Recepción registrada correctamente", flash.getFlashAttributes().get("mensaje"));
    }

    @Test
    void delegaCancelacionConUsuarioAutenticado() {
        var ordenes = mock(OrdenCompraServicio.class);
        var controlador = controlador(ordenes);
        var auth = UsernamePasswordAuthenticationToken.authenticated("compras", null, List.of());
        var flash = new RedirectAttributesModelMap();

        assertEquals("redirect:/ordenes-compra/4", controlador.cancelar(4, auth, flash));
        verify(ordenes).cancelarOrden(4, "compras");
    }

    private OrdenCompraControlador controlador(OrdenCompraServicio ordenes) {
        return new OrdenCompraControlador(ordenes, mock(ProveedorServicio.class), mock(MedicamentoServicio.class));
    }

    private OrdenCompra orden(String estado) {
        return new OrdenCompra(4, "OC-4", 1, 2, LocalDateTime.now(), estado, BigDecimal.TEN, null);
    }
}
