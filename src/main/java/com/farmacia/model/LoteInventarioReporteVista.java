package com.farmacia.model;

import java.time.LocalDate;

public record LoteInventarioReporteVista(
        Integer idLote,
        String numeroLote,
        LocalDate fechaVencimiento,
        int stockActual,
        long diasParaVencer) {
}
