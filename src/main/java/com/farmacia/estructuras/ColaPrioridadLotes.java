package com.farmacia.estructuras;

import com.farmacia.model.LoteDisponibleVista;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Cola de prioridad de lotes sobre {@link PriorityQueue} (montículo binario).
 * El criterio de prioridad se recibe por constructor (lo aporta una política Strategy);
 * sin argumentos usa FEFO: vencimiento ascendente y, si empatan, {@code id_lote} ascendente.
 * Complejidad: agregar y sacar O(log n); consultar el tamaño O(1).
 */
public final class ColaPrioridadLotes {
    /** Orden FEFO por defecto. */
    public static final Comparator<LoteDisponibleVista> ORDEN_FEFO =
            Comparator.comparing(LoteDisponibleVista::fechaVencimiento)
                    .thenComparing(LoteDisponibleVista::idLote);

    private final PriorityQueue<LoteDisponibleVista> lotes;

    public ColaPrioridadLotes() {
        this(ORDEN_FEFO);
    }

    public ColaPrioridadLotes(Comparator<LoteDisponibleVista> orden) {
        this.lotes = new PriorityQueue<>(Objects.requireNonNull(orden, "El orden de la cola es obligatorio"));
    }

    public void agregar(LoteDisponibleVista lote) {
        if (lote == null || lote.idLote() == null || lote.fechaVencimiento() == null
                || lote.stockActual() == null || lote.stockActual() <= 0) {
            throw new IllegalArgumentException("Solo se pueden encolar lotes válidos con existencias");
        }
        lotes.add(lote);
    }

    public Optional<LoteDisponibleVista> sacarSiguiente() {
        return Optional.ofNullable(lotes.poll());
    }

    public boolean estaVacia() {
        return lotes.isEmpty();
    }

    public int cantidad() {
        return lotes.size();
    }
}
