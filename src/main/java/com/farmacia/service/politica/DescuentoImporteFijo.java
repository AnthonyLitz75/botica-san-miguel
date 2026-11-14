package com.farmacia.service.politica;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Descuento en importe fijo (soles) por línea: 0 ≤ descuento ≤ importe bruto, con céntimos. */
@Component
public class DescuentoImporteFijo implements PoliticaDescuento {

    @Override
    public BigDecimal calcular(BigDecimal importeBruto, BigDecimal valorIngresado) {
        if (valorIngresado == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (valorIngresado.signum() < 0) {
            throw new IllegalArgumentException("El descuento no puede ser negativo");
        }
        if (valorIngresado.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El descuento admite como máximo dos decimales");
        }
        BigDecimal descuento = valorIngresado.setScale(2, RoundingMode.HALF_UP);
        if (descuento.compareTo(importeBruto) > 0) {
            throw new IllegalArgumentException("El descuento supera el importe de la línea");
        }
        return descuento;
    }
}
