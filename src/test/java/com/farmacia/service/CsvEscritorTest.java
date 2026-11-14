package com.farmacia.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Escritor CSV (RFC 4180) compartido por la auditoría (014) y los reportes (008). */
class CsvEscritorTest {

    @Test
    @DisplayName("Separa con comas, termina con CRLF y deja vacíos los nulos")
    void filaSimple() {
        assertEquals("a,1,\r\n", new CsvEscritor().fila("a", 1, null).toString());
    }

    @Test
    @DisplayName("Entrecomilla valores con coma, comillas o saltos de línea y duplica las comillas")
    void escapeRfc4180() {
        assertEquals("\"Lima, Perú\",\"dijo \"\"hola\"\"\",\"línea1\nlínea2\"\r\n",
                new CsvEscritor().fila("Lima, Perú", "dijo \"hola\"", "línea1\nlínea2").toString());
    }

    @Test
    @DisplayName("Neutraliza fórmulas (inyección CSV) pero conserva números negativos")
    void inyeccionCsv() {
        assertEquals("'=HYPERLINK(1),-5.50,'@SUM(A1)\r\n",
                new CsvEscritor().fila("=HYPERLINK(1)", "-5.50", "@SUM(A1)").toString());
    }
}
