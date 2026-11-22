package com.farmacia.util;

import java.util.Locale;

/**
 * Normalizador de datos de contacto (correo y teléfono) para Clientes y Proveedores (Spec 023).
 */
public final class NormalizadorContacto {

    private NormalizadorContacto() {}

    /**
     * Normaliza un correo: trim + minúsculas. Cadena vacía o en blanco pasa a NULL.
     * "Juan@Gmail.com " y "juan@gmail.com" se unifican como "juan@gmail.com".
     */
    public static String normalizarCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            return null;
        }
        String limpio = correo.strip().toLowerCase(Locale.ROOT);
        return limpio.isEmpty() ? null : limpio;
    }

    /**
     * Normaliza un teléfono: conserva solo dígitos numéricos (elimina espacios, guiones, paréntesis).
     * Si contiene el prefijo de país "+51" con 11 dígitos, lo retira dejando los 9 dígitos nacionales.
     * Cadena vacía o en blanco pasa a NULL.
     */
    public static String normalizarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return null;
        }
        String soloDigitos = telefono.replaceAll("[^0-9]", "");
        if (soloDigitos.isEmpty()) {
            return null;
        }
        if (soloDigitos.length() == 11 && soloDigitos.startsWith("51")) {
            soloDigitos = soloDigitos.substring(2);
        }
        return soloDigitos;
    }
}
