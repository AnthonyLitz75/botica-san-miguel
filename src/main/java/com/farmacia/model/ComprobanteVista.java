package com.farmacia.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Comprobante interno de una venta: una línea por lote vendido. */
public record ComprobanteVista(
        Integer idVenta,
        String numeroComprobante,
        LocalDateTime fechaHora,
        Integer idVendedor,
        String vendedor,
        String cliente,
        String documentoCliente,
        List<Linea> lineas,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal total) {

    public static final String PUBLICO_GENERAL = "Público general";

    public ComprobanteVista {
        lineas = List.copyOf(lineas);
    }

    /** Nombre del cliente o "Público general" si la venta no tiene cliente. */
    public String clienteVisible() {
        return cliente == null ? PUBLICO_GENERAL : cliente;
    }

    public record Linea(
            String codigoMedicamento,
            String medicamento,
            String numeroLote,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal descuentoImporte,
            BigDecimal importe) {
    }
}
