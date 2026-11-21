package com.farmacia.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Formateador de fechas de último acceso según Spec 020 (CA-2.1 a CA-2.3).
 * Utiliza la zona horaria America/Lima y soporta Clock inyectado o por defecto.
 */
public final class FormatoUltimoAcceso {

    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_COMPLETO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private FormatoUltimoAcceso() {}

    /**
     * Retorna "Hoy HH:mm", "Ayer HH:mm", "dd/MM/yyyy" o "Nunca" si fechaHora es null.
     */
    public static String formatear(LocalDateTime fechaHora, Clock clock) {
        if (fechaHora == null) {
            return "Nunca";
        }
        Clock relojLima = clock.getZone().equals(ZONA_LIMA) ? clock : clock.withZone(ZONA_LIMA);
        LocalDate hoy = LocalDate.now(relojLima);
        LocalDate fecha = fechaHora.toLocalDate();

        if (fecha.equals(hoy)) {
            return "Hoy " + fechaHora.format(FORMATO_HORA);
        } else if (fecha.equals(hoy.minusDays(1))) {
            return "Ayer " + fechaHora.format(FORMATO_HORA);
        } else {
            return fechaHora.format(FORMATO_FECHA);
        }
    }

    /**
     * Retorna el formato relativo utilizando la hora actual de America/Lima.
     */
    public static String formatear(LocalDateTime fechaHora) {
        return formatear(fechaHora, Clock.system(ZONA_LIMA));
    }

    /**
     * Retorna "dd/MM/yyyy HH:mm" para el atributo title de la celda (CA-2.2), o null si es null.
     */
    public static String formatearCompleto(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            return null;
        }
        return fechaHora.format(FORMATO_COMPLETO);
    }
}
