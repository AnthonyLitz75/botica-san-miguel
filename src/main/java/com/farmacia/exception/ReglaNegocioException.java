package com.farmacia.exception;

/**
 * Excepción base para infracciones de reglas de negocio en el dominio de farmacia.
 */
public abstract class ReglaNegocioException extends RuntimeException {

    protected ReglaNegocioException(String mensaje) {
        super(mensaje);
    }

    protected ReglaNegocioException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
