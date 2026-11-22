package com.farmacia.service;

import com.farmacia.exception.ValidacionContactoException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Validador unificado y reutilizable de unicidad de teléfono y correo para Clientes y Proveedores (Spec 023).
 */
public final class ValidadorUnicidadContacto {

    @FunctionalInterface
    public interface BuscadorTitular {
        Optional<String> buscar(String valor, Integer excluirId);
    }

    private ValidadorUnicidadContacto() {}

    /**
     * Valida que ni el correo ni el teléfono normalizados existan en otro registro.
     * Si ambos están duplicados, lanza una ValidacionContactoException con ambos errores.
     */
    public static void validar(
            BuscadorTitular buscadorCorreo,
            BuscadorTitular buscadorTelefono,
            String correoNormalizado,
            String telefonoNormalizado,
            Integer excluirId) {

        Map<String, String> errores = new LinkedHashMap<>();

        if (correoNormalizado != null) {
            buscadorCorreo.buscar(correoNormalizado, excluirId).ifPresent(titular -> {
                errores.put("correo", "Este correo ya está registrado a nombre de " + titular + ".");
            });
        }

        if (telefonoNormalizado != null) {
            buscadorTelefono.buscar(telefonoNormalizado, excluirId).ifPresent(titular -> {
                errores.put("telefono", "Este teléfono ya está registrado a nombre de " + titular + ".");
            });
        }

        if (!errores.isEmpty()) {
            throw new ValidacionContactoException(errores);
        }
    }

    /**
     * Traduce una violación de índice único de base de datos a ValidacionContactoException por campo.
     * Retorna null si la excepción no corresponde a un índice de contacto conocido.
     */
    public static ValidacionContactoException traducirExcepcionIndice(
            DataIntegrityViolationException ex,
            BuscadorTitular buscadorCorreo,
            BuscadorTitular buscadorTelefono,
            String correo,
            String telefono,
            Integer excluirId) {

        String mensaje = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

        if (mensaje.contains("uq_cliente_correo") || mensaje.contains("uq_proveedor_correo")) {
            String titular = buscadorCorreo.buscar(correo, excluirId).orElse("otro registro");
            return new ValidacionContactoException("correo", "Este correo ya está registrado a nombre de " + titular + ".");
        }

        if (mensaje.contains("uq_cliente_telefono") || mensaje.contains("uq_proveedor_telefono")) {
            String titular = buscadorTelefono.buscar(telefono, excluirId).orElse("otro registro");
            return new ValidacionContactoException("telefono", "Este teléfono ya está registrado a nombre de " + titular + ".");
        }

        return null;
    }
}
