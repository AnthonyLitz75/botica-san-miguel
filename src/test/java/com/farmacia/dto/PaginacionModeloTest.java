package com.farmacia.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas unitarias de PaginacionModelo")
class PaginacionModeloTest {

    @Test
    @DisplayName("Cuando total es 0, paginas es 1, desde/hasta 0 y no hay elementos")
    void crear_cuandoTotalCero_valoresCorrectos() {
        PaginacionModelo modelo = PaginacionModelo.crear(1, 0, 25, "/movimientos", Map.of());

        assertEquals(1, modelo.pagina());
        assertEquals(1, modelo.paginas());
        assertEquals(0, modelo.total());
        assertEquals(25, modelo.tamano());
        assertEquals(0, modelo.desde());
        assertEquals(0, modelo.hasta());
        assertNull(modelo.urlAnterior());
        assertNull(modelo.urlSiguiente());
        assertTrue(modelo.elementos().isEmpty());
    }

    @Test
    @DisplayName("Cuando hay una sola página, no genera elementos de nav")
    void crear_unaSolaPagina_sinElementosNav() {
        PaginacionModelo modelo = PaginacionModelo.crear(1, 15, 25, "/movimientos", Map.of());

        assertEquals(1, modelo.pagina());
        assertEquals(1, modelo.paginas());
        assertEquals(15, modelo.total());
        assertEquals(1, modelo.desde());
        assertEquals(15, modelo.hasta());
        assertNull(modelo.urlAnterior());
        assertNull(modelo.urlSiguiente());
        assertTrue(modelo.elementos().isEmpty());
    }

    @Test
    @DisplayName("Clamp ajusta pagina < 1 a 1 y pagina > paginas a la última página")
    void crear_clampValoresInvalidos() {
        PaginacionModelo bajo = PaginacionModelo.crear(-5, 100, 25, "/movimientos", Map.of());
        assertEquals(1, bajo.pagina());
        assertEquals(4, bajo.paginas());
        assertEquals(1, bajo.desde());
        assertEquals(25, bajo.hasta());

        PaginacionModelo alto = PaginacionModelo.crear(9999, 100, 25, "/movimientos", Map.of());
        assertEquals(4, alto.pagina());
        assertEquals(4, alto.paginas());
        assertEquals(76, alto.desde());
        assertEquals(100, alto.hasta());
    }

    @Test
    @DisplayName("Ventana compacta en página 1 de 10 muestra: 1, 2, …, 10")
    void crear_ventanaCompacta_primeraPagina() {
        PaginacionModelo modelo = PaginacionModelo.crear(1, 250, 25, "/ventas", Map.of());

        assertEquals(10, modelo.paginas());
        assertNull(modelo.urlAnterior());
        assertEquals("/ventas?pagina=2", modelo.urlSiguiente());

        List<PaginacionModelo.ElementoPaginacion> elems = modelo.elementos();
        assertEquals(4, elems.size());
        assertEquals("1", elems.get(0).texto());
        assertTrue(elems.get(0).esActual());
        assertEquals("2", elems.get(1).texto());
        assertFalse(elems.get(1).esActual());
        assertTrue(elems.get(2).esSeparador());
        assertEquals("…", elems.get(2).texto());
        assertEquals("10", elems.get(3).texto());
    }

    @Test
    @DisplayName("Ventana compacta en página intermedia 5 de 10 muestra: 1, …, 4, 5, 6, …, 10")
    void crear_ventanaCompacta_paginaIntermedia() {
        PaginacionModelo modelo = PaginacionModelo.crear(5, 250, 25, "/ventas", Map.of());

        assertEquals(5, modelo.pagina());
        assertEquals("/ventas?pagina=4", modelo.urlAnterior());
        assertEquals("/ventas?pagina=6", modelo.urlSiguiente());

        List<PaginacionModelo.ElementoPaginacion> elems = modelo.elementos();
        assertEquals(7, elems.size());
        assertEquals("1", elems.get(0).texto());
        assertTrue(elems.get(1).esSeparador());
        assertEquals("4", elems.get(2).texto());
        assertEquals("5", elems.get(3).texto());
        assertTrue(elems.get(3).esActual());
        assertEquals("6", elems.get(4).texto());
        assertTrue(elems.get(5).esSeparador());
        assertEquals("10", elems.get(6).texto());
    }

    @Test
    @DisplayName("Ventana compacta en última página 10 de 10 muestra: 1, …, 9, 10")
    void crear_ventanaCompacta_ultimaPagina() {
        PaginacionModelo modelo = PaginacionModelo.crear(10, 250, 25, "/ventas", Map.of());

        assertEquals(10, modelo.pagina());
        assertEquals("/ventas?pagina=9", modelo.urlAnterior());
        assertNull(modelo.urlSiguiente());

        List<PaginacionModelo.ElementoPaginacion> elems = modelo.elementos();
        assertEquals(4, elems.size());
        assertEquals("1", elems.get(0).texto());
        assertTrue(elems.get(1).esSeparador());
        assertEquals("9", elems.get(2).texto());
        assertEquals("10", elems.get(3).texto());
        assertTrue(elems.get(3).esActual());
    }

    @Test
    @DisplayName("Conserva parámetros de filtro en URLs codificadas excluyendo nulls y vacíos")
    void crear_conservaParametrosDeFiltro() {
        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("tipo", "ENTRADA");
        filtros.put("motivo", "");
        filtros.put("idUsuario", null);
        filtros.put("q", "Paracetamol 500");

        PaginacionModelo modelo = PaginacionModelo.crear(2, 55, 25, "/movimientos", filtros);

        assertEquals(3, modelo.paginas());
        assertEquals(2, modelo.pagina());
        assertEquals("/movimientos?tipo=ENTRADA&q=Paracetamol+500&pagina=1", modelo.urlAnterior());
        assertEquals("/movimientos?tipo=ENTRADA&q=Paracetamol+500&pagina=3", modelo.urlSiguiente());
    }
}
