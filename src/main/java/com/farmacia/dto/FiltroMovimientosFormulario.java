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
}
