package com.farmacia.service.politica;

import com.farmacia.estructuras.ColaPrioridadLotes;
import com.farmacia.model.LoteDisponibleVista;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * FEFO (First Expired, First Out): sale primero el lote que vence antes; si empatan, el de menor
 * {@code id_lote}. Un lote es vigente hasta el final de su día de vencimiento y debe tener stock.
 */
@Component
public class PoliticaFefo implements PoliticaSeleccionLotes {

    @Override
    public boolean esElegible(LoteDisponibleVista lote, LocalDate hoy) {
        return lote != null
                && lote.idLote() != null
                && lote.fechaVencimiento() != null
                && !lote.fechaVencimiento().isBefore(hoy)
                && lote.stockActual() != null
                && lote.stockActual() > 0;
    }

    @Override
    public Comparator<LoteDisponibleVista> orden() {
        return ColaPrioridadLotes.ORDEN_FEFO;
    }
}
