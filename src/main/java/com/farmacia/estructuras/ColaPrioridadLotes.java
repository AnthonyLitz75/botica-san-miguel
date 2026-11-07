package com.farmacia.estructuras;

import com.farmacia.model.LoteDisponibleVista;

import java.util.Comparator;
import java.util.Optional;
import java.util.PriorityQueue;

/** Cola FEFO: devuelve primero el lote con vencimiento más cercano. */
public final class ColaPrioridadLotes {
    private static final Comparator<LoteDisponibleVista> ORDEN_FEFO =
            Comparator.comparing(LoteDisponibleVista::fechaVencimiento)
                    .thenComparing(LoteDisponibleVista::idLote);

    private final PriorityQueue<LoteDisponibleVista> lotes = new PriorityQueue<>(ORDEN_FEFO);

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
