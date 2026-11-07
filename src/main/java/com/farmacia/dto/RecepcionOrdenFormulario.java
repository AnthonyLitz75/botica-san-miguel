package com.farmacia.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RecepcionOrdenFormulario(
        @NotEmpty(message = "Agrega al menos un producto recibido") @Valid List<@Valid DetalleRecepcionFormulario> detalles,
        @Size(max = 250, message = "El motivo admite hasta 250 caracteres") String motivo) {}
