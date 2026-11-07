package com.farmacia.model;

public record Cliente(
        Integer idCliente,
        String tipoDocumento,
        String numeroDocumento,
        String nombreCompleto,
        String telefono,
        String correo) {
}
