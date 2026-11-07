package com.farmacia.service;

import com.farmacia.model.LoteDisponibleVista;
import java.util.Set;
import com.farmacia.repository.LoteRepositorio;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LotePrioridadServicioTest {
    @Test
    void reparteLaCantidadEnOrdenFefoYOmiteLotesVencidos() {
        var repositorio = mock(LoteRepositorio.class);
        LocalDate hoy = LocalDate.now();
        when(repositorio.listarDisponiblesPorMedicamento(4)).thenReturn(List.of(
                lote(1, hoy.minusDays(1), 100), lote(3, hoy.plusDays(10), 2), lote(2, hoy.plusDays(30), 8)));
        var asignaciones = new LotePrioridadServicio(repositorio).planificarSalida(4, 7);
        assertEquals(2, asignaciones.size());
        assertEquals(3, asignaciones.get(0).idLote());
        assertEquals(2, asignaciones.get(0).cantidadAsignada());
        assertEquals(2, asignaciones.get(1).idLote());
        assertEquals(5, asignaciones.get(1).cantidadAsignada());
    }

    @Test
    void rechazaCantidadQueSuperaStockNoVencido() {
        var repositorio = mock(LoteRepositorio.class);
        when(repositorio.listarDisponiblesPorMedicamento(4)).thenReturn(List.of(lote(1, LocalDate.now().plusDays(2), 2)));
        assertThrows(IllegalArgumentException.class, () -> new LotePrioridadServicio(repositorio).planificarSalida(4, 3));
    }

    @Test
    void validaCantidadSolicitada() {
        assertThrows(IllegalArgumentException.class, () -> new LotePrioridadServicio(mock(LoteRepositorio.class)).planificarSalida(4, 0));
    }

    @Test
    void identificaUnLotePrioritarioVigentePorMedicamento() {
        LocalDate hoy = LocalDate.now();
        var servicio = new LotePrioridadServicio(mock(LoteRepositorio.class));
        var disponibles = List.of(
                lote(1, 4, hoy.plusDays(30), 10),
                lote(2, 4, hoy.plusDays(10), 3),
                lote(3, 4, hoy.minusDays(1), 20),
                lote(4, 9, hoy.plusDays(5), 6));

        assertEquals(Set.of(2, 4), servicio.identificarLotesPrioritarios(disponibles));
    }

    private LoteDisponibleVista lote(int id, LocalDate vencimiento, int stock) {
        return lote(id, 4, vencimiento, stock);
    }

    private LoteDisponibleVista lote(int id, int medicamento, LocalDate vencimiento, int stock) {
        return new LoteDisponibleVista(id, medicamento, "MED-" + medicamento, "Medicamento", "LT-" + id,
                vencimiento, stock, "UNIDAD");
    }
}
