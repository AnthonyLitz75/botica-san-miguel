package com.farmacia.model;

import java.time.LocalDate;

/**
 * Representa una fila en la vista de inventario de lotes por medicamento,
 * incluyendo el cálculo de prioridad de salida según la política FEFO.
 */
public record LoteFilaVista(
        Integer idLote,
        Integer idMedicamento,
        String numeroLote,
        LocalDate fechaVencimiento,
        Integer stockActual,
        boolean tieneBadgeSalePrimero,
        String textoPrioridad,
        String textoPosicionCola
) {
    public boolean esLoteUnico() {
        return "Lote único".equals(textoPrioridad);
    }
}
