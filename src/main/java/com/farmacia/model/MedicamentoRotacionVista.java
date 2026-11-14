package com.farmacia.model;

import java.math.BigDecimal;

public record MedicamentoRotacionVista(
        Integer idMedicamento,
        String codigo,
        String nombre,
        long unidadesVendidas,
        BigDecimal totalVendido) {
}
