package com.farmacia.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Estado de la preparación: total = subtotal − descuentoTotal (spec 004). */
public record PreparacionVentaVista(
        List<LineaPreparacionVentaVista> detalles,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal total,
        boolean puedeDeshacer) {
    public PreparacionVentaVista {
        detalles = List.copyOf(detalles);
    }

    /** Vista sin descuentos (compatibilidad con el contrato anterior a la spec 004). */
    public PreparacionVentaVista(List<LineaPreparacionVentaVista> detalles, BigDecimal total, boolean puedeDeshacer) {
        this(detalles, total, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), total, puedeDeshacer);
    }
}
