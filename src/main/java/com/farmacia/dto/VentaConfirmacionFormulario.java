package com.farmacia.dto;

import jakarta.validation.constraints.Positive;

/** Cuerpo de POST /ventas/confirmar; sin cliente, la venta es para "Público general". */
public record VentaConfirmacionFormulario(
        @Positive(message = "Selecciona un cliente válido") Integer idCliente) {
}
