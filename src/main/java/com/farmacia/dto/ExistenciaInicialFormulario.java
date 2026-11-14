package com.farmacia.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record ExistenciaInicialFormulario(
        @NotNull(message = "Selecciona un medicamento")
        Integer idMedicamento,

        @NotBlank(message = "El número de lote es obligatorio")
        @Size(max = 50, message = "El número de lote admite hasta 50 caracteres")
        String numeroLote,

        @NotNull(message = "La fecha de vencimiento es obligatoria")
        @Future(message = "La fecha de vencimiento debe ser posterior a hoy")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fechaVencimiento,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor que cero")
        Integer cantidad,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 250, message = "El motivo admite hasta 250 caracteres")
        String motivo,

        Boolean confirmarAcumulacion) {

    public ExistenciaInicialFormulario {
        if (motivo == null) {
            motivo = "Existencias iniciales";
        }
        if (confirmarAcumulacion == null) {
            confirmarAcumulacion = false;
        }
    }

    public static ExistenciaInicialFormulario vacio() {
        return new ExistenciaInicialFormulario(null, null, null, null, "Existencias iniciales", false);
    }
}
