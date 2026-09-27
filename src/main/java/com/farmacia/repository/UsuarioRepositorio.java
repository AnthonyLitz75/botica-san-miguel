package com.farmacia.repository;

import com.farmacia.model.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UsuarioRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public UsuarioRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Usuario> buscarPorNombreUsuario(String nombreUsuario) {
        String sql = """
                SELECT id_usuario, nombre, nombre_usuario,
                       clave_hash, rol, activo
                FROM usuario
                WHERE nombre_usuario = ?
                """;

        List<Usuario> usuarios = jdbcTemplate.query(
                sql,
                (fila, numeroFila) -> new Usuario(
                        fila.getInt("id_usuario"),
                        fila.getString("nombre"),
                        fila.getString("nombre_usuario"),
                        fila.getString("clave_hash"),
                        fila.getString("rol"),
                        fila.getBoolean("activo")),
                nombreUsuario);

        return usuarios.stream().findFirst();
    }
}