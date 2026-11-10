package com.farmacia.service;

import com.farmacia.model.Lote;
import com.farmacia.model.LoteDisponibleVista;
import com.farmacia.repository.LoteRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas unitarias de LoteServicio")
class LoteServicioTest {

    @Mock
    private LoteRepositorio repositorio;

    private LoteServicio servicio;

    @BeforeEach
    void setUp() {
        servicio = new LoteServicio(repositorio);
    }

    private Lote crearLote(Integer idLote, Integer idMedicamento, String numeroLote,
                           LocalDate fechaVencimiento, Integer stockActual) {
        return new Lote(
                idLote,
                idMedicamento,
                numeroLote,
                fechaVencimiento,
                stockActual
        );
    }

    private LoteDisponibleVista crearLoteDisponibleVista(Integer idLote, Integer idMedicamento,
                                                         String codigoMedicamento, String nombreMedicamento,
                                                         String numeroLote, LocalDate fechaVencimiento,
                                                         Integer stockActual, String unidadControl) {
        return new LoteDisponibleVista(
                idLote,
                idMedicamento,
                codigoMedicamento,
                nombreMedicamento,
                numeroLote,
                fechaVencimiento,
                stockActual,
                unidadControl
        );
    }

    // =========================================================================
    // 1. Pruebas de listarPorMedicamento(Integer idMedicamento)
    // =========================================================================
    @Nested
    @DisplayName("1. listarPorMedicamento(Integer idMedicamento)")
    class ListarPorMedicamento {

