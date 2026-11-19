package com.farmacia.controller;

import com.farmacia.dto.VentaConfirmacionFormulario;
import com.farmacia.model.VentaConfirmadaVista;
import com.farmacia.service.PreparacionVentaServicio;
import com.farmacia.service.VentaServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de confirmación de venta. Coordina la preparación de la sesión y el servicio transaccional:
 * la preparación solo se vacía después de que la venta se confirmó (si hay rollback, se conserva).
 */
@RestController
@RequestMapping("/ventas")
public class VentaControlador {
    private final VentaServicio ventaServicio;
    private final PreparacionVentaServicio preparacionServicio;

    public VentaControlador(VentaServicio ventaServicio, PreparacionVentaServicio preparacionServicio) {
        this.ventaServicio = ventaServicio;
        this.preparacionServicio = preparacionServicio;
    }

    @PostMapping("/confirmar")
    public VentaConfirmadaVista confirmar(Authentication autenticacion,
            @Valid @RequestBody VentaConfirmacionFormulario formulario) {
        String usuario = autenticacion.getName();
        var preparacion = preparacionServicio.obtener(usuario);
        var resultado = ventaServicio.confirmar(usuario, formulario.idCliente(), formulario.metodoPago(), preparacion.detalles());
        preparacionServicio.limpiar(usuario);
        return resultado;
    }
}
