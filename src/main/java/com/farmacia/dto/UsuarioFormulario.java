package com.farmacia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioFormulario(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres")
        String nombre,

        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{4,50}$", message = "El nombre de usuario debe tener entre 4 y 50 caracteres alfanuméricos, puntos, guiones o guiones bajos")
        String nombreUsuario,

        @NotBlank(message = "El rol es obligatorio")
        String rol,

        String clave) {

    public UsuarioFormulario {
        nombre = nombre != null ? nombre.strip() : null;
        nombreUsuario = nombreUsuario != null ? nombreUsuario.strip() : null;
        rol = rol != null ? rol.strip() : null;
        clave = clave != null ? clave.strip() : null;
    }
}
