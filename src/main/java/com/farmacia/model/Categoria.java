package com.farmacia.model;

/**
 * Modelo inmutable que representa una categoría taxonómica de medicamentos.
 *
 * @param idCategoria Identificador único de la categoría.
 * @param nombre Nombre descriptivo de la categoría.
 * @param idPadre Identificador de la categoría padre (null si es raíz).
 * @param activo Estado operativo de la categoría.
 */
public record Categoria(
        Integer idCategoria,
        String nombre,
        Integer idPadre,
        boolean activo) {

    public Categoria(Integer idCategoria, String nombre, Integer idPadre) {
        this(idCategoria, nombre, idPadre, true);
    }
}
