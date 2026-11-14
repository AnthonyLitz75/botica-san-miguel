package com.farmacia.repository;

import com.farmacia.model.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UsuarioRepositorio {

    private static final RowMapper<Usuario> MAPEO_USUARIO = (fila, numeroFila) -> new Usuario(
            fila.getInt("id_usuario"),
            fila.getString("nombre"),
            fila.getString("nombre_usuario"),
            fila.getString("clave_hash"),
            fila.getString("rol"),
            fila.getBoolean("activo"));

    private final JdbcTemplate jdbcTemplate;

    public UsuarioRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Usuario> listar() {
        String sql = """
                SELECT id_usuario, nombre, nombre_usuario,
                       clave_hash, rol, activo
                FROM usuario
                ORDER BY nombre ASC, id_usuario ASC
                """;
        return jdbcTemplate.query(sql, MAPEO_USUARIO);
    }

    public List<Usuario> listarActivos() {
        String sql = """
                SELECT id_usuario, nombre, nombre_usuario,
                       clave_hash, rol, activo
                FROM usuario
                WHERE activo = true
                ORDER BY nombre ASC
                """;
        return jdbcTemplate.query(sql, MAPEO_USUARIO);
    }

    public Optional<Usuario> buscarPorId(Integer idUsuario) {
        String sql = """
                SELECT id_usuario, nombre, nombre_usuario,
                       clave_hash, rol, activo
                FROM usuario
                WHERE id_usuario = ?
                """;
        List<Usuario> usuarios = jdbcTemplate.query(sql, MAPEO_USUARIO, idUsuario);
        return usuarios.stream().findFirst();
    }

    public Optional<Usuario> buscarPorNombreUsuario(String nombreUsuario) {
        String sql = """
                SELECT id_usuario, nombre, nombre_usuario,
                       clave_hash, rol, activo
                FROM usuario
                WHERE nombre_usuario = ?
                """;
        List<Usuario> usuarios = jdbcTemplate.query(sql, MAPEO_USUARIO, nombreUsuario);
        return usuarios.stream().findFirst();
    }

    public Integer insertar(String nombre, String nombreUsuario, String claveHash, String rol) {
        String sql = """
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES (?, ?, ?, ?, true)
                RETURNING id_usuario
                """;
        return jdbcTemplate.queryForObject(sql, Integer.class, nombre, nombreUsuario, claveHash, rol);
    }

    public boolean actualizar(Integer idUsuario, String nombre, String rol) {
        String sql = """
                UPDATE usuario
                SET nombre = ?, rol = ?
                WHERE id_usuario = ?
                """;
        int filas = jdbcTemplate.update(sql, nombre, rol, idUsuario);
        return filas > 0;
    }

    public boolean actualizarClave(Integer idUsuario, String nuevaClaveHash) {
        String sql = """
                UPDATE usuario
                SET clave_hash = ?
                WHERE id_usuario = ?
                """;
        int filas = jdbcTemplate.update(sql, nuevaClaveHash, idUsuario);
        return filas > 0;
    }

    public boolean cambiarEstado(Integer idUsuario, boolean activo) {
        String sql = """
                UPDATE usuario
                SET activo = ?
                WHERE id_usuario = ?
                """;
        int filas = jdbcTemplate.update(sql, activo, idUsuario);
        return filas > 0;
    }

    public int contarAdministradoresActivos() {
        String sql = "SELECT COUNT(*) FROM usuario WHERE rol = 'ADMINISTRADOR' AND activo = true";
        Integer cuenta = jdbcTemplate.queryForObject(sql, Integer.class);
        return cuenta == null ? 0 : cuenta;
    }
}
