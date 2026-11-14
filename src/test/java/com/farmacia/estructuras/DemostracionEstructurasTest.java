package com.farmacia.estructuras;

import com.farmacia.model.LoteDisponibleVista;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas demostrativas de las cuatro (4) estructuras de datos requeridas para la sustentación
 * académica de Algoritmos y Estructuras de Datos (AyE) (CA-4.3).
 */
@DisplayName("Demostración Académica de Estructuras de Datos (AyE)")
class DemostracionEstructurasTest {

    @Test
    @DisplayName("1. ListaEnlazada: debe enlazar el primer nodo con el tercero tras eliminar el intermedio")
    void listaEnlazada_debeEnlazarPrimerNodoConTercero_trasEliminarElIntermedio() {
        ListaEnlazada<String> lista = new ListaEnlazada<>();
        lista.agregar("Nodo 1 - Paracetamol 500mg");
        lista.agregar("Nodo 2 - Ibuprofeno 400mg");
        lista.agregar("Nodo 3 - Amoxicilina 500mg");

        assertThat(lista.tamanio()).isEqualTo(3);

        // Eliminación del nodo intermedio
        Optional<String> eliminado = lista.eliminar(s -> s.contains("Ibuprofeno"));
        assertThat(eliminado).contains("Nodo 2 - Ibuprofeno 400mg");

        // Verificación de invariantes de punteros: tamaño se decrementa a 2
        assertThat(lista.tamanio()).isEqualTo(2);

        // Al recorrer la lista, el Nodo 1 enlaza directamente con el Nodo 3
        Iterator<String> it = lista.iterator();
        assertThat(it.hasNext()).isTrue();
        assertThat(it.next()).isEqualTo("Nodo 1 - Paracetamol 500mg");
        assertThat(it.hasNext()).isTrue();
        assertThat(it.next()).isEqualTo("Nodo 3 - Amoxicilina 500mg");
        assertThat(it.hasNext()).isFalse();

        assertThat(lista.comoLista())
                .containsExactly("Nodo 1 - Paracetamol 500mg", "Nodo 3 - Amoxicilina 500mg");
    }

    @Test
    @DisplayName("2. ColaPrioridadLotes: debe extraer 5 lotes desordenados en orden ascendente de vencimiento (empate por id_lote)")
    void colaPrioridadLotes_debeExtraerCincoLotesDesordenados_enOrdenAscendenteDeVencimiento_conEmpatePorIdLote() {
        ColaPrioridadLotes cola = new ColaPrioridadLotes();

        // 5 lotes insertados en orden deliberadamente desordenado
        LoteDisponibleVista loteA = crearLote(103, LocalDate.of(2026, 12, 1), 50);
        LoteDisponibleVista loteB = crearLote(101, LocalDate.of(2026, 5, 10), 30);
        LoteDisponibleVista loteC = crearLote(105, LocalDate.of(2026, 8, 15), 20); // Empate de fecha con loteD
        LoteDisponibleVista loteD = crearLote(102, LocalDate.of(2026, 8, 15), 40); // Empate de fecha con loteC, pero id 102 < 105
        LoteDisponibleVista loteE = crearLote(104, LocalDate.of(2026, 3, 1), 15);  // Vence primero

        cola.agregar(loteA);
        cola.agregar(loteB);
        cola.agregar(loteC);
        cola.agregar(loteD);
        cola.agregar(loteE);

        assertThat(cola.cantidad()).isEqualTo(5);

        // Extracción sucesiva: debe respetar política FEFO estricta y desempate por id_lote
        assertThat(cola.sacarSiguiente()).contains(loteE); // 2026-03-01 (id 104)
        assertThat(cola.sacarSiguiente()).contains(loteB); // 2026-05-10 (id 101)
        assertThat(cola.sacarSiguiente()).contains(loteD); // 2026-08-15 (id 102 < 105)
        assertThat(cola.sacarSiguiente()).contains(loteC); // 2026-08-15 (id 105)
        assertThat(cola.sacarSiguiente()).contains(loteA); // 2026-12-01 (id 103)

        assertThat(cola.estaVacia()).isTrue();
    }

