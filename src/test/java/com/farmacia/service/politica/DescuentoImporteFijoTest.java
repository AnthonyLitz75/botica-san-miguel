package com.farmacia.service.politica;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Spec 004, US-4 (CA-4.1) y CA-1.1 a CA-1.3 a nivel de política. */
class DescuentoImporteFijoTest {
    private final PoliticaDescuento politica = new DescuentoImporteFijo();

    @Test
    @DisplayName("CA-1.1: 5.00 sobre un bruto de 30.00 descuenta 5.00")
    void descuentaElImporteIngresado() {
        assertEquals(new BigDecimal("5.00"), politica.calcular(new BigDecimal("30.00"), new BigDecimal("5")));
    }

    @Test
    @DisplayName("Sin valor ingresado el descuento es 0.00")
    void sinValorEsCero() {
        assertEquals(new BigDecimal("0.00"), politica.calcular(new BigDecimal("30.00"), null));
    }

    @Test
    @DisplayName("Descuento igual al bruto (línea gratis) está permitido")
    void igualAlBrutoPermitido() {
        assertEquals(new BigDecimal("30.00"), politica.calcular(new BigDecimal("30.00"), new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("CA-1.2: 30.01 sobre 30.00 se rechaza con mensaje claro")
    void superaElImporteDeLaLinea() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> politica.calcular(new BigDecimal("30.00"), new BigDecimal("30.01")));
        assertEquals("El descuento supera el importe de la línea", error.getMessage());
    }

    @Test
    @DisplayName("CA-1.3: un descuento negativo se rechaza")
    void negativoRechazado() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> politica.calcular(new BigDecimal("30.00"), new BigDecimal("-0.01")));
        assertEquals("El descuento no puede ser negativo", error.getMessage());
    }

    @Test
    @DisplayName("Más de dos decimales se rechaza (importes en céntimos)")
    void masDeDosDecimalesRechazado() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> politica.calcular(new BigDecimal("30.00"), new BigDecimal("1.005")));
        assertEquals("El descuento admite como máximo dos decimales", error.getMessage());
    }
}
