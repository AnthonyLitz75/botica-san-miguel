package com.farmacia.model;

import java.time.LocalDate;

public record AlertaVencimientoVista(
        Integer idLote,
        Integer idMedicamento,
        String codigoMedicamento,
        String nombreMedicamento,
        String numeroLote,
        LocalDate fechaVencimiento,
        Integer stockActual,
        String unidadControl,
        Long diasParaVencer,
        String tipoAlerta) {
}
