package com.farmacia.repository;

import com.farmacia.model.Proveedor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.farmacia.dto.ProveedorFormulario;
import java.util.Optional;

import java.util.List;

@Repository
public class ProveedorRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public ProveedorRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Proveedor> listarActivos() {
        return listarPorEstado(true);
    }

    public List<Proveedor> listarPorEstado(boolean activo) {
        return buscarPorEstado("", activo);
    }

    public List<Proveedor> buscarPorEstado(String texto, boolean activo) {
        String sql = """
                SELECT id_proveedor, ruc, razon_social,
                       telefono, correo, direccion, activo
                FROM proveedor
                WHERE activo = ?
                  AND (
                      POSITION(LOWER(?) IN LOWER(ruc)) > 0
                      OR POSITION(LOWER(?) IN LOWER(razon_social)) > 0
                  )
                ORDER BY razon_social, id_proveedor
                """;

        return jdbcTemplate.query(
                sql,
                (fila, numeroFila) -> new Proveedor(
                        fila.getInt("id_proveedor"),
                        fila.getString("ruc"),
                        fila.getString("razon_social"),
                        fila.getString("telefono"),
                        fila.getString("correo"),
                        fila.getString("direccion"),
                        fila.getBoolean("activo")),
                activo,
                texto,
                texto);
    }

    public void guardar(ProveedorFormulario formulario) {
        String sql = """
                INSERT INTO proveedor (
                    ruc,
                    razon_social,
                    telefono,
                    correo,
                    direccion,
                    activo
                )
                VALUES (?, ?, ?, ?, ?, TRUE)
                """;

        jdbcTemplate.update(
                sql,
                formulario.ruc(),
                formulario.razonSocial(),
                formulario.telefono(),
                formulario.correo(),
                formulario.direccion());
    }

    public Optional<Proveedor> buscarPorId(Integer id) {
        String sql = """
                SELECT id_proveedor, ruc, razon_social,
                       telefono, correo, direccion, activo
                FROM proveedor
                WHERE id_proveedor = ?
                """;

        List<Proveedor> proveedores = jdbcTemplate.query(
                sql,
                (fila, numeroFila) -> new Proveedor(
                        fila.getInt("id_proveedor"),
                        fila.getString("ruc"),
                        fila.getString("razon_social"),
                        fila.getString("telefono"),
                        fila.getString("correo"),
                        fila.getString("direccion"),
                        fila.getBoolean("activo")),
                id);

        return proveedores.stream().findFirst();
    }

    public int actualizar(Integer id, ProveedorFormulario formulario) {
        String sql = """
                UPDATE proveedor
                SET ruc = ?,
                    razon_social = ?,
                    telefono = ?,
                    correo = ?,
                    direccion = ?
                WHERE id_proveedor = ?
                """;

        return jdbcTemplate.update(
                sql,
                formulario.ruc(),
                formulario.razonSocial(),
                formulario.telefono(),
                formulario.correo(),
                formulario.direccion(),
                id);
    }

    public int desactivar(Integer id) {
        String sql = """
                UPDATE proveedor
                SET activo = FALSE
                WHERE id_proveedor = ?
                """;

        return jdbcTemplate.update(sql, id);
    }

    public int activar(Integer id) {
        String sql = """
                UPDATE proveedor
                SET activo = TRUE
                WHERE id_proveedor = ?
                """;

        return jdbcTemplate.update(sql, id);
    }

    public Optional<String> buscarTitularPorCorreo(String correo, Integer idExcluir) {
        if (correo == null || correo.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT razon_social
                FROM proveedor
                WHERE correo = ? AND (CAST(? AS INTEGER) IS NULL OR id_proveedor <> ?)
                LIMIT 1
                """;
        List<String> resultados = jdbcTemplate.query(
                sql, (rs, rowNum) -> rs.getString("razon_social"), correo, idExcluir, idExcluir);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public Optional<String> buscarTitularPorTelefono(String telefono, Integer idExcluir) {
        if (telefono == null || telefono.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT razon_social
                FROM proveedor
                WHERE telefono = ? AND (CAST(? AS INTEGER) IS NULL OR id_proveedor <> ?)
                LIMIT 1
                """;
        List<String> resultados = jdbcTemplate.query(
                sql, (rs, rowNum) -> rs.getString("razon_social"), telefono, idExcluir, idExcluir);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }
}