package com.farmacia.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrdenCompraFormulario(
        @NotNull(message = "Selecciona un proveedor") @Min(value = 1, message = "El proveedor no es valido") Integer idProveedor,

        @Size(max = 500, message = "Las observaciones admiten hasta 500 caracteres") String observaciones,

        @NotEmpty(message = "Agrega al menos un medicamento") @Valid List<@NotNull(message = "El detalle no puede estar vacio") DetalleOrdenCompraFormulario> detalles) {
}