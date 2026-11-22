package com.farmacia.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fila resumida del historial de ventas (spec 005, CA-1.2, spec 016). */
public record VentaResumenVista(
        Integer idVenta,
        String numeroComprobante,
        LocalDateTime fechaHora,
        Integer idVendedor,
        String vendedor,
        String cliente,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal total,
        MetodoPago metodoPago) {

    public static final String PUBLICO_GENERAL = "Público general";

    public VentaResumenVista(
            Integer idVenta,
            String numeroComprobante,
            LocalDateTime fechaHora,
            Integer idVendedor,
            String vendedor,
            String cliente,
            BigDecimal subtotal,
            BigDecimal descuentoTotal,
            BigDecimal total) {
        this(idVenta, numeroComprobante, fechaHora, idVendedor, vendedor, cliente, subtotal, descuentoTotal, total, null);
    }

    public String clienteVisible() {
        return cliente == null || cliente.isBlank() ? PUBLICO_GENERAL : cliente;
    }

    public String metodoPagoVisible() {
        return metodoPago == null ? "—" : metodoPago.etiqueta();
    }

    public boolean tieneDescuento() {
        return descuentoTotal != null && descuentoTotal.compareTo(BigDecimal.ZERO) > 0;
    }
}
