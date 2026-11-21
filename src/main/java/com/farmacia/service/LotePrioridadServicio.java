package com.farmacia.service;

import com.farmacia.estructuras.ColaPrioridadLotes;
import com.farmacia.model.AsignacionLoteFefo;
import com.farmacia.model.Lote;
import com.farmacia.model.LoteDisponibleVista;
import com.farmacia.model.LoteFilaVista;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.service.politica.PoliticaSeleccionLotes;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    public List<LoteFilaVista> calcularFilasLotes(List<Lote> lotes) {
        return calcularFilasLotes(lotes, LocalDate.now(clock));
    }

    public List<LoteFilaVista> calcularFilasLotes(List<Lote> lotes, LocalDate hoy) {
        if (lotes == null || lotes.isEmpty()) {
            return List.of();
        }

        // 1. Filtrar los lotes elegibles según la política y vigencia
        List<LoteDisponibleVista> elegibles = new ArrayList<>();
        for (var lote : lotes) {
            if (lote != null && lote.idLote() != null && lote.fechaVencimiento() != null
                    && lote.stockActual() != null && lote.stockActual() > 0) {
                var vista = new LoteDisponibleVista(
                        lote.idLote(),
                        lote.idMedicamento(),
                        "",
                        "",
                        lote.numeroLote(),
                        lote.fechaVencimiento(),
                        lote.stockActual(),
                        ""
                );
                if (politica.esElegible(vista, hoy)) {
                    elegibles.add(vista);
                }
            }
        }

        record InfoCola(boolean tieneBadge, String textoPrioridad, String textoPosicion) {}
        Map<Integer, InfoCola> mapaPrioridad = new HashMap<>();

        if (elegibles.size() == 1) {
            // Un solo lote vigente con stock: "Lote único" sin badge
            mapaPrioridad.put(elegibles.get(0).idLote(), new InfoCola(false, "Lote único", null));
        } else if (elegibles.size() >= 2) {
            // Dos o más lotes: encolar en ColaPrioridadLotes según la política (FEFO)
            ColaPrioridadLotes cola = new ColaPrioridadLotes(politica.orden());
            for (var elegible : elegibles) {
                cola.agregar(elegible);
            }

            int posicion = 1;
            while (!cola.estaVacia()) {
                var loteOrdenado = cola.sacarSiguiente().orElseThrow();
                if (posicion == 1) {
                    mapaPrioridad.put(loteOrdenado.idLote(), new InfoCola(true, "Sale primero", "1.º en la cola"));
                } else {
                    String texto = posicion + ".º en la cola";
                    mapaPrioridad.put(loteOrdenado.idLote(), new InfoCola(false, texto, texto));
                }
                posicion++;
            }
        }

        // 2. Mapear respetando el orden original de la lista recibida
        List<LoteFilaVista> resultado = new ArrayList<>(lotes.size());
        for (var lote : lotes) {
            if (lote == null) continue;
            InfoCola info = mapaPrioridad.get(lote.idLote());
            if (info != null) {
                resultado.add(new LoteFilaVista(
                        lote.idLote(),
                        lote.idMedicamento(),
                        lote.numeroLote(),
                        lote.fechaVencimiento(),
                        lote.stockActual(),
                        info.tieneBadge(),
                        info.textoPrioridad(),
                        info.textoPosicion()
                ));
            } else {
                resultado.add(new LoteFilaVista(
                        lote.idLote(),
                        lote.idMedicamento(),
                        lote.numeroLote(),
                        lote.fechaVencimiento(),
                        lote.stockActual(),
                        false,
                        "—",
                        null
                ));
            }
        }
        return List.copyOf(resultado);
    }
}
