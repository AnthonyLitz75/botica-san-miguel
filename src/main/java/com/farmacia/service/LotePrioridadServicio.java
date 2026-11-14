package com.farmacia.service;

import com.farmacia.estructuras.ColaPrioridadLotes;
import com.farmacia.model.AsignacionLoteFefo;
import com.farmacia.model.LoteDisponibleVista;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.service.politica.PoliticaSeleccionLotes;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Service
public class LotePrioridadServicio {
    private final LoteRepositorio repositorio;
    private final Clock clock;
    private final PoliticaSeleccionLotes politica;

    /**
     * @param politica regla de selección de lotes (Strategy); en producción, {@code PoliticaFefo}
     */
    public LotePrioridadServicio(LoteRepositorio repositorio, Clock clock, PoliticaSeleccionLotes politica) {
        this.repositorio = repositorio;
        this.clock = clock;
        this.politica = politica;
    }

    public List<AsignacionLoteFefo> planificarSalida(Integer idMedicamento, Integer cantidad) {
        if (idMedicamento == null || idMedicamento <= 0) {
            throw new IllegalArgumentException("Selecciona un medicamento válido");
        }
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }

        var cola = new ColaPrioridadLotes(politica.orden());
        LocalDate hoy = LocalDate.now(clock);
        for (var lote : repositorio.listarDisponiblesPorMedicamento(idMedicamento)) {
            if (idMedicamento.equals(lote.idMedicamento()) && politica.esElegible(lote, hoy)) {
                cola.agregar(lote);
            }
        }

        int pendiente = cantidad;
        List<AsignacionLoteFefo> asignaciones = new ArrayList<>();
        while (pendiente > 0 && !cola.estaVacia()) {
            var lote = cola.sacarSiguiente().orElseThrow();
            int asignada = Math.min(pendiente, lote.stockActual());
            asignaciones.add(new AsignacionLoteFefo(lote.idLote(), lote.numeroLote(),
                    lote.fechaVencimiento(), lote.stockActual(), asignada));
            pendiente -= asignada;
        }
        if (pendiente > 0) {
            throw new IllegalArgumentException("No hay existencias vigentes suficientes para preparar la cantidad solicitada");
        }
        return List.copyOf(asignaciones);
    }

    /** Devuelve el lote prioritario de cada medicamento usando la misma política y cola de las salidas. */
    public Set<Integer> identificarLotesPrioritarios(List<LoteDisponibleVista> disponibles) {
        LocalDate hoy = LocalDate.now(clock);
        var colasPorMedicamento = new HashMap<Integer, ColaPrioridadLotes>();
        for (var lote : disponibles) {
            if (lote != null && lote.idMedicamento() != null && politica.esElegible(lote, hoy)) {
                colasPorMedicamento.computeIfAbsent(lote.idMedicamento(),
                                ignorado -> new ColaPrioridadLotes(politica.orden()))
                        .agregar(lote);
            }
        }
        Set<Integer> prioritarios = new HashSet<>();
        for (var cola : colasPorMedicamento.values()) {
            cola.sacarSiguiente().map(LoteDisponibleVista::idLote).ifPresent(prioritarios::add);
        }
        return Set.copyOf(prioritarios);
    }
}
