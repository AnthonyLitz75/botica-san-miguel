package com.farmacia.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reparte el descuento de una línea entre sus tramos de lote, en proporción a la cantidad de cada tramo
 * (HALF_UP, escala 2). El último tramo recibe el resto, de modo que la suma es exactamente el descuento.
 * Es aritmética pura: no decide cuánto se descuenta (eso lo hace la {@code PoliticaDescuento}).
 */
public final class RepartoDescuento {

    private RepartoDescuento() {
    }

    public static List<BigDecimal> repartir(BigDecimal descuento, List<Integer> cantidades) {
        if (descuento == null || descuento.signum() < 0) {
            throw new IllegalArgumentException("El descuento no puede ser negativo");
        }
        if (cantidades == null || cantidades.isEmpty()
                || cantidades.stream().anyMatch(cantidad -> cantidad == null || cantidad <= 0)) {
            throw new IllegalArgumentException("Cada tramo debe tener una cantidad mayor que cero");
        }
        BigDecimal total = descuento.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cantidadTotal = BigDecimal.valueOf(cantidades.stream().mapToLong(Integer::longValue).sum());

        List<BigDecimal> partes = new ArrayList<>(cantidades.size());
        BigDecimal asignado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (int i = 0; i < cantidades.size() - 1; i++) {
            BigDecimal parte = total.multiply(BigDecimal.valueOf(cantidades.get(i)))
                    .divide(cantidadTotal, 2, RoundingMode.HALF_UP);
            // Nunca asignar más de lo que queda (evita que el último tramo quede negativo).
            parte = parte.min(total.subtract(asignado));
            partes.add(parte);
            asignado = asignado.add(parte);
        }
        partes.add(total.subtract(asignado));
        return List.copyOf(partes);
    }
}
