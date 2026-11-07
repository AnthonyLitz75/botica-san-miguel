package com.farmacia.model;

import java.math.BigDecimal;
import java.util.List;

public record LineaPreparacionVentaVista(
        Integer idMedicamento,
        String codigo,
        String nombre,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal importe,
        List<AsignacionLoteFefo> lotesFefo) {
    public LineaPreparacionVentaVista {
        lotesFefo = List.copyOf(lotesFefo);
    }
}
