package com.farmacia.model;

import java.util.List;

public record InventarioReporteVista(
        Integer idMedicamento,
        String codigo,
        String nombre,
        String unidadControl,
        int stockDisponible,
        int stockVencido,
        int stockMinimo,
        String estado,
        List<LoteInventarioReporteVista> lotes) {
}
