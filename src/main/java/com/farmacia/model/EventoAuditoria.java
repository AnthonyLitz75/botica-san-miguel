package com.farmacia.model;

import com.farmacia.service.AccionAuditoria;

import java.time.LocalDateTime;

/** Fila de la bitácora de auditoría (spec 014 y 018). */
public record EventoAuditoria(
        Long idAuditoria,
        LocalDateTime fechaHora,
        Integer idUsuario,
        String nombreUsuario,
        String accion,
        String entidad,
        String idEntidad,
        String resultado,
        String detalle,
        String ipOrigen) {

    /**
     * Devuelve la etiqueta legible de la acción o el código original si es desconocida (CA-2.1, CA-2.5).
     */
    public String etiquetaAccion() {
        return etiquetaAccion(this.accion);
    }

    public static String etiquetaAccion(String codigoAccion) {
        if (codigoAccion == null || codigoAccion.isBlank()) {
            return "—";
        }
        try {
            return AccionAuditoria.valueOf(codigoAccion.strip()).etiqueta();
        } catch (IllegalArgumentException e) {
            return codigoAccion;
        }
    }

    /**
     * Devuelve la etiqueta legible del resultado (CA-2.2).
     */
    public String etiquetaResultado() {
        return etiquetaResultado(this.resultado);
    }

    public static String etiquetaResultado(String codigoResultado) {
        if (codigoResultado == null || codigoResultado.isBlank()) {
            return "—";
        }
        return switch (codigoResultado.strip().toUpperCase()) {
            case "EXITO" -> "ÉXITO";
            case "DENEGADO" -> "DENEGADO";
            case "FALLO" -> "FALLO";
            default -> codigoResultado;
        };
    }

    /**
     * Devuelve la etiqueta humana legible de la entidad (Boceto 06).
     */
    public String etiquetaEntidad() {
        return etiquetaEntidad(this.entidad);
    }

    public static String etiquetaEntidad(String codigoEntidad) {
        if (codigoEntidad == null || codigoEntidad.isBlank()) {
            return "—";
        }
        return switch (codigoEntidad.strip().toUpperCase()) {
            case "SESION" -> "Sesión";
            case "RUTA" -> "Ruta";
            case "VENTA" -> "Venta";
            case "MEDICAMENTO" -> "Medicamento";
            case "CATEGORIA" -> "Categoría";
            case "CLIENTE" -> "Cliente";
            case "PROVEEDOR" -> "Proveedor";
            case "USUARIO" -> "Usuario";
            case "LOTE" -> "Lote";
            case "ORDEN_COMPRA" -> "Orden de compra";
            case "AUDITORIA" -> "Auditoría";
            case "REPORTE_VENTAS" -> "Reporte de ventas";
            case "REPORTE_MAS_VENDIDOS" -> "Reporte más vendidos";
            case "REPORTE_MERMAS" -> "Reporte de mermas";
            case "REPORTE_INVENTARIO" -> "Reporte de inventario";
            case "REPORTE_COMPRAS" -> "Reporte de compras";
            default -> codigoEntidad;
        };
    }
}
