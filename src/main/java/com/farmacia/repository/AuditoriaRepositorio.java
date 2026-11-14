package com.farmacia.repository;

import com.farmacia.model.EventoAuditoria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Acceso a la bitácora: solo inserción y consulta (la tabla rechaza UPDATE y DELETE). */
@Repository
public class AuditoriaRepositorio {
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<EventoAuditoria> MAPEO = (fila, numero) -> new EventoAuditoria(
            fila.getLong("id_auditoria"),
            fila.getObject("fecha_hora", LocalDateTime.class),
            fila.getObject("id_usuario", Integer.class),
            fila.getString("nombre_usuario"),
            fila.getString("accion"),
            fila.getString("entidad"),
            fila.getString("id_entidad"),
            fila.getString("resultado"),
            fila.getString("detalle"),
            fila.getString("ip_origen"));

    public AuditoriaRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserta un evento; el id del usuario se resuelve por su nombre (nulo si no existe). */
    public void insertar(LocalDateTime fechaHora, String nombreUsuario, String accion, String entidad,
            String idEntidad, String resultado, String detalle, String ipOrigen) {
        jdbcTemplate.update("""
                INSERT INTO auditoria (fecha_hora, id_usuario, nombre_usuario, accion, entidad, id_entidad,
                                       resultado, detalle, ip_origen)
                VALUES (?, (SELECT id_usuario FROM usuario WHERE nombre_usuario = ?), ?, ?, ?, ?, ?, ?, ?)
                """, fechaHora, nombreUsuario, nombreUsuario, accion, entidad, idEntidad, resultado, detalle,
                ipOrigen);
    }

    /** Filtros de la consulta; los nulos o vacíos no filtran. */
    public record Filtro(LocalDate desde, LocalDate hasta, String usuario, String accion, String entidad,
            String resultado) {
    }

    public List<EventoAuditoria> buscar(Filtro filtro, int limite, int desplazamiento) {
        List<Object> parametros = new ArrayList<>();
        String sql = "SELECT * FROM auditoria" + condiciones(filtro, parametros)
                + " ORDER BY fecha_hora DESC, id_auditoria DESC LIMIT ? OFFSET ?";
        parametros.add(limite);
        parametros.add(desplazamiento);
        return jdbcTemplate.query(sql, MAPEO, parametros.toArray());
    }

    public int contar(Filtro filtro) {
        List<Object> parametros = new ArrayList<>();
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria" + condiciones(filtro, parametros), Integer.class,
                parametros.toArray());
        return total == null ? 0 : total;
    }

    /**
     * Cláusula WHERE con fragmentos constantes y marcadores {@code ?} (constitución 2): ningún dato del
     * usuario se concatena en el SQL.
     */
    private static String condiciones(Filtro filtro, List<Object> parametros) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (filtro.desde() != null) {
            where.append(" AND fecha_hora >= ?");
            parametros.add(filtro.desde().atStartOfDay());
        }
        if (filtro.hasta() != null) {
            where.append(" AND fecha_hora < ?");
            parametros.add(filtro.hasta().plusDays(1).atStartOfDay());
        }
        if (tieneTexto(filtro.usuario())) {
            where.append(" AND nombre_usuario = ?");
            parametros.add(filtro.usuario().strip());
        }
        if (tieneTexto(filtro.accion())) {
            where.append(" AND accion = ?");
            parametros.add(filtro.accion().strip());
        }
        if (tieneTexto(filtro.entidad())) {
            where.append(" AND entidad = ?");
            parametros.add(filtro.entidad().strip());
        }
        if (tieneTexto(filtro.resultado())) {
            where.append(" AND resultado = ?");
            parametros.add(filtro.resultado().strip());
        }
        return where.toString();
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
