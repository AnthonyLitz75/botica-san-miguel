package com.farmacia.repository;

import com.farmacia.model.Categoria;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JDBC para operaciones CRUD y consultas agregadas sobre la tabla {@code categoria}.
 * Todas las sentencias utilizan sentencias SQL preparadas estrictamente parametrizadas.
 */
@Repository
public class CategoriaRepositorio {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Categoria> ROW_MAPPER = (ResultSet rs, int rowNum) -> new Categoria(
            rs.getInt("id_categoria"),
            rs.getString("nombre"),
            rs.getObject("id_padre", Integer.class),
            rs.getBoolean("activo")
    );

    public CategoriaRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Categoria> listarTodas() {
        String sql = """
                SELECT id_categoria, nombre, id_padre, activo
                FROM categoria
                ORDER BY id_categoria
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public List<Categoria> listarActivas() {
        String sql = """
                SELECT id_categoria, nombre, id_padre, activo
                FROM categoria
                WHERE activo = TRUE
                ORDER BY id_categoria
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public Optional<Categoria> buscarPorId(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id_categoria, nombre, id_padre, activo
                FROM categoria
                WHERE id_categoria = ?
                """;
        try {
            Categoria categoria = jdbcTemplate.queryForObject(sql, ROW_MAPPER, id);
            return Optional.ofNullable(categoria);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Categoria> buscarPorPadreYNombre(Integer idPadre, String nombre) {
        if (nombre == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id_categoria, nombre, id_padre, activo
                FROM categoria
                WHERE COALESCE(id_padre, 0) = COALESCE(?, 0)
                  AND LOWER(nombre) = LOWER(?)
                """;
        try {
            Categoria categoria = jdbcTemplate.queryForObject(sql, ROW_MAPPER, idPadre, nombre.trim());
            return Optional.ofNullable(categoria);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Integer guardar(String nombre, Integer idPadre) {
        String sql = """
                INSERT INTO categoria (nombre, id_padre, activo)
                VALUES (?, ?, TRUE)
                RETURNING id_categoria
                """;
        return jdbcTemplate.queryForObject(sql, Integer.class, nombre.trim(), idPadre);
    }

    public void actualizar(Integer id, String nombre, Integer idPadre) {
        String sql = """
                UPDATE categoria
                SET nombre = ?, id_padre = ?
                WHERE id_categoria = ?
                """;
        jdbcTemplate.update(sql, nombre.trim(), idPadre, id);
    }

    public void actualizarEstado(Integer id, boolean activo) {
        String sql = """
                UPDATE categoria
                SET activo = ?
                WHERE id_categoria = ?
                """;
        jdbcTemplate.update(sql, activo, id);
    }

    public int contarHijosActivos(Integer idCategoria) {
        String sql = """
                SELECT COUNT(*)
                FROM categoria
                WHERE id_padre = ? AND activo = TRUE
                """;
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, idCategoria);
        return total != null ? total : 0;
    }

    public int contarMedicamentosActivos(Integer idCategoria) {
        String sql = """
                SELECT COUNT(*)
                FROM medicamento
                WHERE id_categoria = ? AND activo = TRUE
                """;
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, idCategoria);
        return total != null ? total : 0;
    }
}
