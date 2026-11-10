package com.farmacia.model;

import java.math.BigDecimal;

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
                Integer stockActual) {

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
                this(idMedicamento, codigo, nombre, concentracion, presentacion, unidadControl, precioVenta, stockMinimo, activo, 0);
        }
}