package com.farmacia.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PreparacionVentaCambioFormulario(
        @NotNull(message = "Selecciona un medicamento") @Min(value = 1, message = "El medicamento no es válido") Integer idMedicamento,
        @NotNull(message = "Ingresa una cantidad") @Min(value = 1, message = "La cantidad debe ser mayor que cero") Integer cantidad) {
}
