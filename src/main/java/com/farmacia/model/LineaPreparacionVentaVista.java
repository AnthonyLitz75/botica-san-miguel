package com.farmacia.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Línea de la venta en preparación. {@code importe} = cantidad × precio − descuento (spec 004).
 */
public record LineaPreparacionVentaVista(
        Integer idMedicamento,
        String codigo,
        String nombre,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuento,
        BigDecimal importe,
        List<AsignacionLoteFefo> lotesFefo) {

    public LineaPreparacionVentaVista {
        lotesFefo = List.copyOf(lotesFefo);
        descuento = descuento == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : descuento;
    }

    /** Línea sin descuento (compatibilidad con el contrato anterior a la spec 004). */
    public LineaPreparacionVentaVista(Integer idMedicamento, String codigo, String nombre, Integer cantidad,
            BigDecimal precioUnitario, BigDecimal importe, List<AsignacionLoteFefo> lotesFefo) {
        this(idMedicamento, codigo, nombre, cantidad, precioUnitario, null, importe, lotesFefo);
    }

    /** cantidad × precio unitario, antes del descuento. */
    public BigDecimal importeBruto() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
    }
}
