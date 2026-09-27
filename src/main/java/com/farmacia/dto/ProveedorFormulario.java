package com.farmacia.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProveedorFormulario(

        @NotBlank(message = "El RUC es obligatorio") @Pattern(regexp = "^([0-9]{11})?$", message = "El RUC debe contener 11 digitos") String ruc,

        @NotBlank(message = "La razon social es obligatoria") @Size(max = 150, message = "La razon social admite hasta 150 caracteres") String razonSocial,

        @Size(max = 20, message = "El telefono admite hasta 20 caracteres") String telefono,

        @Email(message = "Ingresa un correo valido") @Size(max = 150, message = "El correo admite hasta 150 caracteres") String correo,

        @Size(max = 200, message = "La direccion admite hasta 200 caracteres") String direccion) {
}