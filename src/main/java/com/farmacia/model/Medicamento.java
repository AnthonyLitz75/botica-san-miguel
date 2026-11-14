package com.farmacia.model;

import java.math.BigDecimal;

/**
 * Modelo inmutable que representa un medicamento en el catálogo.
 */
public record Medicamento(
        Integer idMedicamento,
        String codigo,
        String nombre,
        String concentracion,
        String presentacion,
        String unidadControl,
        BigDecimal precioVenta,
        Integer stockMinimo,
        boolean activo,
        Integer stockActual,
        Integer idCategoria) {

    /** Constructor de compatibilidad (10 parámetros). */
    public Medicamento(
            Integer idMedicamento,
            String codigo,
            String nombre,
            String concentracion,
            String presentacion,
            String unidadControl,
            BigDecimal precioVenta,
            Integer stockMinimo,
            boolean activo,
            Integer stockActual) {
        this(idMedicamento, codigo, nombre, concentracion, presentacion, unidadControl, precioVenta, stockMinimo, activo, stockActual, null);
    }

    /** Constructor de compatibilidad (9 parámetros). */
    public Medicamento(
            Integer idMedicamento,
            String codigo,
            String nombre,
            String concentracion,
            String presentacion,
            String unidadControl,
            BigDecimal precioVenta,
            Integer stockMinimo,
            boolean activo) {
        this(idMedicamento, codigo, nombre, concentracion, presentacion, unidadControl, precioVenta, stockMinimo, activo, 0, null);
    }
}
