package com.farmacia.model;

import java.math.BigDecimal;

public record PanelVista(
        BigDecimal totalMes,
        long cantidadVentasMes,
        BigDecimal ticketPromedio,
        long medicamentosAlertaStock,
        long lotesPorVencer,
        int diasPorVencer) {
}
