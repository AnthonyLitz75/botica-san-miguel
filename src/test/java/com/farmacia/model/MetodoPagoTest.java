package com.farmacia.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetodoPagoTest {

    @Test
    @DisplayName("Etiquetas correctas para cada metodo de pago")
    void etiquetas() {
        assertEquals("Efectivo", MetodoPago.EFECTIVO.etiqueta());
        assertEquals("Yape / Plin", MetodoPago.YAPE_PLIN.etiqueta());
        assertEquals("Tarjeta", MetodoPago.TARJETA.etiqueta());
    }

    @Test
    @DisplayName("Valores del enum")
    void valores() {
        assertEquals(3, MetodoPago.values().length);
        assertEquals(MetodoPago.EFECTIVO, MetodoPago.valueOf("EFECTIVO"));
    }
}
