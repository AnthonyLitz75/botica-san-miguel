package com.farmacia.service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Escritor CSV mínimo según RFC 4180 (sin dependencias): separador coma, fin de línea CRLF y comillas
 * dobles cuando un valor contiene coma, comillas o saltos de línea. Lo usan la auditoría (014) y los
 * reportes (008).
 */
public final class CsvEscritor {
    private final StringBuilder contenido = new StringBuilder();

    public CsvEscritor fila(List<?> valores) {
        contenido.append(valores.stream().map(CsvEscritor::campo).collect(Collectors.joining(","))).append("\r\n");
        return this;
    }

    public CsvEscritor fila(Object... valores) {
        // Arrays.asList admite nulos (List.of no): un evento sin IP o sin detalle produce un campo vacío.
        return fila(java.util.Arrays.asList(valores));
    }

    @Override
    public String toString() {
        return contenido.toString();
    }

    static String campo(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor.toString();
        // Evita que una hoja de cálculo interprete el valor como fórmula (inyección CSV).
        if (!texto.isEmpty() && "=+-@".indexOf(texto.charAt(0)) >= 0 && !esNumero(texto)) {
            texto = "'" + texto;
        }
        if (texto.contains(",") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }

    private static boolean esNumero(String texto) {
        try {
            new java.math.BigDecimal(texto);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
