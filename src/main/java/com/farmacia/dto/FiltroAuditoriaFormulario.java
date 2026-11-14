package com.farmacia.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Filtros de GET /auditoria (spec 014). Los vacíos no filtran; sin fechas, los últimos 7 días. */
public record FiltroAuditoriaFormulario(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        String usuario,
        String accion,
        String entidad,
        String resultado) {

    public static FiltroAuditoriaFormulario vacio() {
        return new FiltroAuditoriaFormulario(null, null, null, null, null, null);
    }
}
