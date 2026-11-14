package com.farmacia.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 004 (CA-3.1, CA-3.2): reparto del descuento de una línea entre sus tramos de lote. */
class RepartoDescuentoTest {

    @Test
    @DisplayName("CA-3.1: 5.00 en tramos de 4 y 6 → 2.00 y 3.00")
    void proporcionalALaCantidad() {
        assertEquals(List.of(new BigDecimal("2.00"), new BigDecimal("3.00")),
                RepartoDescuento.repartir(new BigDecimal("5.00"), List.of(4, 6)));
    }

    @Test
    @DisplayName("CA-3.2: 0.01 en 3 tramos suma exactamente 0.01 (el último absorbe el redondeo)")
    void elUltimoTramoAbsorbeElRedondeo() {
        var partes = RepartoDescuento.repartir(new BigDecimal("0.01"), List.of(1, 1, 1));
        assertEquals(List.of(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.01")), partes);
    }

    @ParameterizedTest(name = "{0} entre {1}")
    @CsvSource({
            "5.00, 4;6",
            "0.01, 1;1;1",
            "10.00, 3;3;3",
            "7.77, 1;2;4;8",
            "0.05, 1;1",
            "100.00, 7",
            "0.00, 2;5"
    })
    @DisplayName("La suma de las partes es exactamente el descuento y ninguna es negativa")
    void sumaExactaYSinNegativos(String descuento, String cantidades) {
        var lista = Arrays.stream(cantidades.split(";")).map(Integer::valueOf).toList();
        var partes = RepartoDescuento.repartir(new BigDecimal(descuento), lista);

        assertEquals(lista.size(), partes.size());
        assertEquals(new BigDecimal(descuento).setScale(2),
                partes.stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
        assertTrue(partes.stream().allMatch(parte -> parte.signum() >= 0 && parte.scale() == 2), partes.toString());
    }

    @Test
    @DisplayName("Datos inválidos se rechazan")
    void validaLosDatos() {
        assertThrows(IllegalArgumentException.class, () -> RepartoDescuento.repartir(BigDecimal.ONE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> RepartoDescuento.repartir(BigDecimal.ONE, List.of(0)));
        assertThrows(IllegalArgumentException.class,
                () -> RepartoDescuento.repartir(new BigDecimal("-1"), List.of(1)));
    }
}
