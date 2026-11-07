package com.farmacia.estructuras;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PilaDeshacerTest {
    @Test
    void desapilaEnOrdenLifo() {
        var pila = new PilaDeshacer<String>();
        pila.apilar("agregar");
        pila.apilar("retirar");
        assertEquals("retirar", pila.desapilar().orElseThrow());
        assertEquals("agregar", pila.desapilar().orElseThrow());
        assertTrue(pila.estaVacia());
    }

    @Test
    void vaciaDevuelveOptionalVacioYLimpiaHistorial() {
        var pila = new PilaDeshacer<Integer>();
        assertTrue(pila.desapilar().isEmpty());
        pila.apilar(1);
        pila.limpiar();
        assertEquals(0, pila.cantidad());
    }
}
