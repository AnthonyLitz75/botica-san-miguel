package com.farmacia.estructuras;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;

/** Pila LIFO para acciones inversas de una preparación todavía no confirmada. */
public final class PilaDeshacer<T> {
    private final ArrayDeque<T> elementos = new ArrayDeque<>();

    public void apilar(T elemento) {
        elementos.push(Objects.requireNonNull(elemento, "El elemento de historial es obligatorio"));
    }

    public Optional<T> desapilar() {
        return elementos.isEmpty() ? Optional.empty() : Optional.of(elementos.pop());
    }

    public boolean estaVacia() {
        return elementos.isEmpty();
    }

    public int cantidad() {
        return elementos.size();
    }

    public void limpiar() {
        elementos.clear();
    }
}
