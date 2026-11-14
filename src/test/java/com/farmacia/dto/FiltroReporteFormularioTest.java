package com.farmacia.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FiltroReporteFormularioTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneId.of("UTC"));

    @Test
    @DisplayName("Filtro por defecto toma los últimos 30 días y límite 10")
    void porDefecto_ultimos30Dias() {
        var filtro = FiltroReporteFormulario.porDefecto(clock);
        LocalDate hoy = LocalDate.now(clock);
        assertEquals(hoy.minusDays(30), filtro.desde());
        assertEquals(hoy, filtro.hasta());
        assertEquals(10, filtro.limite());
    }

    @Test
    @DisplayName("CA-1.3: Rango invertido lanza IllegalArgumentException")
    void validar_rangoInvertido_lanzaExcepcion() {
        var filtro = new FiltroReporteFormulario(
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 10),
                10, null, null);

        var ex = assertThrows(IllegalArgumentException.class, filtro::validar);
        assertEquals("La fecha inicial no puede ser posterior a la fecha final", ex.getMessage());
    }

    @Test
    @DisplayName("CA-1.3: Rango mayor a 366 días lanza IllegalArgumentException")
    void validar_rangoMayorA366Dias_lanzaExcepcion() {
        var filtro = new FiltroReporteFormulario(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 3), // 367 días
                10, null, null);

        var ex = assertThrows(IllegalArgumentException.class, filtro::validar);
        assertEquals("El rango de fechas no puede superar los 366 días", ex.getMessage());
    }

    @Test
    @DisplayName("Rango válido de 366 días no lanza excepción")
    void validar_rangoValido_noLanzaExcepcion() {
        var filtro = new FiltroReporteFormulario(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                10, null, null);

        assertDoesNotThrow(filtro::validar);
    }
}
