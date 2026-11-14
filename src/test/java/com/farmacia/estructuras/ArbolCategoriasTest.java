package com.farmacia.estructuras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArbolCategoriasTest {

    private ArbolCategorias arbol;

    @BeforeEach
    void setUp() {
        arbol = new ArbolCategorias();
    }

    @Test
    @DisplayName("CA-1.1: Con 3 niveles, ruta(AINEs) devuelve [Analgésicos, Antiinflamatorios, AINEs]")
    void rutaDevuelveCaminoDesdeRaizHastaNodo() {
        // Nivel 1 (raíz)
        arbol.insertar(1, "Analgésicos", null);
        // Nivel 2
        arbol.insertar(2, "Antiinflamatorios", 1);
        // Nivel 3
        arbol.insertar(3, "AINEs", 2);

        List<String> nombresRuta = arbol.ruta(3).stream()
                .map(ArbolCategorias.Nodo::nombre)
                .toList();

        assertEquals(List.of("Analgésicos", "Antiinflamatorios", "AINEs"), nombresRuta);
    }

    @Test
    @DisplayName("CA-1.2: descendientes(Analgésicos) incluye los 3 niveles; descendientes(AINEs) solo a sí mismo")
    void descendientesIncluyeRamaCompleta() {
        arbol.insertar(1, "Analgésicos", null);
        arbol.insertar(2, "Antiinflamatorios", 1);
        arbol.insertar(3, "AINEs", 2);
        arbol.insertar(4, "Opioides", 1);

        List<Integer> idsDescendientesRaiz = arbol.descendientes(1).stream()
                .map(ArbolCategorias.Nodo::id)
                .toList();
        assertEquals(List.of(1, 2, 3, 4), idsDescendientesRaiz);

        List<Integer> idsDescendientesHoja = arbol.descendientes(3).stream()
                .map(ArbolCategorias.Nodo::id)
                .toList();
        assertEquals(List.of(3), idsDescendientesHoja);
    }

    @Test
    @DisplayName("CA-1.3: Insertar nodo duplicado, padre inexistente o ciclo lanza excepción")
    void validacionesDeIntegridadAlInsertar() {
        arbol.insertar(1, "Analgésicos", null);

        // Id duplicado
        assertThrows(IllegalArgumentException.class, () -> arbol.insertar(1, "Otro", null));

        // Padre inexistente
        assertThrows(IllegalArgumentException.class, () -> arbol.insertar(2, "Hijo", 999));

        // Auto-referencia (ciclo directo)
        assertThrows(IllegalArgumentException.class, () -> arbol.insertar(2, "Ciclo", 2));
    }

    @Test
    @DisplayName("CA-1.4: El DFS visita en preorden y respeta el orden de inserción de los hijos")
    void dfsVisitaEnPreordenRespetandoOrdenDeInsercion() {
        // Árbol:
        //        1 (Medicamentos)
        //       /               \
        //  2 (Analgésicos)     5 (Antibióticos)
        //    /          \            \
        // 3 (AINEs)  4 (Opioides)  6 (Penicilinas)
        arbol.insertar(1, "Medicamentos", null);
        arbol.insertar(2, "Analgésicos", 1);
        arbol.insertar(3, "AINEs", 2);
        arbol.insertar(4, "Opioides", 2);
        arbol.insertar(5, "Antibióticos", 1);
        arbol.insertar(6, "Penicilinas", 5);

        List<String> recorrido = arbol.recorrerEnProfundidad().stream()
                .map(ArbolCategorias.Nodo::nombre)
                .toList();

        assertEquals(List.of(
                "Medicamentos",
                "Analgésicos",
                "AINEs",
                "Opioides",
                "Antibióticos",
                "Penicilinas"
        ), recorrido);
    }

    @Test
    @DisplayName("Operaciones básicas de búsqueda, hijos y tamaño")
    void operacionesBasicas() {
        assertTrue(arbol.estaVacio());
        assertEquals(0, arbol.tamanio());

        arbol.insertar(10, "Cardiología", null);
        arbol.insertar(20, "Antihipertensivos", 10);

        assertFalse(arbol.estaVacio());
        assertEquals(2, arbol.tamanio());
        assertTrue(arbol.contiene(10));
        assertTrue(arbol.contiene(20));
        assertFalse(arbol.contiene(30));

        var nodoBuscado = arbol.buscar(20);
        assertTrue(nodoBuscado.isPresent());
        assertEquals("Antihipertensivos", nodoBuscado.get().nombre());
        assertEquals(10, nodoBuscado.get().idPadre());

        var hijosRaiz = arbol.hijos(10);
        assertEquals(1, hijosRaiz.size());
        assertEquals(20, hijosRaiz.getFirst().id());

        var raices = arbol.raices();
        assertEquals(1, raices.size());
        assertEquals(10, raices.getFirst().id());
    }
}
