package com.farmacia.model;

public record DetalleMovimientoInventario(
                Integer idLote,
                String codigoMedicamento,
                String nombreMedicamento,
                String numeroLote,
                String unidadControl,
                Integer cantidad) {
}