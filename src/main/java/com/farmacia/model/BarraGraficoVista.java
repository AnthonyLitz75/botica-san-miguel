package com.farmacia.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BarraGraficoVista(
        LocalDate fecha,
        BigDecimal total,
        int porcentajeAltura) {
}
