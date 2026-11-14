package com.farmacia.service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Describe qué campos cambiaron en una edición para el detalle de auditoría (spec 014, CA-3.3):
 * {@code "precio_venta: 8.50 → 9.00; nombre: A → B"}. Solo se usa con datos no sensibles.
 */
final class DiferenciaCampos {
    private final StringJoiner cambios = new StringJoiner("; ");

    DiferenciaCampos campo(String nombre, Object antes, Object despues) {
        if (!iguales(antes, despues)) {
            cambios.add(nombre + ": " + texto(antes) + " → " + texto(despues));
        }
        return this;
    }

    /** Variante para datos personales: registra solo el nombre del campo, sin sus valores. */
    DiferenciaCampos campoSinValores(String nombre, Object antes, Object despues) {
        if (!iguales(antes, despues)) {
            cambios.add(nombre);
        }
        return this;
    }

    boolean hayCambios() {
        return cambios.length() > 0;
    }

    String describir() {
        return hayCambios() ? cambios.toString() : "Sin cambios en los datos";
    }

    private static boolean iguales(Object antes, Object despues) {
        if (antes instanceof BigDecimal a && despues instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        return Objects.equals(vacioComoNulo(antes), vacioComoNulo(despues));
    }

    private static Object vacioComoNulo(Object valor) {
        return valor instanceof String texto && texto.isBlank() ? null : valor;
    }

    private static String texto(Object valor) {
        Object normalizado = vacioComoNulo(valor);
        return normalizado == null ? "—" : normalizado.toString();
    }
}
