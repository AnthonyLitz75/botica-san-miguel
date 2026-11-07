package com.farmacia.model;

public record ResumenStockInventario(
        Integer idMedicamento,
        String codigoMedicamento,
        String nombreMedicamento,
        String unidadControl,
        Integer stockMinimo,
        Integer stockDisponible,
        Integer pendienteRecibir) {
}
