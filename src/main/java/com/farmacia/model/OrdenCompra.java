package com.farmacia.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrdenCompra(
                Integer idOrden,
                String numeroOrden,
                Integer idUsuario,
                Integer idProveedor,
                LocalDateTime fechaHora,
                String estado,
                BigDecimal totalEstimado,
                String observaciones) {
}