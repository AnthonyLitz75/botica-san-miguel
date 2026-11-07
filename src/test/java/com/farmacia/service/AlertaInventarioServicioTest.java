package com.farmacia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.farmacia.model.ResumenStockInventario;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.MedicamentoRepositorio;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertaInventarioServicioTest {
    private final MedicamentoRepositorio medicamentos = org.mockito.Mockito.mock(MedicamentoRepositorio.class);
    private final LoteRepositorio lotes = org.mockito.Mockito.mock(LoteRepositorio.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneId.of("UTC"));
    private final AlertaInventarioServicio servicio = new AlertaInventarioServicio(medicamentos, lotes, clock);

    @Test
    void separaAgotadosYStockBajoYCalculaReposicionDescontandoPendientes() {
        when(medicamentos.listarResumenStock(LocalDate.now(clock))).thenReturn(List.of(
                resumen(1, 0, 5, 0), resumen(2, 3, 5, 1),
                resumen(3, 4, 5, 2), resumen(4, 7, 5, 0)));

        var alertas = servicio.alertasStock();
        var reposicion = servicio.necesidadesReposicion();

        assertEquals(List.of("AGOTADO", "STOCK_BAJO", "STOCK_BAJO"),
                alertas.stream().map(a -> a.tipoAlerta()).toList());
        assertEquals(5, alertas.getFirst().cantidadSugerida());
        assertEquals(1, alertas.get(1).cantidadSugerida());
        assertEquals(0, alertas.get(2).cantidadSugerida());
        assertEquals(List.of(1, 2), reposicion.stream().map(a -> a.idMedicamento()).toList());
    }

    private ResumenStockInventario resumen(int id, int stock, int minimo, int pendiente) {
        return new ResumenStockInventario(id, "MED-" + id, "Medicamento " + id,
                "UNIDAD", minimo, stock, pendiente);
    }
}
