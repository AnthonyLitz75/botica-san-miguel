package com.farmacia.service;

/**
 * Adaptador de compatibilidad hacia atrás para {@link com.farmacia.exception.StockInsuficienteException}.
 * Permite que los servicios y pruebas existentes conserven su importación sin regresiones.
 */
public class StockInsuficienteException extends com.farmacia.exception.StockInsuficienteException {

    public StockInsuficienteException(String mensaje) {
        super(mensaje);
    }

    public StockInsuficienteException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