    private static LoteDisponibleVista crearLote(int idLote, LocalDate vencimiento, int stock) {
        return new LoteDisponibleVista(
                idLote,
                1,
                "MED-001",
                "Paracetamol 500mg",
                "LOT-" + idLote,
                vencimiento,
                stock,
                "TABLETA"
        );
    }

    @Test
    @DisplayName("3. PilaDeshacer: debe revertir estados en orden cronológico inverso (LIFO)")
    void pilaDeshacer_debeRevertirEstados_enOrdenCronologicoInverso() {
        PilaDeshacer<String> pila = new PilaDeshacer<>();

        pila.apilar("Estado 1: Canasta con 1 Paracetamol");
        pila.apilar("Estado 2: Canasta con 1 Paracetamol + 2 Ibuprofenos");
        pila.apilar("Estado 3: Canasta con 1 Paracetamol + 2 Ibuprofenos + Descuento S/ 2.00");

        assertThat(pila.cantidad()).isEqualTo(3);

        // Operación Deshacer (Undo 1): revierte al estado con 2 productos antes del descuento
        Optional<String> undo1 = pila.desapilar();
        assertThat(undo1).contains("Estado 3: Canasta con 1 Paracetamol + 2 Ibuprofenos + Descuento S/ 2.00");

        // Operación Deshacer (Undo 2): revierte al estado con solo 1 producto
        Optional<String> undo2 = pila.desapilar();
        assertThat(undo2).contains("Estado 2: Canasta con 1 Paracetamol + 2 Ibuprofenos");

        // Operación Deshacer (Undo 3): revierte al estado inicial
        Optional<String> undo3 = pila.desapilar();
        assertThat(undo3).contains("Estado 1: Canasta con 1 Paracetamol");

        assertThat(pila.estaVacia()).isTrue();
    }

    @Test
    @DisplayName("4. ArbolCategorias: debe resolver búsqueda O(1), ruta a la raíz y descendientes de una rama por DFS")
    void arbolCategorias_debeRetornarRutaHaciaRaiz_yDescendientesDeUnaRama() {
        ArbolCategorias arbol = new ArbolCategorias();

        // 1: Raíz
        arbol.insertar(1, "Medicamentos", null);
        // 2 y 4: Hijos de 1
        arbol.insertar(2, "Analgésicos", 1);
        arbol.insertar(4, "Antibióticos", 1);
        // 3: Nieto de 1 (hijo de 2)
        arbol.insertar(3, "Antiinflamatorios No Esteroideos", 2);
        // 5: Nieto de 1 (hijo de 4)
        arbol.insertar(5, "Amoxicilina y Betalactámicos", 4);

        // Búsqueda O(1) asistida por mapa interno
        Optional<ArbolCategorias.Nodo> nodoBuscado = arbol.buscar(3);
        assertThat(nodoBuscado).isPresent();
        assertThat(nodoBuscado.get().nombre()).isEqualTo("Antiinflamatorios No Esteroideos");
        assertThat(nodoBuscado.get().idPadre()).isEqualTo(2);

        // Ruta a la raíz desde el nodo 3: [1 -> 2 -> 3]
        List<ArbolCategorias.Nodo> ruta = arbol.ruta(3);
        assertThat(ruta)
                .extracting(ArbolCategorias.Nodo::nombre)
                .containsExactly(
                        "Medicamentos",
                        "Analgésicos",
                        "Antiinflamatorios No Esteroideos"
                );

        // Descendientes de la categoría 2 ("Analgésicos") - incluye al nodo y toda su descendencia en preorden
        List<ArbolCategorias.Nodo> descendientesAnalg = arbol.descendientes(2);
        assertThat(descendientesAnalg)
                .extracting(ArbolCategorias.Nodo::nombre)
                .containsExactly(
                        "Analgésicos",
                        "Antiinflamatorios No Esteroideos"
                );

        // Descendientes de la categoría raíz 1 ("Medicamentos") mediante recorrido DFS
        List<ArbolCategorias.Nodo> todosLosDescendientes = arbol.descendientes(1);
        assertThat(todosLosDescendientes)
                .extracting(ArbolCategorias.Nodo::id)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    }
}
