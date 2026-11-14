package com.farmacia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioClaveFormulario(
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 10, max = 100, message = "La contraseña debe tener entre 10 y 100 caracteres")
        String nuevaClave) {

    public UsuarioClaveFormulario {
        nuevaClave = nuevaClave != null ? nuevaClave.strip() : null;
    }
}
