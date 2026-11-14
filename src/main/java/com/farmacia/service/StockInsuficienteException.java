package com.farmacia.service;

/**
 * El stock vigente no alcanza al confirmar una venta (otro usuario vendió o un lote venció).
 * Extiende IllegalArgumentException para reutilizar la respuesta 400 {mensaje} de la API.
 */
public class StockInsuficienteException extends IllegalArgumentException {
    public StockInsuficienteException(String mensaje) {
        super(mensaje);
    }
}
