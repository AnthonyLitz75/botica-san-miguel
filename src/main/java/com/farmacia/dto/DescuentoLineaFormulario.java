package com.farmacia.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Cuerpo de PUT /ventas/preparacion/{idMedicamento}/descuento (spec 004). */
public record DescuentoLineaFormulario(
        @NotNull(message = "Ingresa el descuento")
        @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El descuento admite como máximo dos decimales") BigDecimal descuento) {
}
