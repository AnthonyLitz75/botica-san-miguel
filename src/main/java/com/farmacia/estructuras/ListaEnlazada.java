package com.farmacia.estructuras;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Lista simplemente enlazada que conserva el orden de preparación de una operación. */
public final class ListaEnlazada<T> implements Iterable<T> {
    private Nodo<T> cabeza;
    private Nodo<T> cola;
    private int tamanio;

    public void agregar(T elemento) {
        Nodo<T> nuevo = new Nodo<>(Objects.requireNonNull(elemento, "El elemento es obligatorio"));
        if (cabeza == null) {
            cabeza = nuevo;
        } else {
            cola.siguiente = nuevo;
        }
        cola = nuevo;
        tamanio++;
    }

    public Optional<T> buscar(Predicate<? super T> criterio) {
        Objects.requireNonNull(criterio, "El criterio de búsqueda es obligatorio");
        for (Nodo<T> actual = cabeza; actual != null; actual = actual.siguiente) {
            if (criterio.test(actual.valor)) {
                return Optional.of(actual.valor);
            }
        }
        return Optional.empty();
    }

    /** Reemplaza el primer elemento que coincide y conserva su posición. */
    public boolean reemplazar(Predicate<? super T> criterio, T elemento) {
        Objects.requireNonNull(criterio, "El criterio de reemplazo es obligatorio");
        Objects.requireNonNull(elemento, "El elemento es obligatorio");
        for (Nodo<T> actual = cabeza; actual != null; actual = actual.siguiente) {
            if (criterio.test(actual.valor)) {
                actual.valor = elemento;
                return true;
            }
        }
        return false;
    }

    public Optional<T> eliminar(Predicate<? super T> criterio) {
        Objects.requireNonNull(criterio, "El criterio de eliminación es obligatorio");
        Nodo<T> anterior = null;
        Nodo<T> actual = cabeza;
        while (actual != null) {
            if (criterio.test(actual.valor)) {
                if (anterior == null) {
                    cabeza = actual.siguiente;
                } else {
                    anterior.siguiente = actual.siguiente;
                }
                if (actual == cola) {
                    cola = anterior;
                }
                tamanio--;
                return Optional.of(actual.valor);
            }
            anterior = actual;
            actual = actual.siguiente;
        }
        return Optional.empty();
    }

    public List<T> comoLista() {
        List<T> elementos = new ArrayList<>(tamanio);
        for (T elemento : this) {
            elementos.add(elemento);
        }
        return List.copyOf(elementos);
    }

    public void limpiar() {
        cabeza = null;
        cola = null;
        tamanio = 0;
    }

    public int tamanio() {
        return tamanio;
    }

    public boolean estaVacia() {
        return tamanio == 0;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private Nodo<T> actual = cabeza;

            @Override
            public boolean hasNext() {
                return actual != null;
            }

            @Override
            public T next() {
                if (actual == null) {
                    throw new NoSuchElementException();
                }
                T valor = actual.valor;
                actual = actual.siguiente;
                return valor;
            }
        };
    }

    private static final class Nodo<T> {
        private T valor;
        private Nodo<T> siguiente;

        private Nodo(T valor) {
            this.valor = valor;
        }
    }
}
