package com.farmacia.model;

import java.math.BigDecimal;
import java.util.Map;

public record EstadoComprasResumenVista(
        Map<String, CantidadYMonto> totalesPorEstado,
        int totalOrdenes,
        BigDecimal montoTotal) {

    public record CantidadYMonto(int cantidad, BigDecimal monto) {
    }
}
