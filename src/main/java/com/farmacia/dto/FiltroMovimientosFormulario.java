package com.farmacia.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Filtros para GET /movimientos (spec 005, US-2, US-3, RF-002).
 */
public record FiltroMovimientosFormulario(
        String tipo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        String motivo,
        Integer idMedicamento,
        String numeroLote,
        Integer idUsuario,
        Integer pagina) {

    public static FiltroMovimientosFormulario vacio() {
        return new FiltroMovimientosFormulario(null, null, null, null, null, null, null, null);
    }

    public java.util.Map<String, Object> aParametros() {
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (tipo != null && !tipo.isBlank()) params.put("tipo", tipo);
        if (desde != null) params.put("desde", desde.toString());
        if (hasta != null) params.put("hasta", hasta.toString());
        if (motivo != null && !motivo.isBlank()) params.put("motivo", motivo);
        if (idMedicamento != null) params.put("idMedicamento", idMedicamento);
        if (numeroLote != null && !numeroLote.isBlank()) params.put("numeroLote", numeroLote);
        if (idUsuario != null) params.put("idUsuario", idUsuario);
        return params;
    }
}
