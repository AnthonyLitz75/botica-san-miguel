package com.farmacia.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CompraReporteVista(
        Integer idOrden,
        String numeroOrden,
        LocalDate fechaEmision,
        String proveedor,
        String estado,
        BigDecimal totalEstimado,
        int unidadesSolicitadas,
        int unidadesRecibidas,
        int unidadesPendientes) {
}
