package com.farmacia.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProveedorFormulario(

                @NotBlank(message = "El RUC es obligatorio") @Pattern(regexp = "^([0-9]{11})?$", message = "El RUC debe contener 11 dígitos") String ruc,

                @NotBlank(message = "La razón social es obligatoria") @Size(max = 150, message = "La razón social admite hasta 150 caracteres") String razonSocial,

                @Size(max = 20, message = "El teléfono admite hasta 20 caracteres") String telefono,

                @Email(message = "Ingresa un correo válido") @Size(max = 150, message = "El correo admite hasta 150 caracteres") String correo,

                @Size(max = 200, message = "La dirección admite hasta 200 caracteres") String direccion) {
}