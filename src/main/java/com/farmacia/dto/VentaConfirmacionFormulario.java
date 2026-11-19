package com.farmacia.dto;

import com.farmacia.model.MetodoPago;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de POST /ventas/confirmar.
 * Exige metodoPago (RF-02, CA-1.3); sin cliente, la venta es para "Público general".
 */
public record VentaConfirmacionFormulario(
        @Positive(message = "Selecciona un cliente válido") Integer idCliente,
        @NotNull(message = "El método de pago es obligatorio") MetodoPago metodoPago) {

    public VentaConfirmacionFormulario(Integer idCliente) {
        this(idCliente, null);
    }
}
