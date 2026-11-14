package com.farmacia.service.politica;

import java.math.BigDecimal;

/**
 * Patrón Strategy: regla que convierte el valor de descuento ingresado para una línea en un importe
 * a descontar. Permite añadir otras reglas (porcentaje, promociones) sin editar los servicios de venta.
 */
public interface PoliticaDescuento {

    /**
     * @param importeBruto   cantidad × precio unitario de la línea (escala 2)
     * @param valorIngresado valor que escribió el vendedor; {@code null} equivale a sin descuento
     * @return importe a descontar, con escala 2, entre 0 y {@code importeBruto}
     * @throws IllegalArgumentException si el valor no es válido para esta regla
     */
    BigDecimal calcular(BigDecimal importeBruto, BigDecimal valorIngresado);
}
