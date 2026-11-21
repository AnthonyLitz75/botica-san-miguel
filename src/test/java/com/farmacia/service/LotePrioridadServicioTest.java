package com.farmacia.service;

import com.farmacia.model.Lote;
import com.farmacia.model.LoteDisponibleVista;
import com.farmacia.model.LoteFilaVista;
import com.farmacia.service.politica.PoliticaFefo;
import com.farmacia.service.politica.PoliticaSeleccionLotes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import com.farmacia.repository.LoteRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LotePrioridadServicioTest {
    /** Reloj fijo en 2026-11-10: las reglas de vigencia no dependen de la fecha real. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    @Test
    void reparteLaCantidadEnOrdenFefoYOmiteLotesVencidos() {
        var repositorio = mock(LoteRepositorio.class);
        when(repositorio.listarDisponiblesPorMedicamento(4)).thenReturn(List.of(
                lote(1, HOY.minusDays(1), 100), lote(3, HOY.plusDays(10), 2), lote(2, HOY.plusDays(30), 8)));
        var asignaciones = new LotePrioridadServicio(repositorio, RELOJ, new PoliticaFefo()).planificarSalida(4, 7);
        assertEquals(2, asignaciones.size());
        assertEquals(3, asignaciones.get(0).idLote());
        assertEquals(2, asignaciones.get(0).cantidadAsignada());
        assertEquals(2, asignaciones.get(1).idLote());
        assertEquals(5, asignaciones.get(1).cantidadAsignada());
    }

    @Test
    void rechazaCantidadQueSuperaStockNoVencido() {
        var repositorio = mock(LoteRepositorio.class);
        when(repositorio.listarDisponiblesPorMedicamento(4)).thenReturn(List.of(lote(1, HOY.plusDays(2), 2)));
        assertThrows(IllegalArgumentException.class, () -> new LotePrioridadServicio(repositorio, RELOJ, new PoliticaFefo()).planificarSalida(4, 3));
    }

    @Test
    void validaCantidadSolicitada() {
        assertThrows(IllegalArgumentException.class,
                () -> new LotePrioridadServicio(mock(LoteRepositorio.class), RELOJ, new PoliticaFefo()).planificarSalida(4, 0));
    }

    @Test
    void identificaUnLotePrioritarioVigentePorMedicamento() {
        var servicio = new LotePrioridadServicio(mock(LoteRepositorio.class), RELOJ, new PoliticaFefo());
        var disponibles = List.of(
                lote(1, 4, HOY.plusDays(30), 10),
                lote(2, 4, HOY.plusDays(10), 3),
                lote(3, 4, HOY.minusDays(1), 20),
                lote(4, 9, HOY.plusDays(5), 6));

        assertEquals(Set.of(2, 4), servicio.identificarLotesPrioritarios(disponibles));
    }

    @Test
    @DisplayName("CA-5.1: con el reloj en 2026-11-10, el lote que venció el 09 se excluye y el que vence el 10 se usa")
    void elRelojInyectadoDefineLaVigenciaDelLote() {
        var repositorio = mock(LoteRepositorio.class);
        when(repositorio.listarDisponiblesPorMedicamento(4)).thenReturn(List.of(
                lote(1, LocalDate.of(2026, 11, 9), 50),
                lote(2, LocalDate.of(2026, 11, 10), 5)));

        var asignaciones = new LotePrioridadServicio(repositorio, RELOJ, new PoliticaFefo()).planificarSalida(4, 5);

        assertEquals(1, asignaciones.size());
        assertEquals(2, asignaciones.getFirst().idLote(), "Un lote vence al final de su día: hoy aún es vigente");
        assertThrows(IllegalArgumentException.class,
                () -> new LotePrioridadServicio(repositorio, RELOJ, new PoliticaFefo()).planificarSalida(4, 6),
                "El lote vencido ayer no debe completar la cantidad");
    }

    @Test
    @DisplayName("CA-5.1: el lote prioritario también respeta el reloj inyectado")
    void lotePrioritarioRespetaElRelojInyectado() {
        var servicio = new LotePrioridadServicio(mock(LoteRepositorio.class), RELOJ, new PoliticaFefo());
        var disponibles = List.of(
                lote(1, 4, LocalDate.of(2026, 11, 9), 10),
                lote(2, 4, LocalDate.of(2026, 11, 10), 3));

        assertEquals(Set.of(2), servicio.identificarLotesPrioritarios(disponibles));
    }

    @Test
    @DisplayName("CA-5.4 (spec 003): otra política cambia la asignación sin modificar el servicio (Strategy)")
    void otraPoliticaCambiaLaAsignacionSinTocarElServicio() {
        // Política de prueba: "mayor stock primero", misma regla de vigencia que FEFO.
        PoliticaSeleccionLotes mayorStockPrimero = new PoliticaSeleccionLotes() {
            private final PoliticaFefo fefo = new PoliticaFefo();

            @Override
            public boolean esElegible(LoteDisponibleVista lote, LocalDate hoy) {
                return fefo.esElegible(lote, hoy);
            }

            @Override
            public java.util.Comparator<LoteDisponibleVista> orden() {
                return java.util.Comparator.comparing(LoteDisponibleVista::stockActual).reversed()
                        .thenComparing(LoteDisponibleVista::idLote);
            }
        };
        var repositorio = mock(LoteRepositorio.class);
        when(repositorio.listarDisponiblesPorMedicamento(4)).thenReturn(List.of(
                lote(3, HOY.plusDays(10), 2), lote(2, HOY.plusDays(30), 8)));

        var conFefo = new LotePrioridadServicio(repositorio, RELOJ, new PoliticaFefo()).planificarSalida(4, 3);
        var conOtra = new LotePrioridadServicio(repositorio, RELOJ, mayorStockPrimero).planificarSalida(4, 3);

        assertEquals(3, conFefo.getFirst().idLote(), "FEFO toma primero el lote que vence antes");
        assertEquals(2, conOtra.getFirst().idLote(), "La otra política toma primero el lote con más stock");
        assertEquals(1, conOtra.size());
    }

    private LoteDisponibleVista lote(int id, LocalDate vencimiento, int stock) {
        return lote(id, 4, vencimiento, stock);
    }

    private LoteDisponibleVista lote(int id, int medicamento, LocalDate vencimiento, int stock) {
        return new LoteDisponibleVista(id, medicamento, "MED-" + medicamento, "Medicamento", "LT-" + id,
                vencimiento, stock, "UNIDAD");
    }

    private Lote loteModel(int id, LocalDate vencimiento, int stock) {
        return new Lote(id, 4, "LT-" + id, vencimiento, stock);
    }

    @Test
    @DisplayName("a) Lote único vigente con stock: muestra 'Lote único', sin badge")
    void calcularFilasLotes_loteUnicoVigente_muestraLoteUnicoSinBadge() {
        var servicio = new LotePrioridadServicio(mock(LoteRepositorio.class), RELOJ, new PoliticaFefo());
        var lotes = List.of(loteModel(1, HOY.plusDays(30), 10));

        var filas = servicio.calcularFilasLotes(lotes);

        assertEquals(1, filas.size());
        assertFalse(filas.get(0).tieneBadgeSalePrimero());
        assertEquals("Lote único", filas.get(0).textoPrioridad());
        assertNull(filas.get(0).textoPosicionCola());
    }

    @Test
    @DisplayName("b) 2+ lotes vigentes: primero con badge 'Sale primero' y posiciones 1.º/2.º/3.º")
    void calcularFilasLotes_multiplesLotesVigentes_primeroSalePrimeroYPosiciones() {
        var servicio = new LotePrioridadServicio(mock(LoteRepositorio.class), RELOJ, new PoliticaFefo());
        var lotes = List.of(
                loteModel(1, HOY.plusDays(60), 20),
                loteModel(2, HOY.plusDays(10), 15),
                loteModel(3, HOY.plusDays(90), 5));

        var filas = servicio.calcularFilasLotes(lotes);

        assertEquals(3, filas.size());
        // Fila 0 corresponde a lote 1 (orden original preservado): es 2.º en la cola
        assertEquals(1, filas.get(0).idLote());
        assertFalse(filas.get(0).tieneBadgeSalePrimero());
        assertEquals("2.º en la cola", filas.get(0).textoPrioridad());
        assertEquals("2.º en la cola", filas.get(0).textoPosicionCola());

        // Fila 1 corresponde a lote 2 (vence antes): es 1.º en la cola, con badge "Sale primero"
        assertEquals(2, filas.get(1).idLote());
        assertTrue(filas.get(1).tieneBadgeSalePrimero());
        assertEquals("Sale primero", filas.get(1).textoPrioridad());
        assertEquals("1.º en la cola", filas.get(1).textoPosicionCola());

        // Fila 2 corresponde a lote 3: es 3.º en la cola
        assertEquals(3, filas.get(2).idLote());
        assertFalse(filas.get(2).tieneBadgeSalePrimero());
        assertEquals("3.º en la cola", filas.get(2).textoPrioridad());
        assertEquals("3.º en la cola", filas.get(2).textoPosicionCola());
    }

    @Test
    @DisplayName("c) Lote con stock 0: muestra '—' y no cuenta en la cola")
    void calcularFilasLotes_loteConStockCero_muestraGuionYNoCuenta() {
        var servicio = new LotePrioridadServicio(mock(LoteRepositorio.class), RELOJ, new PoliticaFefo());
        var lotes = List.of(
                loteModel(1, HOY.plusDays(30), 10),
                loteModel(2, HOY.plusDays(10), 0));

        var filas = servicio.calcularFilasLotes(lotes);

        assertEquals(2, filas.size());
        // Lote 1 es el único vigente con stock: "Lote único"
        assertEquals(1, filas.get(0).idLote());
        assertFalse(filas.get(0).tieneBadgeSalePrimero());
        assertEquals("Lote único", filas.get(0).textoPrioridad());

        // Lote 2 con stock 0: muestra "—"
        assertEquals(2, filas.get(1).idLote());
        assertFalse(filas.get(1).tieneBadgeSalePrimero());
        assertEquals("—", filas.get(1).textoPrioridad());
        assertNull(filas.get(1).textoPosicionCola());
    }

    @Test
    @DisplayName("d) Lote vencido: muestra '—' y no cuenta en la cola")
    void calcularFilasLotes_loteVencido_muestraGuionYNoCuenta() {
        var servicio = new LotePrioridadServicio(mock(LoteRepositorio.class), RELOJ, new PoliticaFefo());
        var lotes = List.of(
                loteModel(1, HOY.minusDays(1), 50),
                loteModel(2, HOY.plusDays(20), 10));

        var filas = servicio.calcularFilasLotes(lotes);

        assertEquals(2, filas.size());
        // Lote 1 vencido: muestra "—"
        assertEquals(1, filas.get(0).idLote());
        assertFalse(filas.get(0).tieneBadgeSalePrimero());
        assertEquals("—", filas.get(0).textoPrioridad());
        assertNull(filas.get(0).textoPosicionCola());

        // Lote 2 es el único vigente con stock: "Lote único"
        assertEquals(2, filas.get(1).idLote());
        assertFalse(filas.get(1).tieneBadgeSalePrimero());
        assertEquals("Lote único", filas.get(1).textoPrioridad());
    }

    @Test
    @DisplayName("e) Criterio FEFO: desempata por menor id_lote cuando la fecha de vencimiento es igual")
    void calcularFilasLotes_empateFechaVencimiento_desempataPorIdLote() {
        var servicio = new LotePrioridadServicio(mock(LoteRepositorio.class), RELOJ, new PoliticaFefo());
        var lotes = List.of(
                loteModel(5, HOY.plusDays(10), 10),
                loteModel(2, HOY.plusDays(10), 10));

        var filas = servicio.calcularFilasLotes(lotes);

        assertEquals(2, filas.size());
        // Lote 5 tiene id 5 > 2: 2.º en la cola
        assertEquals(5, filas.get(0).idLote());
        assertFalse(filas.get(0).tieneBadgeSalePrimero());
        assertEquals("2.º en la cola", filas.get(0).textoPrioridad());

        // Lote 2 tiene id 2 < 5: 1.º en la cola con badge "Sale primero"
        assertEquals(2, filas.get(1).idLote());
        assertTrue(filas.get(1).tieneBadgeSalePrimero());
        assertEquals("Sale primero", filas.get(1).textoPrioridad());
        assertEquals("1.º en la cola", filas.get(1).textoPosicionCola());
    }
}
