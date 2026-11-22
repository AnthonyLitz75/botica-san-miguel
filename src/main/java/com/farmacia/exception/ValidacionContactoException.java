package com.farmacia.exception;

import java.util.Map;

/**
 * Excepción lanzada cuando uno o más datos de contacto (correo, teléfono) ya están registrados.
 * Permite contener múltiples errores asociados a sus respectivos nombres de campo.
 */
public class ValidacionContactoException extends RuntimeException {

    private final Map<String, String> erroresPorCampo;

    public ValidacionContactoException(Map<String, String> erroresPorCampo) {
        super(construirMensaje(erroresPorCampo));
        this.erroresPorCampo = Map.copyOf(erroresPorCampo);
    }

    public ValidacionContactoException(String campo, String mensaje) {
        this(Map.of(campo, mensaje));
    }

    public Map<String, String> getErroresPorCampo() {
        return erroresPorCampo;
    }

    public boolean tieneError(String campo) {
        return erroresPorCampo.containsKey(campo);
    }

    public String getError(String campo) {
        return erroresPorCampo.get(campo);
    }

    private static String construirMensaje(Map<String, String> errores) {
        if (errores == null || errores.isEmpty()) {
            return "Error de validación de contacto";
        }
        return String.join(" | ", errores.values());
    }
}
