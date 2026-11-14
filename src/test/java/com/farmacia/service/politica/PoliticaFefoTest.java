package com.farmacia.service.politica;

import com.farmacia.model.LoteDisponibleVista;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 003, US-5 (CA-5.1): la política FEFO decide la elegibilidad y el orden de los lotes. */
class PoliticaFefoTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);
    private final PoliticaSeleccionLotes politica = new PoliticaFefo();

    @Test
    @DisplayName("Un lote que vence hoy es elegible; uno vencido ayer no")
    void vigenteHastaElDiaDeVencimiento() {
        assertTrue(politica.esElegible(lote(1, HOY, 5), HOY));
        assertFalse(politica.esElegible(lote(2, HOY.minusDays(1), 5), HOY));
    }

    @Test
    @DisplayName("Un lote sin stock o sin fecha no es elegible")
    void sinStockOSinFechaNoEsElegible() {
        assertFalse(politica.esElegible(lote(1, HOY.plusDays(5), 0), HOY));
        assertFalse(politica.esElegible(lote(2, null, 5), HOY));
        assertFalse(politica.esElegible(null, HOY));
    }

    @Test
    @DisplayName("Ordena por vencimiento ascendente y, si empatan, por id_lote ascendente")
    void ordenFefoConDesempatePorIdLote() {
        var lotes = new ArrayList<>(List.of(
                lote(9, HOY.plusDays(30), 1),
                lote(4, HOY.plusDays(10), 1),
                lote(2, HOY.plusDays(30), 1)));

        lotes.sort(politica.orden());

        assertEquals(List.of(4, 2, 9), lotes.stream().map(LoteDisponibleVista::idLote).toList());
    }

    private static LoteDisponibleVista lote(int id, LocalDate vencimiento, int stock) {
        return new LoteDisponibleVista(id, 1, "MED-1", "Medicamento", "LT-" + id, vencimiento, stock, "CAJA");
    }
}
