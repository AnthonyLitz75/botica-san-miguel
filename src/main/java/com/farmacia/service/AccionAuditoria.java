package com.farmacia.service;

/** Acciones de la bitácora (lista cerrada; coincide con el CHECK de la tabla auditoria). */
public enum AccionAuditoria {
    INICIO_SESION,
    INICIO_SESION_FALLIDO,
    CIERRE_SESION,
    ACCESO_DENEGADO,
    CREAR,
    EDITAR,
    ACTIVAR,
    DESACTIVAR,
    CANCELAR,
    CONFIRMAR_VENTA,
    RECEPCION,
    SALIDA,
    EXISTENCIA_INICIAL,
    CAMBIAR_CLAVE,
    CAMBIAR_ROL,
    EXPORTAR
}
