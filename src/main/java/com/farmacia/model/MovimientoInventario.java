package com.farmacia.model;

import java.time.LocalDateTime;

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