package com.farmacia.estructuras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ListaEnlazadaTest {
    @Test
    void agregaRecorreYActualizaSinCambiarElOrden() {
        var lista = new ListaEnlazada<String>();
        lista.agregar("venta");
        lista.agregar("orden");
        lista.agregar("movimiento");

        assertTrue(lista.reemplazar("orden"::equals, "orden actualizada"));
        assertEquals(List.of("venta", "orden actualizada", "movimiento"), lista.comoLista());
        assertEquals("movimiento", lista.buscar("movimiento"::equals).orElseThrow());
    }

    @Test
    void eliminaCabezaMedioYColaYPermiteReutilizarLaLista() {
        var lista = new ListaEnlazada<Integer>();
        lista.agregar(1);
        lista.agregar(2);
        lista.agregar(3);

        assertEquals(1, lista.eliminar(valor -> valor == 1).orElseThrow());
        assertEquals(2, lista.eliminar(valor -> valor == 2).orElseThrow());
        assertEquals(3, lista.eliminar(valor -> valor == 3).orElseThrow());
        assertTrue(lista.estaVacia());
        assertTrue(lista.eliminar(valor -> valor == 4).isEmpty());

        lista.agregar(5);
        assertEquals(List.of(5), lista.comoLista());
        assertEquals(1, lista.tamanio());
    }

    @Test
    void limpiarReiniciaNodosYContador() {
        var lista = new ListaEnlazada<String>();
        lista.agregar("elemento");
        lista.limpiar();

        assertTrue(lista.estaVacia());
        assertEquals(0, lista.tamanio());
        assertFalse(lista.iterator().hasNext());
    }
}
