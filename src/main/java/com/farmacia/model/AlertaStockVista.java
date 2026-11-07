package com.farmacia.model;

public record AlertaStockVista(
        Integer idMedicamento,
        String codigoMedicamento,
        String nombreMedicamento,
        String unidadControl,
        Integer stockDisponible,
        Integer stockMinimo,
        Integer pendienteRecibir,
        Integer cantidadSugerida,
        String tipoAlerta) {
}
