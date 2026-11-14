package com.farmacia.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VentasDiaVista(
        LocalDate fecha,
        long cantidadVentas,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal total) {
}
