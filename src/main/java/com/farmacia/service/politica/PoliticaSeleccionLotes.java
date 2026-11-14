package com.farmacia.service.politica;

import com.farmacia.model.LoteDisponibleVista;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Patrón Strategy: regla que decide qué lotes pueden salir y en qué orden.
 * La estructura de datos ({@code ColaPrioridadLotes}, un montículo binario) aporta el orden eficiente;
 * esta política aporta el criterio. Cambiar la regla no exige modificar los servicios que la usan.
 */
public interface PoliticaSeleccionLotes {

    /** Indica si el lote puede asignarse a una salida en la fecha {@code hoy}. */
    boolean esElegible(LoteDisponibleVista lote, LocalDate hoy);

    /** Orden de prioridad: el primer elemento según este comparador sale primero. */
    Comparator<LoteDisponibleVista> orden();
}
