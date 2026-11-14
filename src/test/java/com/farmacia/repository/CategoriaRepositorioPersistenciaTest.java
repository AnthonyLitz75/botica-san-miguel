package com.farmacia.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.farmacia.model.Categoria;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class CategoriaRepositorioPersistenciaTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private CategoriaRepositorio categoriaRepositorio;

    @BeforeEach
    void setUp() {
        limpiarBaseDeDatos();
    }

    @Test
    @DisplayName("Guarda y recupera categorías en jerarquía de 3 niveles")
    void guardarYRecuperarJerarquia() {
        Integer idRaiz = categoriaRepositorio.guardar("Analgésicos", null);
        Integer idNivel2 = categoriaRepositorio.guardar("Antiinflamatorios", idRaiz);
        Integer idNivel3 = categoriaRepositorio.guardar("AINEs", idNivel2);

        var raizOpt = categoriaRepositorio.buscarPorId(idRaiz);
        assertTrue(raizOpt.isPresent());
        assertEquals("Analgésicos", raizOpt.get().nombre());
        assertTrue(raizOpt.get().activo());

        var n3Opt = categoriaRepositorio.buscarPorId(idNivel3);
        assertTrue(n3Opt.isPresent());
        assertEquals(idNivel2, n3Opt.get().idPadre());

        List<Categoria> activas = categoriaRepositorio.listarActivas();
        assertEquals(3, activas.size());
    }

    @Test
    @DisplayName("Unicidad: no permite nombres duplicados bajo el mismo padre (case-insensitive)")
    void unicidadMismoPadreRechazaDuplicado() {
        Integer idPadre = categoriaRepositorio.guardar("Antibióticos", null);
        categoriaRepositorio.guardar("Penicilinas", idPadre);

        // Intento con idéntico nombre y padre
        assertThrows(DataIntegrityViolationException.class,
                () -> categoriaRepositorio.guardar("Penicilinas", idPadre));

        // Intento case-insensitive
        assertThrows(DataIntegrityViolationException.class,
                () -> categoriaRepositorio.guardar("penicilinas", idPadre));
    }

    @Test
    @DisplayName("Unicidad: permite el mismo nombre bajo padres distintos")
    void mismoNombreBajoPadresDistintosPermitido() {
        Integer padreA = categoriaRepositorio.guardar("Adultos", null);
        Integer padreB = categoriaRepositorio.guardar("Pediátricos", null);

        Integer idA = categoriaRepositorio.guardar("Jarabe", padreA);
        Integer idB = categoriaRepositorio.guardar("Jarabe", padreB);

        assertTrue(idA > 0);
        assertTrue(idB > 0);
    }

    @Test
    @DisplayName("Check: auto-referencia directa lanza violación de integridad")
    void autoReferenciaFallaPorCheck() {
        Integer id = categoriaRepositorio.guardar("Gastroenterología", null);

        assertThrows(DataIntegrityViolationException.class, () ->
                categoriaRepositorio.actualizar(id, "Gastroenterología", id));
    }

    @Test
    @DisplayName("Conteo de hijos activos y medicamentos activos")
    void conteosActivos() {
        Integer idPadre = categoriaRepositorio.guardar("Respiratorio", null);
        Integer idHijo1 = categoriaRepositorio.guardar("Antihistamínicos", idPadre);
        Integer idHijo2 = categoriaRepositorio.guardar("Mucolíticos", idPadre);

        assertEquals(2, categoriaRepositorio.contarHijosActivos(idPadre));
        assertEquals(0, categoriaRepositorio.contarMedicamentosActivos(idPadre));

        // Desactivar un hijo
        categoriaRepositorio.actualizarEstado(idHijo1, false);
        assertEquals(1, categoriaRepositorio.contarHijosActivos(idPadre));

        // Insertar un medicamento asociado a idPadre
        jdbcTemplate.update("""
                INSERT INTO medicamento (codigo, nombre, concentracion, presentacion, unidad_control, precio_venta, stock_minimo, id_categoria)
                VALUES ('TEST-RESP-1', 'Medicamento Resp', '10mg', 'Caja', 'CAJA', 5.0, 10, ?)
                """, idPadre);

        assertEquals(1, categoriaRepositorio.contarMedicamentosActivos(idPadre));
    }
}
