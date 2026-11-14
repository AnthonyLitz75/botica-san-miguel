package com.farmacia.service;

import com.farmacia.dto.MedicamentoFormulario;
import com.farmacia.model.Medicamento;
import com.farmacia.repository.MedicamentoRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas unitarias de MedicamentoServicio")
class MedicamentoServicioTest {
    /** Auditoría simulada (spec 014). */
    private final AuditoriaServicio auditoria = org.mockito.Mockito.mock(AuditoriaServicio.class);

    @Mock
    private MedicamentoRepositorio repositorio;

    private MedicamentoServicio servicio;

    @BeforeEach
    void setUp() {
        servicio = new MedicamentoServicio(repositorio, auditoria);
    }

    private Medicamento crearMedicamento(Integer id, String codigo, String nombre,
                                        String concentracion, boolean activo, Integer stockActual) {
        return new Medicamento(
                id,
                codigo,
                nombre,
                concentracion,
                "TABLETA",
                "CAJA",
                new BigDecimal("15.50"),
                20,
                activo,
                stockActual
        );
    }

    private MedicamentoFormulario crearFormulario(String codigo, String nombre, String concentracion,
                                                  String presentacion, String unidadControl,
                                                  BigDecimal precioVenta, Integer stockMinimo) {
        return new MedicamentoFormulario(
                codigo,
                nombre,
                concentracion,
                presentacion,
                unidadControl,
                precioVenta,
                stockMinimo
        );
    }

    // =========================================================================
    // 1. Pruebas de listarActivos()
    // =========================================================================
    @Nested
    @DisplayName("1. listarActivos()")
    class ListarActivos {

