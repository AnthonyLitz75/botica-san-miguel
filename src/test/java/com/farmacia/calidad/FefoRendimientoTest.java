package com.farmacia.calidad;

import com.farmacia.estructuras.ColaPrioridadLotes;
import com.farmacia.model.AsignacionLoteFefo;
import com.farmacia.model.LoteDisponibleVista;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.service.LotePrioridadServicio;
import com.farmacia.service.politica.PoliticaFefo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Spec 010 (US-4 / CA-4.3): Prueba de rendimiento del algoritmo FEFO.
 * Comprueba que la planificación FEFO con 200 lotes responde en menos de 500 ms.
 * Excluida de la suite por defecto mediante @Tag("rendimiento").
 */
@Tag("rendimiento")
class FefoRendimientoTest {

    @Test
    @DisplayName("CA-4.3: Planificación FEFO con 200 lotes responde en menos de 500 ms")
    void fefoCon200LotesMenorA500Ms() {
        int cantidadLotes = 200;
        int idMedicamento = 100;
        LocalDate hoy = LocalDate.now();

        // 1. Crear 200 lotes con fechas de vencimiento variables y stock
        List<LoteDisponibleVista> lotes = new ArrayList<>(cantidadLotes);
        for (int i = 1; i <= cantidadLotes; i++) {
            lotes.add(new LoteDisponibleVista(
                    i,
                    idMedicamento,
                    "MED-PERF",
                    "Medicamento Test Rendimiento",
                    "LT-PERF-" + i,
                    hoy.plusDays((i * 3) % 400 + 1), // Fechas desordenadas en el futuro
                    10 + (i % 20),
                    "CAJA"
            ));
        }

        var loteRepositorio = mock(LoteRepositorio.class);
        when(loteRepositorio.listarDisponiblesPorMedicamento(idMedicamento)).thenReturn(lotes);

        var servicio = new LotePrioridadServicio(loteRepositorio, Clock.systemDefaultZone(), new PoliticaFefo());

        // Calentamiento previo de la JVM (Warmup)
        for (int w = 0; w < 50; w++) {
            servicio.planificarSalida(idMedicamento, 50);
        }

        // 2. Medir tiempo de ejecución de planificarSalida extrayendo 500 unidades
        long inicioNano = System.nanoTime();
        List<AsignacionLoteFefo> asignaciones = servicio.planificarSalida(idMedicamento, 500);
        long duracionNano = System.nanoTime() - inicioNano;
        long duracionMs = duracionNano / 1_000_000;

        assertNotNull(asignaciones);
        assertFalse(asignaciones.isEmpty());
        int totalAsignado = asignaciones.stream().mapToInt(AsignacionLoteFefo::cantidadAsignada).sum();
        assertEquals(500, totalAsignado, "Debe haber asignado exactamente 500 unidades");

        // Verificar invariante FEFO: los lotes asignados deben estar estrictamente ordenados por fecha de vencimiento
        for (int i = 0; i < asignaciones.size() - 1; i++) {
            LocalDate actual = asignaciones.get(i).fechaVencimiento();
            LocalDate siguiente = asignaciones.get(i + 1).fechaVencimiento();
            assertFalse(actual.isAfter(siguiente),
                    "Violación FEFO: el lote " + asignaciones.get(i).numeroLote() + " (" + actual + ") vence después que "
                            + asignaciones.get(i + 1).numeroLote() + " (" + siguiente + ")");
        }

        // 3. Verificar objetivo CA-4.3: duración < 500 ms
        assertTrue(duracionMs < 500,
                "La planificación FEFO con 200 lotes tardó " + duracionMs + " ms (límite: 500 ms)");

        // 4. Medir rendimiento directo de la estructura artesanal ColaPrioridadLotes
        long inicioCola = System.nanoTime();
        var cola = new ColaPrioridadLotes(Comparator.comparing(LoteDisponibleVista::fechaVencimiento));
        for (var l : lotes) {
            cola.agregar(l);
        }
        int extraidos = 0;
        while (!cola.estaVacia() && extraidos < 50) {
            cola.sacarSiguiente();
            extraidos++;
        }
        long duracionColaMs = (System.nanoTime() - inicioCola) / 1_000_000;

        assertTrue(duracionColaMs < 500,
                "La estructura ColaPrioridadLotes tardó " + duracionColaMs + " ms (límite: 500 ms)");
    }
}
