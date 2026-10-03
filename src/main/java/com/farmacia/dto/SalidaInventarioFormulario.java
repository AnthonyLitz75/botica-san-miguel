package com.farmacia.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalidaInventarioFormulario(

        @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad debe ser mayor que cero") Integer cantidad,

        @NotBlank(message = "El motivo es obligatorio") @Size(max = 250, message = "El motivo admite hasta 250 caracteres") String motivo) {
}