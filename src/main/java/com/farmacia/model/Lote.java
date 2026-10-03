package com.farmacia.model;

import java.time.LocalDate;

public record Lote(
        Integer idLote,
        Integer idMedicamento,
        String numeroLote,
        LocalDate fechaVencimiento,
        Integer stockActual) {
}