package com.farmacia.controller;

import com.farmacia.dto.DescuentoLineaFormulario;
import com.farmacia.dto.PreparacionVentaCambioFormulario;
import com.farmacia.model.PreparacionVentaVista;
import com.farmacia.service.PreparacionVentaServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ventas/preparacion")
public class PreparacionVentaControlador {
    private final PreparacionVentaServicio servicio;

    public PreparacionVentaControlador(PreparacionVentaServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public PreparacionVentaVista obtener(Authentication autenticacion) {
        return servicio.obtener(autenticacion.getName());
    }

    @PostMapping
    public PreparacionVentaVista agregar(Authentication autenticacion,
            @Valid @RequestBody PreparacionVentaCambioFormulario formulario) {
        return servicio.agregar(autenticacion.getName(), formulario);
    }

    @DeleteMapping("/{idMedicamento:[0-9]+}")
    public PreparacionVentaVista retirar(Authentication autenticacion,
            @PathVariable("idMedicamento") Integer idMedicamento) {
        return servicio.retirar(autenticacion.getName(), idMedicamento);
    }

    /** Fija el descuento (importe en soles) de una línea ya preparada (spec 004). */
    @PutMapping("/{idMedicamento:[0-9]+}/descuento")
    public PreparacionVentaVista aplicarDescuento(Authentication autenticacion,
            @PathVariable("idMedicamento") Integer idMedicamento,
            @Valid @RequestBody DescuentoLineaFormulario formulario) {
        return servicio.aplicarDescuento(autenticacion.getName(), idMedicamento, formulario.descuento());
    }

        @PostMapping("/deshacer")
    public PreparacionVentaVista deshacer(Authentication autenticacion) {
        return servicio.deshacer(autenticacion.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorVista> errorSolicitud(IllegalArgumentException excepcion) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorVista(excepcion.getMessage()));
    }

    public record ErrorVista(String mensaje) {
    }
}
