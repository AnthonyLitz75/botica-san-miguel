package com.farmacia.exception;

/**
 * Excepción de dominio lanzada cuando el stock vigente no alcanza para atender una venta o despacho
 * (por ejemplo, concurrencia de ventas o caducidad del lote durante la atención en mostrador).
 */
public class StockInsuficienteException extends ReglaNegocioException {

    public StockInsuficienteException(String mensaje) {
        super(mensaje);
    }

    public StockInsuficienteException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
