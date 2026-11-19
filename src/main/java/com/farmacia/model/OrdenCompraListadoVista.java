package com.farmacia.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista de orden de compra para listados y cabecera con la razón social del proveedor (spec 018, CA-1.1).
 */
public record OrdenCompraListadoVista(
        Integer idOrden,
        String numeroOrden,
        Integer idUsuario,
        Integer idProveedor,
        String razonSocialProveedor,
        LocalDateTime fechaHora,
        String estado,
        BigDecimal totalEstimado,
        String observaciones) {
}
