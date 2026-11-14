package com.farmacia.model;

/** Respuesta de POST /ventas/confirmar. */
public record VentaConfirmadaVista(Integer idVenta, String numeroComprobante) {
}
