package com.farmacia.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Formulario para modificar la cantidad directa de una línea en preparación. */
public record CantidadLineaFormulario(
        @NotNull(message = "La cantidad es obligatoria")
        @PositiveOrZero(message = "La cantidad debe ser mayor o igual a 0")
        Integer cantidad) {
}
