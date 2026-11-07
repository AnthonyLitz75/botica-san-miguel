package com.farmacia.model;

import java.time.LocalDate;

public record AsignacionLoteFefo(
        Integer idLote,
        String numeroLote,
        LocalDate fechaVencimiento,
        Integer cantidadDisponible,
        Integer cantidadAsignada) {
}
