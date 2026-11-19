package com.farmacia.service;

/** Acciones de la bitácora (lista cerrada; coincide con el CHECK de la tabla auditoria). */
public enum AccionAuditoria {
    INICIO_SESION("Inicio de sesión"),
    INICIO_SESION_FALLIDO("Inicio de sesión fallido"),
    CIERRE_SESION("Cierre de sesión"),
    ACCESO_DENEGADO("Acceso denegado"),
    CREAR("Crear"),
    EDITAR("Editar"),
    ACTIVAR("Activar"),
    DESACTIVAR("Desactivar"),
    CANCELAR("Cancelar"),
    CONFIRMAR_VENTA("Venta confirmada"),
    RECEPCION("Recepción"),
    SALIDA("Salida"),
    EXISTENCIA_INICIAL("Existencia inicial"),
    CAMBIAR_CLAVE("Cambiar clave"),
    CAMBIAR_ROL("Cambiar rol"),
    EXPORTAR("Exportar");

    private final String etiqueta;

    AccionAuditoria(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
