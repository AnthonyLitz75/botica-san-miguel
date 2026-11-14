package com.farmacia.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 014 (CA-1.1, CA-1.3): bitácora de solo inserción con acciones y resultados de lista cerrada. */
class AuditoriaEsquemaPersistenciaTest extends AbstractRepositorioIntegrationTest {

    private long insertar(String usuario, String accion, String resultado) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO auditoria (fecha_hora, nombre_usuario, accion, entidad, id_entidad, resultado, detalle)
                VALUES (?, ?, ?, 'MEDICAMENTO', '1', ?, 'prueba')
                RETURNING id_auditoria
                """, Long.class, LocalDateTime.of(2026, 11, 13, 10, 0), usuario, accion, resultado);
    }

    private String usuarioUnico() {
        return "aud-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("CA-1.1: se puede insertar un evento")
    void insertaEvento() {
        String usuario = usuarioUnico();
        insertar(usuario, "EDITAR", "EXITO");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE nombre_usuario = ?", Integer.class, usuario));
    }

    @Test
    @DisplayName("CA-1.1: UPDATE sobre la bitácora falla en la base")
    void updateRechazado() {
        long id = insertar(usuarioUnico(), "EDITAR", "EXITO");
        var error = assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE auditoria SET detalle = 'alterado' WHERE id_auditoria = ?", id));
        assertTrue(mensajeCompleto(error).contains("solo insercion"), mensajeCompleto(error));
    }

    @Test
    @DisplayName("CA-1.1: DELETE sobre la bitácora falla en la base")
    void deleteRechazado() {
        long id = insertar(usuarioUnico(), "EDITAR", "EXITO");
        var error = assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "DELETE FROM auditoria WHERE id_auditoria = ?", id));
        assertTrue(mensajeCompleto(error).contains("solo insercion"), mensajeCompleto(error));
    }

    @Test
    @DisplayName("CA-1.3: una acción fuera de la lista se rechaza por CHECK")
    void accionFueraDeLista() {
        assertThrows(DataIntegrityViolationException.class, () -> insertar(usuarioUnico(), "BORRAR_TODO", "EXITO"));
    }

    @Test
    @DisplayName("CA-1.3: un resultado fuera de la lista se rechaza por CHECK")
    void resultadoFueraDeLista() {
        assertThrows(DataIntegrityViolationException.class, () -> insertar(usuarioUnico(), "EDITAR", "QUIZAS"));
    }

    @Test
    @DisplayName("Existen los índices por fecha y por usuario")
    void indices() {
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'auditoria' AND indexname IN ('idx_auditoria_fecha', 'idx_auditoria_usuario')
                """, Integer.class));
    }

    private static String mensajeCompleto(Throwable error) {
        StringBuilder texto = new StringBuilder();
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            texto.append(actual.getMessage()).append(" | ");
        }
        return texto.toString();
    }
}
