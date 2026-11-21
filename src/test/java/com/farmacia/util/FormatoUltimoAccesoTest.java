package com.farmacia.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Spec 020, US-2 (CA-2.1 a CA-2.3): formato de último acceso con Clock fijo. */
class FormatoUltimoAccesoTest {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    // Fijamos el reloj en 2026-11-21 15:30:00 hora de Lima (UTC-5 -> 20:30:00Z)
    private static final Instant INSTANTE_BASE = Instant.parse("2026-11-21T20:30:00Z");
    private final Clock clockFijo = Clock.fixed(INSTANTE_BASE, ZONA_LIMA);

    @Test
    @DisplayName("CA-2.3: si fechaHora es null retorna 'Nunca' y title null")
    void fechaNullRetornaNunca() {
        assertEquals("Nunca", FormatoUltimoAcceso.formatear(null, clockFijo));
        assertNull(FormatoUltimoAcceso.formatearCompleto(null));
    }

    @Test
    @DisplayName("CA-2.1: acceso hoy retorna 'Hoy HH:mm'")
    void accesoHoyRetornaHoyHora() {
        LocalDateTime hoy1030 = LocalDateTime.of(2026, 11, 21, 10, 30);
        assertEquals("Hoy 10:30", FormatoUltimoAcceso.formatear(hoy1030, clockFijo));
    }

    @Test
    @DisplayName("CA-2.1: acceso ayer retorna 'Ayer HH:mm'")
    void accesoAyerRetornaAyerHora() {
        LocalDateTime ayer1748 = LocalDateTime.of(2026, 11, 20, 17, 48);
        assertEquals("Ayer 17:48", FormatoUltimoAcceso.formatear(ayer1748, clockFijo));
    }

    @Test
    @DisplayName("CA-2.1: acceso hace dos o más días retorna 'dd/MM/yyyy'")
    void accesoAntiguoRetornaFecha() {
        LocalDateTime antiguo = LocalDateTime.of(2026, 11, 12, 14, 15);
        assertEquals("12/11/2026", FormatoUltimoAcceso.formatear(antiguo, clockFijo));
    }

    @Test
    @DisplayName("CA-2.2: formatearCompleto retorna 'dd/MM/yyyy HH:mm' para title de celda")
    void formatearCompletoRetornaFechaYHora() {
        LocalDateTime fechaHora = LocalDateTime.of(2026, 11, 20, 17, 48);
        assertEquals("20/11/2026 17:48", FormatoUltimoAcceso.formatearCompleto(fechaHora));
    }

    @Test
    @DisplayName("Borde de medianoche: reloj en 00:00:00 distingue correctamente Ayer (23:59:59) y Hoy (00:00:00)")
    void bordeMedianocheDistingueAyerYHoy() {
        // Medianoche del 21 de noviembre en Lima: 2026-11-21T00:00:00-05:00 -> 2026-11-21T05:00:00Z
        Clock clockMedianoche = Clock.fixed(Instant.parse("2026-11-21T05:00:00Z"), ZONA_LIMA);

        LocalDateTime justoAntes = LocalDateTime.of(2026, 11, 20, 23, 59, 59);
        LocalDateTime justoMedianoche = LocalDateTime.of(2026, 11, 21, 0, 0, 0);

        assertEquals("Ayer 23:59", FormatoUltimoAcceso.formatear(justoAntes, clockMedianoche));
        assertEquals("Hoy 00:00", FormatoUltimoAcceso.formatear(justoMedianoche, clockMedianoche));
    }
}
