package com.farmacia.model;

public record Proveedor(
                Integer idProveedor,
                String ruc,
                String razonSocial,
                String telefono,
                String correo,
                String direccion,
                boolean activo) {
}