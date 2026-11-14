package com.farmacia.dto;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;

public record FiltroReporteFormulario(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        Integer limite,
        String estado,
        Integer idProveedor) {

    public static FiltroReporteFormulario porDefecto(Clock clock) {
        LocalDate hoy = LocalDate.now(clock);
        return new FiltroReporteFormulario(hoy.minusDays(30), hoy, 10, null, null);
    }

    public FiltroReporteFormulario conRangoPorDefecto(Clock clock) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate d = (desde != null) ? desde : hoy.minusDays(30);
        LocalDate h = (hasta != null) ? hasta : hoy;
        Integer lim = (limite != null && limite > 0) ? limite : 10;
        return new FiltroReporteFormulario(d, h, lim, estado, idProveedor);
    }

    public void validar() {
        if (desde != null && hasta != null) {
            if (desde.isAfter(hasta)) {
                throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final");
            }
            if (ChronoUnit.DAYS.between(desde, hasta) > 366) {
                throw new IllegalArgumentException("El rango de fechas no puede superar los 366 días");
            }
        }
    }
}
