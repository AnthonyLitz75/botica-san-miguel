package com.farmacia.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Cambio en la preparación. {@code descuento} es opcional (spec 004): si llega, reemplaza el descuento
 * de la línea; si no, la línea conserva el que tenía.
 */
public record PreparacionVentaCambioFormulario(
        @NotNull(message = "Selecciona un medicamento") @Min(value = 1, message = "El medicamento no es válido") Integer idMedicamento,
        @NotNull(message = "Ingresa una cantidad") @Min(value = 1, message = "La cantidad debe ser mayor que cero") Integer cantidad,
        @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El descuento admite como máximo dos decimales") BigDecimal descuento) {

    /** Cambio sin descuento (compatibilidad con el contrato anterior a la spec 004). */
    public PreparacionVentaCambioFormulario(Integer idMedicamento, Integer cantidad) {
        this(idMedicamento, cantidad, null);
    }
}
