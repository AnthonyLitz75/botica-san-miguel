package com.farmacia.model;

import com.farmacia.service.AccionAuditoria;

import java.time.LocalDateTime;

/** Fila de la bitácora de auditoría (spec 014 y 018). */
public record EventoAuditoria(
        Long idAuditoria,
        LocalDateTime fechaHora,
        Integer idUsuario,
        String nombreUsuario,
        String accion,
        String entidad,
        String idEntidad,
        String resultado,
        String detalle,
        String ipOrigen) {

    /**
     * Devuelve la etiqueta legible de la acción o el código original si es desconocida (CA-2.1, CA-2.5).
     */
    public String etiquetaAccion() {
        return etiquetaAccion(this.accion);
    }

    public static String etiquetaAccion(String codigoAccion) {
        if (codigoAccion == null || codigoAccion.isBlank()) {
            return "—";
        }
        try {
            return AccionAuditoria.valueOf(codigoAccion.strip()).etiqueta();
        } catch (IllegalArgumentException e) {
            return codigoAccion;
        }
    }

    /**
     * Devuelve la etiqueta legible del resultado (CA-2.2).
     */
    public String etiquetaResultado() {
        return etiquetaResultado(this.resultado);
    }

    public static String etiquetaResultado(String codigoResultado) {
        if (codigoResultado == null || codigoResultado.isBlank()) {
            return "—";
        }
        return switch (codigoResultado.strip().toUpperCase()) {
            case "EXITO" -> "ÉXITO";
            case "DENEGADO" -> "DENEGADO";
            case "FALLO" -> "FALLO";
            default -> codigoResultado;
        };
    }
}
