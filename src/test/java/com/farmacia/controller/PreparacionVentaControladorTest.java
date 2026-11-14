package com.farmacia.controller;

import com.farmacia.dto.PreparacionVentaCambioFormulario;
import com.farmacia.model.PreparacionVentaVista;
import com.farmacia.service.PreparacionVentaServicio;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreparacionVentaControladorTest {
    @Test
    void delegaLasOperacionesConElUsuarioAutenticado() {
        var servicio = mock(PreparacionVentaServicio.class);
        var controlador = new PreparacionVentaControlador(servicio);
        var auth = UsernamePasswordAuthenticationToken.authenticated("vendedor", null, List.of());
        var vista = new PreparacionVentaVista(List.of(), BigDecimal.ZERO.setScale(2), false);
        when(servicio.obtener("vendedor")).thenReturn(vista);
        when(servicio.agregar(eq("vendedor"), any())).thenReturn(vista);
        when(servicio.retirar("vendedor", 8)).thenReturn(vista);
        when(servicio.deshacer("vendedor")).thenReturn(vista);

        assertSame(vista, controlador.obtener(auth));
        assertSame(vista, controlador.agregar(auth, new PreparacionVentaCambioFormulario(4, 1)));
        assertSame(vista, controlador.retirar(auth, 8));
        assertSame(vista, controlador.deshacer(auth));
        verify(servicio).agregar(eq("vendedor"), any());
        verify(servicio).retirar("vendedor", 8);
        verify(servicio).deshacer("vendedor");
    }

    @Test
    void delegaElDescuentoDeUnaLinea() {
        var servicio = mock(PreparacionVentaServicio.class);
        var auth = UsernamePasswordAuthenticationToken.authenticated("vendedor", null, List.of());
        var vista = new PreparacionVentaVista(List.of(), BigDecimal.ZERO.setScale(2), false);
        when(servicio.aplicarDescuento("vendedor", 8, new BigDecimal("5.00"))).thenReturn(vista);

        assertSame(vista, new PreparacionVentaControlador(servicio).aplicarDescuento(auth, 8,
                new com.farmacia.dto.DescuentoLineaFormulario(new BigDecimal("5.00"))));
    }

    @Test
    void traduceErroresDeReglaDeNegocioARespuesta400() {
        var respuesta = new PreparacionVentaControlador(mock(PreparacionVentaServicio.class))
                .errorSolicitud(new IllegalArgumentException("Stock insuficiente"));
        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        assertEquals("Stock insuficiente", respuesta.getBody().mensaje());
    }
}
