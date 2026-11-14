package com.farmacia.model;

import java.time.LocalDateTime;

/**
 * Cabecera de un movimiento de inventario. {@code idProveedor} no es una columna de
 * movimiento_inventario (3FN): se deriva de la orden de compra en las recepciones y es nulo en el resto.
 */
public record MovimientoInventario(
        Integer idMovimiento,
        Integer idUsuario,
        Integer idProveedor,
        Integer idVenta,
        Integer idOrden,
        String tipo,
        LocalDateTime fechaHora,
        String motivo) {
}