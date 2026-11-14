package com.farmacia.model;

import java.time.LocalDateTime;

/**
 * Cabecera y vista resumida de un movimiento de inventario (spec 005, US-2, US-3).
 * {@code idProveedor} no es una columna de movimiento_inventario (3FN):
 * se deriva de la orden de compra en las recepciones y es nulo en el resto.
 */
public record MovimientoInventario(
        Integer idMovimiento,
        Integer idUsuario,
        Integer idProveedor,
        Integer idVenta,
        Integer idOrden,
        String tipo,
        LocalDateTime fechaHora,
        String motivo,
        String origen,
        String responsable,
        Integer cantidadMedicamento) {

    /** Constructor de 8 parámetros para compatibilidad con código existente. */
    public MovimientoInventario(
            Integer idMovimiento,
            Integer idUsuario,
            Integer idProveedor,
            Integer idVenta,
            Integer idOrden,
            String tipo,
            LocalDateTime fechaHora,
            String motivo) {
        this(idMovimiento, idUsuario, idProveedor, idVenta, idOrden, tipo, fechaHora, motivo,
                derivarOrigen(tipo, idOrden, idVenta), null, null);
    }

    public static String derivarOrigen(String tipo, Integer idOrden, Integer idVenta) {
        if ("ENTRADA".equalsIgnoreCase(tipo)) {
            return idOrden != null ? "Recepción de compra" : "Existencia inicial";
        }
        if (idVenta != null) {
            return "Venta";
        }
        return "Otra salida";
    }
}
