package com.farmacia.model;

public record Usuario(
        Integer idUsuario,
        String nombre,
        String nombreUsuario,
        String claveHash,
        String rol,
        boolean activo) {
}