package com.farmacia.model;

import java.time.LocalDateTime;

/** Fila de la bitácora de auditoría (spec 014). */
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
}
