package com.farmacia.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteFormulario(
        @NotBlank(message = "El tipo de documento es obligatorio")
        @Pattern(regexp = "DNI|RUC", message = "El tipo de documento debe ser DNI o RUC")
        String tipoDocumento,
        @NotBlank(message = "El número de documento es obligatorio")
        @Pattern(regexp = "[0-9]{8}|[0-9]{11}", message = "El documento debe tener 8 dígitos para DNI o 11 para RUC")
        String numeroDocumento,
        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
        String nombreCompleto,
        @Size(max = 20, message = "El teléfono admite hasta 20 caracteres")
        String telefono,
        @Email(message = "Ingresa un correo válido")
        @Size(max = 150, message = "El correo admite hasta 150 caracteres")
        String correo) {
}
