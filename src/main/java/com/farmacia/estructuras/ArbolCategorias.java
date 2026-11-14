package com.farmacia.estructuras;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Estructura de datos de Árbol N-ario en memoria para la jerarquía de categorías.
 * <p>
 * Características y Complejidad Algorítmica:
 * <ul>
 *   <li><b>Búsqueda por ID ({@link #buscar(Integer)}):</b> O(1) asistida por un índice auxiliar {@link LinkedHashMap}.</li>
 *   <li><b>Inserción ({@link #insertar(Integer, String, Integer)}):</b> O(1) validando preexistencia del padre e integridad referencial.</li>
 *   <li><b>Ruta a la raíz ({@link #ruta(Integer)}):</b> O(h) donde h es la altura del árbol (típicamente ≤ 5 en taxonomías de farmacia).</li>
 *   <li><b>Descendientes y Recorrido DFS ({@link #descendientes(Integer)}, {@link #recorrerEnProfundidad()}):</b> O(k) donde k es la cantidad
 *       de nodos en la rama, visitando en preorden y preservando el orden de inserción de los nodos hijos.</li>
 * </ul>
 * Esta clase es pura y no tiene dependencias con el framework Spring.
 */
public class ArbolCategorias {

    /**
     * Representación inmutable de un nodo de categoría dentro del árbol.
     *
     * @param id Identificador único de la categoría.
     * @param nombre Nombre descriptivo de la categoría.
     * @param idPadre Identificador de la categoría padre (null si es raíz).
     */
    public record Nodo(Integer id, String nombre, Integer idPadre) {
        public Nodo {
            Objects.requireNonNull(id, "El id de categoría no puede ser nulo");
            Objects.requireNonNull(nombre, "El nombre de categoría no puede ser nulo");
            if (nombre.isBlank()) {
                throw new IllegalArgumentException("El nombre de categoría no puede estar vacío");
            }
        }
    }

    /**
     * Nodo interno que enlaza punteros de árbol n-ario (padre y lista de hijos).
     */
    private static class ElementoNodo {
        private final Nodo nodo;
        private final List<ElementoNodo> hijos = new ArrayList<>();
        private ElementoNodo padre;

        ElementoNodo(Nodo nodo) {
            this.nodo = nodo;
        }
    }

    private final Map<Integer, ElementoNodo> indiceNodos = new LinkedHashMap<>();
    private final List<ElementoNodo> raices = new ArrayList<>();

    /**
     * Inserta una nueva categoría en el árbol.
     *
     * @param id Identificador único de la categoría.
     * @param nombre Nombre de la categoría.
     * @param idPadre Identificador del padre, o null si es una raíz.
     * @throws IllegalArgumentException Si el id ya existe, si el padre no existe, o si se detecta auto-referencia.
     */
    public void insertar(Integer id, String nombre, Integer idPadre) {
        if (indiceNodos.containsKey(id)) {
            throw new IllegalArgumentException("Ya existe una categoría con id: " + id);
        }
        if (idPadre != null && idPadre.equals(id)) {
            throw new IllegalArgumentException("Un nodo no puede ser padre de sí mismo: " + id);
        }

        ElementoNodo elementoPadre = null;
        if (idPadre != null) {
            elementoPadre = indiceNodos.get(idPadre);
            if (elementoPadre == null) {
                throw new IllegalArgumentException("El padre especificado no existe en el árbol: " + idPadre);
            }
        }

        Nodo nuevoNodo = new Nodo(id, nombre, idPadre);
        ElementoNodo nuevoElemento = new ElementoNodo(nuevoNodo);

        if (elementoPadre == null) {
            raices.add(nuevoElemento);
        } else {
            nuevoElemento.padre = elementoPadre;
            elementoPadre.hijos.add(nuevoElemento);
        }

        indiceNodos.put(id, nuevoElemento);
    }

    /**
     * Busca un nodo por su identificador único en tiempo O(1).
     *
     * @param id Identificador a buscar.
     * @return {@link Optional} con el nodo si existe, o vacío en caso contrario.
     */
    public Optional<Nodo> buscar(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        ElementoNodo elemento = indiceNodos.get(id);
        return elemento != null ? Optional.of(elemento.nodo) : Optional.empty();
    }

    /**
     * Verifica si una categoría existe en el árbol en tiempo O(1).
     */
    public boolean contiene(Integer id) {
        return id != null && indiceNodos.containsKey(id);
    }

    /**
     * Retorna la cantidad total de categorías registradas en el árbol.
     */
    public int tamanio() {
        return indiceNodos.size();
    }

    /**
     * Indica si el árbol no contiene categorías.
     */
    public boolean estaVacio() {
        return indiceNodos.isEmpty();
    }

    /**
     * Retorna la lista inmutable de nodos raíz (aquellos sin categoría padre).
     */
    public List<Nodo> raices() {
        return raices.stream().map(e -> e.nodo).toList();
    }

    /**
     * Obtiene los hijos directos de un nodo en tiempo O(1), preservando el orden de inserción.
     * Si el id es nulo, devuelve las raíces del árbol.
     *
     * @param id Identificador del nodo padre.
     * @return Lista inmutable de nodos hijos directos.
     */
    public List<Nodo> hijos(Integer id) {
        if (id == null) {
            return raices();
        }
        ElementoNodo elemento = indiceNodos.get(id);
        if (elemento == null) {
            return List.of();
        }
        return elemento.hijos.stream().map(e -> e.nodo).toList();
    }

    /**
     * Calcula la ruta desde la raíz hasta el nodo indicado en tiempo O(h).
     *
     * @param id Identificador del nodo destino.
     * @return Lista ordenada desde la raíz hasta el nodo inclusivo (ej: [Analgésicos, Antiinflamatorios, AINEs]).
     */
    public List<Nodo> ruta(Integer id) {
        if (id == null) {
            return List.of();
        }
        ElementoNodo actual = indiceNodos.get(id);
        if (actual == null) {
            return List.of();
        }

        List<Nodo> camino = new ArrayList<>();
        while (actual != null) {
            camino.add(actual.nodo);
            actual = actual.padre;
        }
        Collections.reverse(camino);
        return Collections.unmodifiableList(camino);
    }

    /**
     * Retorna el nodo y todos sus descendientes en preorden (DFS) en tiempo O(k).
     *
     * @param id Identificador del nodo raíz de la rama.
     * @return Lista en preorden que incluye al nodo dado y toda su descendencia.
     */
    public List<Nodo> descendientes(Integer id) {
        if (id == null) {
            return List.of();
        }
        ElementoNodo elemento = indiceNodos.get(id);
        if (elemento == null) {
            return List.of();
        }

        List<Nodo> resultado = new ArrayList<>();
        recorrerDfs(elemento, resultado);
        return Collections.unmodifiableList(resultado);
    }

    /**
     * Retorna los identificadores de la rama (nodo y todos sus descendientes).
     */
    public List<Integer> descendientesIds(Integer id) {
        return descendientes(id).stream().map(Nodo::id).toList();
    }

    /**
     * Realiza un recorrido en profundidad (DFS) en preorden sobre todo el árbol, respetando
     * el orden de inserción de las raíces y de los hijos en tiempo O(n).
     *
     * @return Lista de nodos en preorden completo.
     */
    public List<Nodo> recorrerEnProfundidad() {
        List<Nodo> resultado = new ArrayList<>(indiceNodos.size());
        for (ElementoNodo raiz : raices) {
            recorrerDfs(raiz, resultado);
        }
        return Collections.unmodifiableList(resultado);
    }

    private void recorrerDfs(ElementoNodo actual, List<Nodo> acumulador) {
        acumulador.add(actual.nodo);
        for (ElementoNodo hijo : actual.hijos) {
            recorrerDfs(hijo, acumulador);
        }
    }
}