        @Test
        @DisplayName("Debería retornar lista de medicamentos activos cuando existen registros en el repositorio")
        void deberiaRetornarListaDeMedicamentosActivosCuandoExistenEnRepositorio() {
            // Dado
            List<Medicamento> esperados = List.of(
                    crearMedicamento(1, "MED-001", "Paracetamol", "500 mg", true, 100),
                    crearMedicamento(2, "MED-002", "Amoxicilina", "250 mg", true, 50)
            );
            when(repositorio.listarActivos()).thenReturn(esperados);

            // Cuando
            List<Medicamento> resultado = servicio.listarActivos();

            // Entonces
            assertNotNull(resultado);
            assertEquals(2, resultado.size());
            assertEquals("MED-001", resultado.get(0).codigo());
            assertEquals("MED-002", resultado.get(1).codigo());
            verify(repositorio, times(1)).listarActivos();
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería retornar lista vacía cuando no existen medicamentos activos")
        void deberiaRetornarListaVaciaCuandoNoExistenMedicamentosActivos() {
            // Dado
            when(repositorio.listarActivos()).thenReturn(List.of());

            // Cuando
            List<Medicamento> resultado = servicio.listarActivos();

            // Entonces
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(repositorio, times(1)).listarActivos();
            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 2. Pruebas de buscarActivos(String texto)
    // =========================================================================
    @Nested
    @DisplayName("2. buscarActivos(String texto)")
    class BuscarActivos {

        @Test
        @DisplayName("Debería limpiar espacios y delegar al repositorio con texto sanitizado")
        void deberiaLimpiarEspaciosYDelegarEnRepositorioCuandoTextoTieneEspacios() {
            // Dado
            String textoEntrada = "   paracetamol   ";
            List<Medicamento> esperados = List.of(
                    crearMedicamento(1, "MED-001", "Paracetamol", "500 mg", true, 100)
            );
            when(repositorio.buscarActivos("paracetamol")).thenReturn(esperados);

            // Cuando
            List<Medicamento> resultado = servicio.buscarActivos(textoEntrada);

            // Entonces
            assertNotNull(resultado);
            assertEquals(1, resultado.size());
            assertEquals("Paracetamol", resultado.get(0).nombre());
            verify(repositorio, times(1)).buscarActivos("paracetamol");
            verifyNoMoreInteractions(repositorio);
        }

        @ParameterizedTest(name = "Texto de búsqueda nulo o en blanco: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Debería normalizar a cadena vacía cuando el texto es nulo, vacío o puros espacios")
        void deberiaNormalizarACadenaVaciaCuandoTextoEsNuloOEnBlanco(String textoEntrada) {
            // Dado
            when(repositorio.buscarActivos("")).thenReturn(List.of());

            // Cuando
            List<Medicamento> resultado = servicio.buscarActivos(textoEntrada);

            // Entonces
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(repositorio, times(1)).buscarActivos("");
            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 3. Pruebas de guardar(MedicamentoFormulario formulario)
    // =========================================================================
    @Nested
    @DisplayName("3. guardar(MedicamentoFormulario formulario)")
    class Guardar {

        @Test
        @DisplayName("Debería sanitizar todos los campos con strip() y retornar el ID generado cuando la concentración está presente")
        void deberiaSanitizarCamposConEspaciosYGuardarMedicamentoConConcentracion() {
            // Dado
            MedicamentoFormulario entrada = crearFormulario(
                    "  MED-010  ",
                    "  Ibuprofeno  ",
                    "  400 mg  ",
                    "  TABLETA  ",
                    "  CAJA  ",
                    new BigDecimal("12.50"),
                    15
            );
            when(repositorio.guardar(any(MedicamentoFormulario.class))).thenReturn(10);

            // Cuando
            Integer idGenerado = servicio.guardar(entrada);

            // Entonces
            assertEquals(10, idGenerado);
            ArgumentCaptor<MedicamentoFormulario> captor = ArgumentCaptor.forClass(MedicamentoFormulario.class);
            verify(repositorio, times(1)).guardar(captor.capture());

            MedicamentoFormulario enviado = captor.getValue();
            assertEquals("MED-010", enviado.codigo());
            assertEquals("Ibuprofeno", enviado.nombre());
            assertEquals("400 mg", enviado.concentracion());
            assertEquals("TABLETA", enviado.presentacion());
            assertEquals("CAJA", enviado.unidadControl());
            assertEquals(new BigDecimal("12.50"), enviado.precioVenta());
            assertEquals(15, enviado.stockMinimo());

            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería preservar concentración nula al sanitizar y guardar el medicamento")
        void deberiaPreservarConcentracionNulaAlSanitizarYGuardar() {
            // Dado
            MedicamentoFormulario entrada = crearFormulario(
                    " MED-020 ",
                    " Alcohol Etílico 70° ",
                    null,
                    " FRASCO ",
                    " FRASCO ",
                    new BigDecimal("8.00"),
                    30
            );
            when(repositorio.guardar(any(MedicamentoFormulario.class))).thenReturn(20);

            // Cuando
            Integer idGenerado = servicio.guardar(entrada);

            // Entonces
            assertEquals(20, idGenerado);
            ArgumentCaptor<MedicamentoFormulario> captor = ArgumentCaptor.forClass(MedicamentoFormulario.class);
            verify(repositorio, times(1)).guardar(captor.capture());

            MedicamentoFormulario enviado = captor.getValue();
            assertEquals("MED-020", enviado.codigo());
            assertEquals("Alcohol Etílico 70°", enviado.nombre());
            assertNull(enviado.concentracion());
            assertEquals("FRASCO", enviado.presentacion());
            assertEquals("FRASCO", enviado.unidadControl());
            assertEquals(new BigDecimal("8.00"), enviado.precioVenta());
            assertEquals(30, enviado.stockMinimo());

            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 4. Pruebas de actualizar(Integer id, MedicamentoFormulario formulario)
    // =========================================================================
    @Nested
    @DisplayName("4. actualizar(Integer id, MedicamentoFormulario formulario)")
    class Actualizar {

        @Test
        @DisplayName("Debería retornar true cuando el repositorio actualiza exactamente 1 fila con concentración presente")
        void deberiaRetornarTrueCuandoElRepositorioActualizaUnaFila() {
            // Dado
            Integer id = 5;
            MedicamentoFormulario entrada = crearFormulario(
                    "  MED-005  ",
                    "  Amoxicilina Ácido Clavulánico  ",
                    "  500 mg / 125 mg  ",
                    "  TABLETA  ",
                    "  CAJA  ",
                    new BigDecimal("35.00"),
                    10
            );
            when(repositorio.actualizar(eq(id), any(MedicamentoFormulario.class))).thenReturn(1);

            // Cuando
            boolean resultado = servicio.actualizar(id, entrada);

            // Entonces
            assertTrue(resultado);
            ArgumentCaptor<MedicamentoFormulario> captor = ArgumentCaptor.forClass(MedicamentoFormulario.class);
            verify(repositorio, times(1)).actualizar(eq(id), captor.capture());

            MedicamentoFormulario enviado = captor.getValue();
            assertEquals("MED-005", enviado.codigo());
            assertEquals("Amoxicilina Ácido Clavulánico", enviado.nombre());
            assertEquals("500 mg / 125 mg", enviado.concentracion());
            assertEquals("TABLETA", enviado.presentacion());
            assertEquals("CAJA", enviado.unidadControl());
            assertEquals(new BigDecimal("35.00"), enviado.precioVenta());
            assertEquals(10, enviado.stockMinimo());

            // Spec 014: lee el registro anterior para describir los cambios en la auditoría

            verify(repositorio).buscarPorId(org.mockito.ArgumentMatchers.any());

            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería retornar false cuando el repositorio actualiza 0 filas (ID no encontrado)")
        void deberiaRetornarFalseCuandoElRepositorioNoActualizaNingunaFila() {
            // Dado
            Integer idInexistente = 999;
            MedicamentoFormulario entrada = crearFormulario(
                    "MED-999",
                    "Medicamento Fantasma",
                    null,
                    "FRASCO",
                    "FRASCO",
                    new BigDecimal("10.00"),
                    5
            );
            when(repositorio.actualizar(eq(idInexistente), any(MedicamentoFormulario.class))).thenReturn(0);

            // Cuando
            boolean resultado = servicio.actualizar(idInexistente, entrada);

            // Entonces
            assertFalse(resultado);
            verify(repositorio, times(1)).actualizar(eq(idInexistente), any(MedicamentoFormulario.class));
            // Spec 014: lee el registro anterior para describir los cambios en la auditoría
            verify(repositorio).buscarPorId(org.mockito.ArgumentMatchers.any());
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería sanitizar preservando concentración nula en la actualización")
        void deberiaSanitizarPreservandoConcentracionNulaEnActualizacion() {
            // Dado
            Integer id = 7;
            MedicamentoFormulario entrada = crearFormulario(
                    "  MED-007  ",
                    "  Agua Oxigenada 10 Vol  ",
                    null,
                    "  FRASCO  ",
                    "  FRASCO  ",
                    new BigDecimal("4.50"),
                    20
            );
            when(repositorio.actualizar(eq(id), any(MedicamentoFormulario.class))).thenReturn(1);

            // Cuando
            boolean resultado = servicio.actualizar(id, entrada);

            // Entonces
            assertTrue(resultado);
            ArgumentCaptor<MedicamentoFormulario> captor = ArgumentCaptor.forClass(MedicamentoFormulario.class);
            verify(repositorio, times(1)).actualizar(eq(id), captor.capture());

            MedicamentoFormulario enviado = captor.getValue();
            assertEquals("MED-007", enviado.codigo());
            assertEquals("Agua Oxigenada 10 Vol", enviado.nombre());
            assertNull(enviado.concentracion());
            assertEquals("FRASCO", enviado.presentacion());
            assertEquals("FRASCO", enviado.unidadControl());

            // Spec 014: lee el registro anterior para describir los cambios en la auditoría

            verify(repositorio).buscarPorId(org.mockito.ArgumentMatchers.any());

            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 5. Pruebas de buscarPorId(Integer id)
    // =========================================================================
    @Nested
    @DisplayName("5. buscarPorId(Integer id)")
    class BuscarPorId {

        @Test
        @DisplayName("Debería retornar Optional con el medicamento cuando el ID existe")
        void deberiaRetornarOptionalConMedicamentoCuandoIdExiste() {
            // Dado
            Integer id = 1;
            Medicamento medicamento = crearMedicamento(1, "MED-001", "Paracetamol", "500 mg", true, 80);
            when(repositorio.buscarPorId(id)).thenReturn(Optional.of(medicamento));

            // Cuando
            Optional<Medicamento> resultado = servicio.buscarPorId(id);

            // Entonces
            assertTrue(resultado.isPresent());
            assertEquals("MED-001", resultado.get().codigo());
            assertEquals("Paracetamol", resultado.get().nombre());
            verify(repositorio, times(1)).buscarPorId(id);
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería retornar Optional vacío cuando el ID no existe")
        void deberiaRetornarOptionalVacioCuandoIdNoExiste() {
            // Dado
            Integer idInexistente = 999;
            when(repositorio.buscarPorId(idInexistente)).thenReturn(Optional.empty());

            // Cuando
            Optional<Medicamento> resultado = servicio.buscarPorId(idInexistente);

            // Entonces
            assertTrue(resultado.isEmpty());
            verify(repositorio, times(1)).buscarPorId(idInexistente);
            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 6. Pruebas de desactivar(Integer id)
    // =========================================================================
    @Nested
    @DisplayName("6. desactivar(Integer id)")
    class Desactivar {

        @Test
        @DisplayName("Debería retornar true cuando el repositorio desactiva exactamente 1 fila")
        void deberiaRetornarTrueCuandoDesactivacionAfectaUnaFila() {
            // Dado
            Integer id = 3;
            when(repositorio.desactivar(id)).thenReturn(1);

            // Cuando
            boolean resultado = servicio.desactivar(id);

            // Entonces
            assertTrue(resultado);
            verify(repositorio, times(1)).desactivar(id);
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería retornar false cuando el repositorio no afecta filas al desactivar")
        void deberiaRetornarFalseCuandoDesactivacionNoAfectaFilas() {
            // Dado
            Integer idInexistente = 999;
            when(repositorio.desactivar(idInexistente)).thenReturn(0);

            // Cuando
            boolean resultado = servicio.desactivar(idInexistente);

            // Entonces
            assertFalse(resultado);
            verify(repositorio, times(1)).desactivar(idInexistente);
            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 7. Pruebas de activar(Integer id)
    // =========================================================================
    @Nested
    @DisplayName("7. activar(Integer id)")
    class Activar {

        @Test
        @DisplayName("Debería retornar true cuando el repositorio activa exactamente 1 fila")
        void deberiaRetornarTrueCuandoActivacionAfectaUnaFila() {
            // Dado
            Integer id = 4;
            when(repositorio.activar(id)).thenReturn(1);

            // Cuando
            boolean resultado = servicio.activar(id);

            // Entonces
            assertTrue(resultado);
            verify(repositorio, times(1)).activar(id);
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería retornar false cuando el repositorio no afecta filas al activar")
        void deberiaRetornarFalseCuandoActivacionNoAfectaFilas() {
            // Dado
            Integer idInexistente = 999;
            when(repositorio.activar(idInexistente)).thenReturn(0);

            // Cuando
            boolean resultado = servicio.activar(idInexistente);

            // Entonces
            assertFalse(resultado);
            verify(repositorio, times(1)).activar(idInexistente);
            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 8. Pruebas de buscarPorEstado(String texto, boolean activo)
    // =========================================================================
    @Nested
    @DisplayName("8. buscarPorEstado(String texto, boolean activo)")
    class BuscarPorEstado {

        @Test
        @DisplayName("Debería limpiar espacios y delegar al repositorio con activo=true")
        void deberiaBuscarPorEstadoConTextoLimpioYActivoTrue() {
            // Dado
            String texto = "  amoxicilina  ";
            List<Medicamento> esperados = List.of(
                    crearMedicamento(2, "MED-002", "Amoxicilina", "250 mg", true, 50)
            );
            when(repositorio.buscarPorEstado("amoxicilina", true)).thenReturn(esperados);

            // Cuando
            List<Medicamento> resultado = servicio.buscarPorEstado(texto, true);

            // Entonces
            assertNotNull(resultado);
            assertEquals(1, resultado.size());
            assertEquals("MED-002", resultado.get(0).codigo());
            verify(repositorio, times(1)).buscarPorEstado("amoxicilina", true);
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería limpiar espacios y delegar al repositorio con activo=false")
        void deberiaBuscarPorEstadoConTextoLimpioYActivoFalse() {
            // Dado
            String texto = "  aspirina  ";
            List<Medicamento> esperados = List.of(
                    crearMedicamento(9, "MED-009", "Aspirina", "100 mg", false, 0)
            );
            when(repositorio.buscarPorEstado("aspirina", false)).thenReturn(esperados);

            // Cuando
            List<Medicamento> resultado = servicio.buscarPorEstado(texto, false);

            // Entonces
            assertNotNull(resultado);
            assertEquals(1, resultado.size());
            assertEquals("MED-009", resultado.get(0).codigo());
            assertFalse(resultado.get(0).activo());
            verify(repositorio, times(1)).buscarPorEstado("aspirina", false);
            verifyNoMoreInteractions(repositorio);
        }

        @ParameterizedTest(name = "Texto nulo o en blanco con estado: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Debería normalizar a cadena vacía cuando el texto es nulo o puros espacios al buscar por estado")
        void deberiaNormalizarTextoNuloACadenaVaciaAlBuscarPorEstado(String textoEntrada) {
            // Dado
            when(repositorio.buscarPorEstado("", true)).thenReturn(List.of());

            // Cuando
            List<Medicamento> resultado = servicio.buscarPorEstado(textoEntrada, true);

            // Entonces
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(repositorio, times(1)).buscarPorEstado("", true);
            verifyNoMoreInteractions(repositorio);
        }
    }
}
