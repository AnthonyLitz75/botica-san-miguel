package com.farmacia.estructuras;

import com.farmacia.model.LoteDisponibleVista;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ColaPrioridadLotesTest {
    @Test
    void extraePorVencimientoYDesempataPorIdDeLote() {
        var cola = new ColaPrioridadLotes();
        cola.agregar(lote(9, LocalDate.parse("2028-01-01"), 10));
        cola.agregar(lote(7, LocalDate.parse("2027-01-01"), 10));
        cola.agregar(lote(3, LocalDate.parse("2027-01-01"), 10));
        assertEquals(3, cola.sacarSiguiente().orElseThrow().idLote());
        assertEquals(7, cola.sacarSiguiente().orElseThrow().idLote());
        assertEquals(9, cola.sacarSiguiente().orElseThrow().idLote());
        assertTrue(cola.estaVacia());
    }

    @Test
    void rechazaLotesNulosOSinStock() {
        var cola = new ColaPrioridadLotes();
        assertThrows(IllegalArgumentException.class, () -> cola.agregar(null));
        assertThrows(IllegalArgumentException.class, () -> cola.agregar(lote(1, LocalDate.now().plusDays(1), 0)));
    }

    private LoteDisponibleVista lote(int id, LocalDate vencimiento, int stock) {
        return new LoteDisponibleVista(id, 10, "MED-10", "Medicamento", "LT-" + id, vencimiento, stock, "UNIDAD");
    }
}
