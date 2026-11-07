package com.farmacia.model;

import java.math.BigDecimal;
import java.util.List;

public record PreparacionVentaVista(
        List<LineaPreparacionVentaVista> detalles,
        BigDecimal total,
        boolean puedeDeshacer) {
    public PreparacionVentaVista {
        detalles = List.copyOf(detalles);
    }
}
