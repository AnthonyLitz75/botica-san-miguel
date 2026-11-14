package com.farmacia.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MedicamentoFormulario(
        @NotBlank(message = "El codigo es obligatorio")
        @Size(max = 50, message = "Maximo 50 caracteres")
        String codigo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "Maximo 150 caracteres")
        String nombre,

        @Size(max = 100, message = "Maximo 100 caracteres")
        String concentracion,

        @NotBlank(message = "La presentacion es obligatoria")
        @Size(max = 100, message = "Maximo 100 caracteres")
        String presentacion,

        @Pattern(regexp = "^(CAJA|FRASCO)$", message = "La unidad debe ser CAJA o FRASCO")
        @NotBlank(message = "La unidad de control es obligatoria")
        @Size(max = 30, message = "Maximo 30 caracteres")
        String unidadControl,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "Maximo 10 enteros y 2 decimales")
        BigDecimal precioVenta,

        @NotNull(message = "El stock minimo es obligatorio")
        @Min(value = 0, message = "El stock minimo no puede ser negativo")
        Integer stockMinimo,

        Integer idCategoria) {

    public MedicamentoFormulario(
            String codigo,
            String nombre,
            String concentracion,
            String presentacion,
            String unidadControl,
            BigDecimal precioVenta,
            Integer stockMinimo) {
        this(codigo, nombre, concentracion, presentacion, unidadControl, precioVenta, stockMinimo, null);
    }
}
