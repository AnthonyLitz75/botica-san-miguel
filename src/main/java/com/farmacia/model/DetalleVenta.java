package com.farmacia.model;

import java.math.BigDecimal;

/** Línea de una venta confirmada: un tramo de lote con el precio congelado al confirmar. */
public record DetalleVenta(
        Integer idLote,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuentoImporte,
        BigDecimal importe) {
}
