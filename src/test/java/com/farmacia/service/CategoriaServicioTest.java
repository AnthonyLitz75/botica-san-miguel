package com.farmacia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farmacia.estructuras.ArbolCategorias;
import com.farmacia.model.Categoria;
import com.farmacia.repository.CategoriaRepositorio;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoriaServicioTest {

    @Mock
    private CategoriaRepositorio categoriaRepositorio;

    @Mock
    private AuditoriaServicio auditoriaServicio;

    @Mock
    private com.farmacia.repository.MedicamentoRepositorio medicamentoRepositorio;

    private final java.time.Clock clockFijo = java.time.Clock.fixed(
            java.time.Instant.parse("2026-11-14T10:00:00Z"),
            java.time.ZoneId.of("UTC")
    );

    private CategoriaServicio categoriaServicio;

    @BeforeEach
    void setUp() {
        categoriaServicio = new CategoriaServicio(categoriaRepositorio, auditoriaServicio, medicamentoRepositorio, clockFijo);
    }

    @Test
    @DisplayName("CA-2.1 & CA-2.4: Crear categoría con padre opcional y registrar evento de auditoría")
    void crearCategoriaConAuditoria() {
        when(categoriaRepositorio.buscarPorPadreYNombre(null, "Analgésicos"))
                .thenReturn(Optional.empty());
        when(categoriaRepositorio.guardar("Analgésicos", null)).thenReturn(1);

        Integer id = categoriaServicio.crear("Analgésicos", null);
        assertEquals(1, id);

        verify(categoriaRepositorio).guardar("Analgésicos", null);
        verify(auditoriaServicio).registrarCambio(
                eq(AccionAuditoria.CREAR), eq("CATEGORIA"), eq(1), anyString());
    }

    @Test
    @DisplayName("CA-2.3: Crear con nombre duplicado bajo el mismo padre lanza excepción")
    void crearNombreDuplicadoLanzaExcepcion() {
        when(categoriaRepositorio.buscarPorPadreYNombre(1, "AINEs"))
                .thenReturn(Optional.of(new Categoria(2, "AINEs", 1, true)));
        when(categoriaRepositorio.buscarPorId(1))
                .thenReturn(Optional.of(new Categoria(1, "Antiinflamatorios", null, true)));

        assertThrows(IllegalArgumentException.class, () -> categoriaServicio.crear("AINEs", 1));
        verify(categoriaRepositorio, never()).guardar(anyString(), any());
    }

    @Test
    @DisplayName("CA-2.1: Mover categoría a su propio descendiente falla para prevenir ciclos")
    void moverADescendienteFallaPorCiclo() {
        // Árbol: 1 -> 2 -> 3
        Categoria c1 = new Categoria(1, "Analgésicos", null, true);
        Categoria c2 = new Categoria(2, "Antiinflamatorios", 1, true);
        Categoria c3 = new Categoria(3, "AINEs", 2, true);

        when(categoriaRepositorio.listarActivas()).thenReturn(List.of(c1, c2, c3));
        when(categoriaRepositorio.buscarPorId(1)).thenReturn(Optional.of(c1));
        when(categoriaRepositorio.buscarPorId(3)).thenReturn(Optional.of(c3));

        // Intento de mover 1 (raíz) para ser hijo de 3 (su nieto)
        var ex = assertThrows(IllegalArgumentException.class,
                () -> categoriaServicio.actualizar(1, "Analgésicos", 3));
        assertEquals("No se puede mover la categoría a uno de sus descendientes para evitar ciclos", ex.getMessage());

        verify(categoriaRepositorio, never()).actualizar(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("CA-2.1 & CA-2.4: Mover y renombrar categoría válido actualiza y audita")
    void moverYRenombrarValido() {
        Categoria c1 = new Categoria(1, "Analgésicos", null, true);
        Categoria c2 = new Categoria(2, "Gastro", null, true);
        Categoria c3 = new Categoria(3, "Antiácidos", 2, true);

        when(categoriaRepositorio.listarActivas()).thenReturn(List.of(c1, c2, c3));
        when(categoriaRepositorio.buscarPorId(3)).thenReturn(Optional.of(c3));
        when(categoriaRepositorio.buscarPorId(1)).thenReturn(Optional.of(c1));
        when(categoriaRepositorio.buscarPorPadreYNombre(1, "Protectores Gástricos"))
                .thenReturn(Optional.empty());

        categoriaServicio.actualizar(3, "Protectores Gástricos", 1);

        verify(categoriaRepositorio).actualizar(3, "Protectores Gástricos", 1);
        verify(auditoriaServicio).registrarCambio(
                eq(AccionAuditoria.EDITAR), eq("CATEGORIA"), eq(3), anyString());
    }

    @Test
    @DisplayName("CA-2.2: Desactivar con subcategorías activas falla")
    void desactivarConSubcategoriasFalla() {
        when(categoriaRepositorio.buscarPorId(1))
                .thenReturn(Optional.of(new Categoria(1, "Analgésicos", null, true)));
        when(categoriaRepositorio.contarHijosActivos(1)).thenReturn(2);

        var ex = assertThrows(IllegalStateException.class, () -> categoriaServicio.desactivar(1));
        assertEquals("No se puede desactivar la categoría porque tiene subcategorías activas", ex.getMessage());

        verify(categoriaRepositorio, never()).actualizarEstado(anyInt(), any(Boolean.class));
    }

    @Test
    @DisplayName("CA-2.2: Desactivar con medicamentos activos falla")
    void desactivarConMedicamentosFalla() {
        when(categoriaRepositorio.buscarPorId(1))
                .thenReturn(Optional.of(new Categoria(1, "Analgésicos", null, true)));
        when(categoriaRepositorio.contarHijosActivos(1)).thenReturn(0);
        when(categoriaRepositorio.contarMedicamentosActivos(1)).thenReturn(5);

        var ex = assertThrows(IllegalStateException.class, () -> categoriaServicio.desactivar(1));
        assertEquals("No se puede desactivar la categoría porque tiene medicamentos activos asociados", ex.getMessage());

        verify(categoriaRepositorio, never()).actualizarEstado(anyInt(), any(Boolean.class));
    }

    @Test
    @DisplayName("CA-2.2 & CA-2.4: Desactivar categoría sin dependencias tiene éxito y audita")
    void desactivarExitoso() {
        when(categoriaRepositorio.buscarPorId(1))
                .thenReturn(Optional.of(new Categoria(1, "Analgésicos", null, true)));
        when(categoriaRepositorio.contarHijosActivos(1)).thenReturn(0);
        when(categoriaRepositorio.contarMedicamentosActivos(1)).thenReturn(0);

        categoriaServicio.desactivar(1);

        verify(categoriaRepositorio).actualizarEstado(1, false);
        verify(auditoriaServicio).registrarCambio(
                eq(AccionAuditoria.DESACTIVAR), eq("CATEGORIA"), eq(1), anyString());
    }

    @Test
    @DisplayName("Construcción del árbol de categorías en memoria")
    void construccionDelArbol() {
        Categoria c1 = new Categoria(1, "Analgésicos", null, true);
        Categoria c2 = new Categoria(2, "Antiinflamatorios", 1, true);
        Categoria c3 = new Categoria(3, "AINEs", 2, true);

        when(categoriaRepositorio.listarActivas()).thenReturn(List.of(c1, c2, c3));

        ArbolCategorias arbol = categoriaServicio.arbol();
        assertNotNull(arbol);
        assertEquals(3, arbol.tamanio());
        assertEquals(1, arbol.raices().size());
        assertEquals(1, arbol.raices().getFirst().id());
        assertEquals(List.of(1, 2, 3), arbol.descendientesIds(1));
    }

    @Test
    @DisplayName("CA-3.1: stockPorRama suma stock disponible de la rama completa")
    void stockPorRama_calculaTotalDescendientes() {
        Categoria c1 = new Categoria(1, "Analgésicos", null, true);
        Categoria c2 = new Categoria(2, "Antiinflamatorios", 1, true);
        when(categoriaRepositorio.listarActivas()).thenReturn(List.of(c1, c2));

        java.time.LocalDate fechaEsperada = java.time.LocalDate.now(clockFijo);
        when(medicamentoRepositorio.sumarStockDisponiblePorCategorias(List.of(1, 2), fechaEsperada)).thenReturn(45);

        int total = categoriaServicio.stockPorRama(1);
        assertEquals(45, total);

        verify(medicamentoRepositorio).sumarStockDisponiblePorCategorias(List.of(1, 2), fechaEsperada);
    }

    @Test
    @DisplayName("alertasPorRama cuenta medicamentos en alerta en toda la rama")
    void alertasPorRama_cuentaAlertas() {
        Categoria c1 = new Categoria(1, "Analgésicos", null, true);
        when(categoriaRepositorio.listarActivas()).thenReturn(List.of(c1));

        java.time.LocalDate fechaEsperada = java.time.LocalDate.now(clockFijo);
        when(medicamentoRepositorio.contarAlertasPorCategorias(List.of(1), fechaEsperada)).thenReturn(3);

        int alertas = categoriaServicio.alertasPorRama(1);
        assertEquals(3, alertas);
    }
}
