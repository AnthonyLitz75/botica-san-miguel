package com.farmacia.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DetalleOrdenCompraFormulario(
        @NotNull(message = "Selecciona un medicamento") @Min(value = 1, message = "El medicamento no es valido") Integer idMedicamento,

        @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad debe ser mayor que cero") Integer cantidadSolicitada,

        @NotNull(message = "El costo unitario es obligatorio") @DecimalMin(value = "0.00", message = "El costo unitario no puede ser negativo") @Digits(integer = 10, fraction = 2, message = "El costo admite hasta 10 enteros y 2 decimales") BigDecimal costoUnitario) {
}