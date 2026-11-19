package com.farmacia.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pruebas unitarias de AccionAuditoria para la spec 018 (CA-2.1).
 */
class AccionAuditoriaTest {

    @Test
    @DisplayName("Existen exactamente 16 acciones de auditoria")
    void conteoAcciones() {
        assertEquals(16, AccionAuditoria.values().length);
    }

    @ParameterizedTest
    @EnumSource(AccionAuditoria.class)
    @DisplayName("Todas las acciones tienen etiqueta no vacia y sin guion bajo")
    void todasLasAccionesTienenEtiquetaValida(AccionAuditoria accion) {
        String etiqueta = accion.etiqueta();
        assertNotNull(etiqueta, () -> "La accion " + accion.name() + " no debe tener etiqueta nula");
        assertFalse(etiqueta.isBlank(), () -> "La accion " + accion.name() + " debe tener etiqueta no vacia");
        assertFalse(etiqueta.contains("_"), () -> "La etiqueta de " + accion.name() + " no debe contener guion bajo: " + etiqueta);
    }

    @Test
    @DisplayName("Etiquetas legibles de acciones clave (CA-2.1)")
    void etiquetasEspecificas() {
        assertEquals("Inicio de sesión fallido", AccionAuditoria.INICIO_SESION_FALLIDO.etiqueta());
        assertEquals("Acceso denegado", AccionAuditoria.ACCESO_DENEGADO.etiqueta());
        assertEquals("Venta confirmada", AccionAuditoria.CONFIRMAR_VENTA.etiqueta());
        assertEquals("Inicio de sesión", AccionAuditoria.INICIO_SESION.etiqueta());
        assertEquals("Cierre de sesión", AccionAuditoria.CIERRE_SESION.etiqueta());
        assertEquals("Crear", AccionAuditoria.CREAR.etiqueta());
        assertEquals("Editar", AccionAuditoria.EDITAR.etiqueta());
        assertEquals("Activar", AccionAuditoria.ACTIVAR.etiqueta());
        assertEquals("Desactivar", AccionAuditoria.DESACTIVAR.etiqueta());
        assertEquals("Cancelar", AccionAuditoria.CANCELAR.etiqueta());
        assertEquals("Recepción", AccionAuditoria.RECEPCION.etiqueta());
        assertEquals("Salida", AccionAuditoria.SALIDA.etiqueta());
        assertEquals("Existencia inicial", AccionAuditoria.EXISTENCIA_INICIAL.etiqueta());
        assertEquals("Cambiar clave", AccionAuditoria.CAMBIAR_CLAVE.etiqueta());
        assertEquals("Cambiar rol", AccionAuditoria.CAMBIAR_ROL.etiqueta());
        assertEquals("Exportar", AccionAuditoria.EXPORTAR.etiqueta());
    }
}
