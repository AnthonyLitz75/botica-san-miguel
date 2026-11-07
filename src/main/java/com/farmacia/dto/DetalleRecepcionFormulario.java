package com.farmacia.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DetalleRecepcionFormulario(
        @NotNull(message = "Selecciona una línea de la orden") @Min(value = 1, message = "La línea no es válida") Integer idDetalleOrden,
        @NotBlank(message = "Ingresa el número de lote") @Size(max = 50, message = "El lote admite hasta 50 caracteres") String numeroLote,
        @NotNull(message = "Ingresa la fecha de vencimiento") LocalDate fechaVencimiento,
        @NotNull(message = "Ingresa la cantidad recibida") @Min(value = 1, message = "La cantidad debe ser mayor que cero") Integer cantidad) {}
