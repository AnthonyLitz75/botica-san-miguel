package com.farmacia.model;

import com.farmacia.util.FormatoUltimoAcceso;
import java.time.LocalDateTime;

/**
 * Vista de proyección para el listado de usuarios (Spec 020).
 * Evita exponer el hash de contraseña y proporciona la fecha del último acceso con formato.
 */
public record UsuarioListadoVista(
        Integer idUsuario,
        String nombre,
        String nombreUsuario,
        String rol,
        boolean activo,
        LocalDateTime ultimoAcceso) {

    public String ultimoAccesoTexto() {
        return FormatoUltimoAcceso.formatear(ultimoAcceso);
    }

    public String ultimoAccesoTitulo() {
        return FormatoUltimoAcceso.formatearCompleto(ultimoAcceso);
    }
}
