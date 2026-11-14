package com.farmacia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Formulario para creación y edición de categorías taxonómicas (spec 009).
 */
public record CategoriaFormulario(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,
        Integer idPadre) {

    public static CategoriaFormulario vacio() {
        return new CategoriaFormulario("", null);
    }
}