        @Test
        @DisplayName("Debería retornar lista de lotes asociados al medicamento ordenados por vencimiento")
        void deberiaRetornarLotesDelMedicamentoCuandoExistenEnRepositorio() {
            // Dado
            Integer idMedicamento = 1;
            List<Lote> esperados = List.of(
                    crearLote(10, idMedicamento, "LOT-2026-001", LocalDate.of(2026, 12, 31), 40),
                    crearLote(11, idMedicamento, "LOT-2027-002", LocalDate.of(2027, 6, 30), 60)
            );
            when(repositorio.listarPorMedicamento(idMedicamento)).thenReturn(esperados);

            // Cuando
            List<Lote> resultado = servicio.listarPorMedicamento(idMedicamento);

            // Entonces
            assertNotNull(resultado);
            assertEquals(2, resultado.size());
            assertEquals("LOT-2026-001", resultado.get(0).numeroLote());
            assertEquals("LOT-2027-002", resultado.get(1).numeroLote());
            verify(repositorio, times(1)).listarPorMedicamento(idMedicamento);
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería retornar lista vacía cuando el medicamento no tiene lotes registrados")
        void deberiaRetornarListaVaciaCuandoMedicamentoNoTieneLotes() {
            // Dado
            Integer idMedicamentoSinLotes = 99;
            when(repositorio.listarPorMedicamento(idMedicamentoSinLotes)).thenReturn(List.of());

            // Cuando
            List<Lote> resultado = servicio.listarPorMedicamento(idMedicamentoSinLotes);

            // Entonces
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(repositorio, times(1)).listarPorMedicamento(idMedicamentoSinLotes);
            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 2. Pruebas de buscarPorId(Integer idLote)
    // =========================================================================
    @Nested
    @DisplayName("2. buscarPorId(Integer idLote)")
    class BuscarPorId {

        @Test
        @DisplayName("Debería retornar Optional con el lote cuando el ID de lote existe")
        void deberiaRetornarOptionalConLoteCuandoIdExiste() {
            // Dado
            Integer idLote = 15;
            Lote loteEsperado = crearLote(15, 2, "LOT-B15", LocalDate.of(2027, 3, 15), 100);
            when(repositorio.buscarPorId(idLote)).thenReturn(Optional.of(loteEsperado));

            // Cuando
            Optional<Lote> resultado = servicio.buscarPorId(idLote);

            // Entonces
            assertTrue(resultado.isPresent());
            assertEquals(15, resultado.get().idLote());
            assertEquals("LOT-B15", resultado.get().numeroLote());
            assertEquals(100, resultado.get().stockActual());
            verify(repositorio, times(1)).buscarPorId(idLote);
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería retornar Optional vacío cuando el ID de lote no existe")
        void deberiaRetornarOptionalVacioCuandoIdNoExiste() {
            // Dado
            Integer idLoteInexistente = 999;
            when(repositorio.buscarPorId(idLoteInexistente)).thenReturn(Optional.empty());

            // Cuando
            Optional<Lote> resultado = servicio.buscarPorId(idLoteInexistente);

            // Entonces
            assertTrue(resultado.isEmpty());
            verify(repositorio, times(1)).buscarPorId(idLoteInexistente);
            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 3. Pruebas de listarDisponiblesConMedicamento()
    // =========================================================================
    @Nested
    @DisplayName("3. listarDisponiblesConMedicamento()")
    class ListarDisponiblesConMedicamento {

        @Test
        @DisplayName("Debería retornar lista de proyecciones LoteDisponibleVista con stock disponible")
        void deberiaRetornarListaDeLotesDisponiblesConDatosDeMedicamento() {
            // Dado
            List<LoteDisponibleVista> esperados = List.of(
                    crearLoteDisponibleVista(1, 1, "MED-001", "Paracetamol", "L-001",
                            LocalDate.of(2026, 10, 1), 50, "CAJA"),
                    crearLoteDisponibleVista(2, 2, "MED-002", "Amoxicilina", "L-002",
                            LocalDate.of(2026, 11, 15), 30, "CAJA")
            );
            when(repositorio.listarDisponiblesConMedicamento()).thenReturn(esperados);

            // Cuando
            List<LoteDisponibleVista> resultado = servicio.listarDisponiblesConMedicamento();

            // Entonces
            assertNotNull(resultado);
            assertEquals(2, resultado.size());
            assertEquals("MED-001", resultado.get(0).codigoMedicamento());
            assertEquals("Paracetamol", resultado.get(0).nombreMedicamento());
            assertEquals(50, resultado.get(0).stockActual());
            assertEquals("MED-002", resultado.get(1).codigoMedicamento());
            verify(repositorio, times(1)).listarDisponiblesConMedicamento();
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería retornar lista vacía cuando no existen lotes disponibles en inventario")
        void deberiaRetornarListaVaciaCuandoNoHayLotesDisponibles() {
            // Dado
            when(repositorio.listarDisponiblesConMedicamento()).thenReturn(List.of());

            // Cuando
            List<LoteDisponibleVista> resultado = servicio.listarDisponiblesConMedicamento();

            // Entonces
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(repositorio, times(1)).listarDisponiblesConMedicamento();
            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 4. Pruebas de listarDisponiblesPorMedicamento(Integer idMedicamento)
    // =========================================================================
    @Nested
    @DisplayName("4. listarDisponiblesPorMedicamento(Integer idMedicamento)")
    class ListarDisponiblesPorMedicamento {

        @Test
        @DisplayName("Debería retornar lotes disponibles filtrados específicamente para el medicamento solicitado")
        void deberiaRetornarLotesDisponiblesFiltradosPorMedicamento() {
            // Dado
            Integer idMedicamento = 3;
            List<LoteDisponibleVista> esperados = List.of(
                    crearLoteDisponibleVista(5, idMedicamento, "MED-003", "Ibuprofeno", "L-IBU-1",
                            LocalDate.of(2027, 1, 20), 25, "CAJA")
            );
            when(repositorio.listarDisponiblesPorMedicamento(idMedicamento)).thenReturn(esperados);

            // Cuando
            List<LoteDisponibleVista> resultado = servicio.listarDisponiblesPorMedicamento(idMedicamento);

            // Entonces
            assertNotNull(resultado);
            assertEquals(1, resultado.size());
            assertEquals(idMedicamento, resultado.get(0).idMedicamento());
            assertEquals("Ibuprofeno", resultado.get(0).nombreMedicamento());
            assertEquals(25, resultado.get(0).stockActual());
            verify(repositorio, times(1)).listarDisponiblesPorMedicamento(idMedicamento);
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería retornar lista vacía cuando el medicamento no tiene existencias disponibles")
        void deberiaRetornarListaVaciaCuandoMedicamentoNoTieneLotesDisponibles() {
            // Dado
            Integer idMedicamentoAgotado = 88;
            when(repositorio.listarDisponiblesPorMedicamento(idMedicamentoAgotado)).thenReturn(List.of());

            // Cuando
            List<LoteDisponibleVista> resultado = servicio.listarDisponiblesPorMedicamento(idMedicamentoAgotado);

            // Entonces
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(repositorio, times(1)).listarDisponiblesPorMedicamento(idMedicamentoAgotado);
            verifyNoMoreInteractions(repositorio);
        }
    }
}
