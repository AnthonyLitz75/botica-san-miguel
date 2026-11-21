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

    public java.util.Map<String, Object> aParametros(LocalDate desdeEfectivo, LocalDate hastaEfectivo) {
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        LocalDate d = desde != null ? desde : desdeEfectivo;
        LocalDate h = hasta != null ? hasta : hastaEfectivo;
        if (d != null) params.put("desde", d.toString());
        if (h != null) params.put("hasta", h.toString());
        if (usuario != null && !usuario.isBlank()) params.put("usuario", usuario);
        if (accion != null && !accion.isBlank()) params.put("accion", accion);
        if (entidad != null && !entidad.isBlank()) params.put("entidad", entidad);
        if (resultado != null && !resultado.isBlank()) params.put("resultado", resultado);
        return params;
    }
}
