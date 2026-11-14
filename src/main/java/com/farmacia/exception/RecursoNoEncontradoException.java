package com.farmacia.exception;

/**
 * Excepción lanzada cuando un recurso solicitado por identificador o clave no existe en el sistema.
 */
public class RecursoNoEncontradoException extends ReglaNegocioException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
