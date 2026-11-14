package com.farmacia.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fila resumida del historial de ventas (spec 005, CA-1.2). */
public record VentaResumenVista(
        Integer idVenta,
        String numeroComprobante,
        LocalDateTime fechaHora,
        Integer idVendedor,
        String vendedor,
        String cliente,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal total) {

    public static final String PUBLICO_GENERAL = "Público general";

    public String clienteVisible() {
        return cliente == null || cliente.isBlank() ? PUBLICO_GENERAL : cliente;
    }
}
