package com.farmacia.service;

import com.farmacia.model.AlertaStockVista;
import com.farmacia.model.AlertaVencimientoVista;
import com.farmacia.model.PanelAlertasVista;
import com.farmacia.model.ResumenStockInventario;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.MedicamentoRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de AlertaInventarioServicio")
class AlertaInventarioServicioTest {

    @Mock
    private MedicamentoRepositorio medicamentos;

    @Mock
    private LoteRepositorio lotes;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneId.of("UTC"));
    private final LocalDate hoy = LocalDate.now(clock); // 2026-10-14
    private final LocalDate fechaHorizonte90Dias = hoy.plusDays(90); // 2027-01-12

    private AlertaInventarioServicio servicio;

    @BeforeEach
    void setUp() {
        servicio = new AlertaInventarioServicio(medicamentos, lotes, clock);
    }

    private ResumenStockInventario resumen(int id, int stock, int minimo, int pendiente) {
        return new ResumenStockInventario(
                id,
                "MED-" + id,
                "Medicamento " + id,
                "UNIDAD",
                minimo,
                stock,
                pendiente
        );
    }

    private AlertaVencimientoVista crearAlertaVencimiento(int idLote, int idMedicamento,
                                                          String numeroLote, LocalDate fechaVencimiento,
                                                          int stockActual, Long diasParaVencer, String tipo) {
        return new AlertaVencimientoVista(
                idLote,
                idMedicamento,
                "MED-" + idMedicamento,
                "Medicamento " + idMedicamento,
                numeroLote,
                fechaVencimiento,
                stockActual,
                "UNIDAD",
                diasParaVencer,
                tipo
        );
    }

    // =========================================================================
    // Prueba heredada preexistente (preservada intacta)
    // =========================================================================
    @Test
    @DisplayName("Prueba original: separa agotados y stock bajo, y calcula reposición descontando pendientes")
    void separaAgotadosYStockBajoYCalculaReposicionDescontandoPendientes() {
        when(medicamentos.listarResumenStock(hoy)).thenReturn(List.of(
                resumen(1, 0, 5, 0),
                resumen(2, 3, 5, 1),
                resumen(3, 4, 5, 2),
                resumen(4, 7, 5, 0)));

        var alertas = servicio.alertasStock();
        var reposicion = servicio.necesidadesReposicion();

        assertEquals(List.of("AGOTADO", "STOCK_BAJO", "STOCK_BAJO"),
                alertas.stream().map(AlertaStockVista::tipoAlerta).toList());
        assertEquals(5, alertas.getFirst().cantidadSugerida());
        assertEquals(1, alertas.get(1).cantidadSugerida());
        assertEquals(0, alertas.get(2).cantidadSugerida());
        assertEquals(List.of(1, 2), reposicion.stream().map(AlertaStockVista::idMedicamento).toList());
    }

    // =========================================================================
    // 1. Pruebas ampliadas de consultarAlertas()
    // =========================================================================
    @Nested
    @DisplayName("1. consultarAlertas()")
    class ConsultarAlertas {

        @Test
        @DisplayName("Debería coordinar y clasificar agotados, stock bajo, vencidos y por vencer a 90 días")
        void deberiaCoordinarYClasificarPanelAlertasIntegral() {
            // Dado
            when(medicamentos.listarResumenStock(hoy)).thenReturn(List.of(
                    resumen(1, 0, 10, 0),   // AGOTADO (stock 0 <= 10)
                    resumen(2, 4, 10, 2),   // STOCK_BAJO (stock 4 <= 10)
                    resumen(3, 15, 10, 0)   // NORMAL (stock 15 > 10, no es alerta)
            ));

            List<AlertaVencimientoVista> lotesVencidos = List.of(
                    crearAlertaVencimiento(101, 1, "L-VENC-1", hoy.minusDays(5), 20, -5L, "VENCIDO")
            );
            List<AlertaVencimientoVista> lotesPorVencer = List.of(
                    crearAlertaVencimiento(102, 2, "L-PROX-1", hoy.plusDays(30), 15, 30L, "POR_VENCER")
            );

            when(lotes.listarVencidos(hoy)).thenReturn(lotesVencidos);
            when(lotes.listarPorVencer(hoy, fechaHorizonte90Dias)).thenReturn(lotesPorVencer);

            // Cuando
            PanelAlertasVista panel = servicio.consultarAlertas();

            // Entonces
            assertNotNull(panel);

            // 1. Agotados
            assertEquals(1, panel.agotados().size());
            assertEquals(1, panel.agotados().get(0).idMedicamento());
            assertEquals("AGOTADO", panel.agotados().get(0).tipoAlerta());
            assertEquals(10, panel.agotados().get(0).cantidadSugerida()); // 10 - 0 - 0 = 10

            // 2. Stock Bajo
            assertEquals(1, panel.stockBajo().size());
            assertEquals(2, panel.stockBajo().get(0).idMedicamento());
            assertEquals("STOCK_BAJO", panel.stockBajo().get(0).tipoAlerta());
            assertEquals(4, panel.stockBajo().get(0).cantidadSugerida()); // 10 - 4 - 2 = 4

            // 3. Vencidos
            assertEquals(1, panel.vencidos().size());
            assertEquals("L-VENC-1", panel.vencidos().get(0).numeroLote());

            // 4. Por Vencer
            assertEquals(1, panel.porVencer().size());
            assertEquals("L-PROX-1", panel.porVencer().get(0).numeroLote());

            verify(medicamentos, times(1)).listarResumenStock(hoy);
            verify(lotes, times(1)).listarVencidos(hoy);
            verify(lotes, times(1)).listarPorVencer(hoy, fechaHorizonte90Dias);
            verifyNoMoreInteractions(medicamentos, lotes);
        }

        @Test
        @DisplayName("Debería retornar listas vacías en el panel cuando no existen alertas ni lotes críticos")
        void deberiaRetornarPanelConListasVaciasCuandoNoHayAlertas() {
            // Dado
            when(medicamentos.listarResumenStock(hoy)).thenReturn(List.of(
                    resumen(5, 50, 20, 0) // Stock saludable
            ));
            when(lotes.listarVencidos(hoy)).thenReturn(List.of());
            when(lotes.listarPorVencer(hoy, fechaHorizonte90Dias)).thenReturn(List.of());

            // Cuando
            PanelAlertasVista panel = servicio.consultarAlertas();

            // Entonces
            assertNotNull(panel);
            assertTrue(panel.agotados().isEmpty());
            assertTrue(panel.stockBajo().isEmpty());
            assertTrue(panel.vencidos().isEmpty());
            assertTrue(panel.porVencer().isEmpty());

            verify(medicamentos, times(1)).listarResumenStock(hoy);
            verify(lotes, times(1)).listarVencidos(hoy);
            verify(lotes, times(1)).listarPorVencer(hoy, fechaHorizonte90Dias);
            verifyNoMoreInteractions(medicamentos, lotes);
        }
    }

    // =========================================================================
    // 2. Pruebas ampliadas de alertasStock()
    // =========================================================================
    @Nested
    @DisplayName("2. alertasStock()")
    class AlertasStock {

        @Test
        @DisplayName("Debería incluir medicamentos donde stockDisponible == stockMinimo como STOCK_BAJO")
        void deberiaIncluirMedicamentoEnStockBajoCuandoStockEsIgualAlMinimo() {
            // Dado
            when(medicamentos.listarResumenStock(hoy)).thenReturn(List.of(
                    resumen(10, 5, 5, 0) // stock == minimo
            ));

            // Cuando
            List<AlertaStockVista> alertas = servicio.alertasStock();

            // Entonces
            assertEquals(1, alertas.size());
            assertEquals("STOCK_BAJO", alertas.get(0).tipoAlerta());
            assertEquals(0, alertas.get(0).cantidadSugerida()); // 5 - 5 - 0 = 0
            verify(medicamentos, times(1)).listarResumenStock(hoy);
            verifyNoMoreInteractions(medicamentos);
        }

        @Test
        @DisplayName("Debería excluir medicamentos con stock suficiente por encima del mínimo")
        void deberiaExcluirMedicamentosConStockSuficiente() {
            // Dado
            when(medicamentos.listarResumenStock(hoy)).thenReturn(List.of(
                    resumen(20, 100, 10, 0),
                    resumen(21, 11, 10, 0)
            ));

            // Cuando
            List<AlertaStockVista> alertas = servicio.alertasStock();

            // Entonces
            assertTrue(alertas.isEmpty());
            verify(medicamentos, times(1)).listarResumenStock(hoy);
            verifyNoMoreInteractions(medicamentos);
        }

        @Test
        @DisplayName("Debería retornar lista vacía cuando el catálogo no tiene medicamentos")
        void deberiaRetornarListaVaciaCuandoCatalogoEstaVacio() {
            // Dado
            when(medicamentos.listarResumenStock(hoy)).thenReturn(List.of());

            // Cuando
            List<AlertaStockVista> alertas = servicio.alertasStock();

            // Entonces
            assertTrue(alertas.isEmpty());
            verify(medicamentos, times(1)).listarResumenStock(hoy);
            verifyNoMoreInteractions(medicamentos);
        }
    }

    // =========================================================================
    // 3. Pruebas ampliadas de necesidadesReposicion()
    // =========================================================================
    @Nested
    @DisplayName("3. necesidadesReposicion()")
    class NecesidadesReposicion {

        @Test
        @DisplayName("Debería sugerir reposición cuando stockDisponible + pendienteRecibir < stockMinimo")
        void deberiaSugerirReposicionCuandoHayDeficitNeto() {
            // Dado
            when(medicamentos.listarResumenStock(hoy)).thenReturn(List.of(
                    resumen(1, 2, 10, 3) // Déficit: 10 - 2 - 3 = 5 > 0
            ));

            // Cuando
            List<AlertaStockVista> reposiciones = servicio.necesidadesReposicion();

            // Entonces
            assertEquals(1, reposiciones.size());
            assertEquals(1, reposiciones.get(0).idMedicamento());
            assertEquals(5, reposiciones.get(0).cantidadSugerida());
            verify(medicamentos, times(1)).listarResumenStock(hoy);
            verifyNoMoreInteractions(medicamentos);
        }

        @Test
        @DisplayName("Debería excluir medicamento cuando el pendiente de recibir cubre o supera el stock mínimo")
        void deberiaExcluirMedicamentoCuandoPendienteCubreDeficit() {
            // Dado: stockMinimo = 10, stockDisponible = 2, pendienteRecibir = 8 -> sugerida = max(0, 10-2-8) = 0
            //       stockMinimo = 10, stockDisponible = 0, pendienteRecibir = 15 -> sugerida = max(0, 10-0-15) = 0
            when(medicamentos.listarResumenStock(hoy)).thenReturn(List.of(
                    resumen(1, 2, 10, 8),
                    resumen(2, 0, 10, 15)
            ));

            // Cuando
            List<AlertaStockVista> reposiciones = servicio.necesidadesReposicion();

            // Entonces
            assertTrue(reposiciones.isEmpty());
            verify(medicamentos, times(1)).listarResumenStock(hoy);
            verifyNoMoreInteractions(medicamentos);
        }
    }

    // =========================================================================
    // 4. Pruebas de listarVencidos()
    // =========================================================================
    @Nested
    @DisplayName("4. listarVencidos()")
    class ListarVencidos {

        @Test
        @DisplayName("Debería consultar al repositorio con la fecha actual del Clock y retornar los lotes vencidos")
        void deberiaConsultarLotesVencidosConFechaActualDelClock() {
            // Dado
            List<AlertaVencimientoVista> esperados = List.of(
                    crearAlertaVencimiento(201, 5, "LOT-V01", hoy.minusDays(1), 10, -1L, "VENCIDO"),
                    crearAlertaVencimiento(202, 6, "LOT-V02", hoy.minusDays(30), 50, -30L, "VENCIDO")
            );
            when(lotes.listarVencidos(hoy)).thenReturn(esperados);

            // Cuando
            List<AlertaVencimientoVista> resultado = servicio.listarVencidos();

            // Entonces
            assertEquals(2, resultado.size());
            assertEquals("LOT-V01", resultado.get(0).numeroLote());
            assertEquals("LOT-V02", resultado.get(1).numeroLote());
            verify(lotes, times(1)).listarVencidos(hoy);
            verifyNoMoreInteractions(lotes);
        }

        @Test
        @DisplayName("Debería retornar lista vacía cuando no existen lotes vencidos")
        void deberiaRetornarListaVaciaCuandoNoHayLotesVencidos() {
            // Dado
            when(lotes.listarVencidos(hoy)).thenReturn(List.of());

            // Cuando
            List<AlertaVencimientoVista> resultado = servicio.listarVencidos();

            // Entonces
            assertTrue(resultado.isEmpty());
            verify(lotes, times(1)).listarVencidos(hoy);
            verifyNoMoreInteractions(lotes);
        }
    }

    // =========================================================================
    // 5. Pruebas de listarPorVencer()
    // =========================================================================
    @Nested
    @DisplayName("5. listarPorVencer()")
    class ListarPorVencer {

        @Test
        @DisplayName("Debería consultar lotes por vencer entre hoy y hoy + 90 días según horizonte reglamentario")
        void deberiaConsultarLotesPorVencerEnHorizonteDe90Dias() {
            // Dado
            List<AlertaVencimientoVista> esperados = List.of(
                    crearAlertaVencimiento(301, 7, "LOT-PV01", hoy.plusDays(15), 30, 15L, "POR_VENCER"),
                    crearAlertaVencimiento(302, 8, "LOT-PV02", hoy.plusDays(89), 40, 89L, "POR_VENCER")
            );
            when(lotes.listarPorVencer(hoy, fechaHorizonte90Dias)).thenReturn(esperados);

            // Cuando
            List<AlertaVencimientoVista> resultado = servicio.listarPorVencer();

            // Entonces
            assertEquals(2, resultado.size());
            assertEquals("LOT-PV01", resultado.get(0).numeroLote());
            assertEquals("LOT-PV02", resultado.get(1).numeroLote());
            verify(lotes, times(1)).listarPorVencer(hoy, fechaHorizonte90Dias);
            verifyNoMoreInteractions(lotes);
        }

        @Test
        @DisplayName("Debería retornar lista vacía cuando no existen lotes por vencer en el horizonte de 90 días")
        void deberiaRetornarListaVaciaCuandoNoHayLotesPorVencer() {
            // Dado
            when(lotes.listarPorVencer(hoy, fechaHorizonte90Dias)).thenReturn(List.of());

            // Cuando
            List<AlertaVencimientoVista> resultado = servicio.listarPorVencer();

            // Entonces
            assertTrue(resultado.isEmpty());
            verify(lotes, times(1)).listarPorVencer(hoy, fechaHorizonte90Dias);
            verifyNoMoreInteractions(lotes);
        }
    }
}
