package com.farmacia.repository;

import com.farmacia.dto.ClienteFormulario;
import com.farmacia.model.Cliente;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClienteRepositorio {
    private final JdbcTemplate jdbcTemplate;

    public ClienteRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Cliente> listar(String texto) {
        String sql = """
                SELECT id_cliente, tipo_documento, numero_documento, nombre_completo, telefono, correo
                FROM cliente
                WHERE POSITION(LOWER(?) IN LOWER(nombre_completo)) > 0
                   OR POSITION(LOWER(?) IN LOWER(numero_documento)) > 0
                ORDER BY nombre_completo, id_cliente
                """;
        return jdbcTemplate.query(sql, (fila, numeroFila) -> mapear(fila.getInt("id_cliente"),
                fila.getString("tipo_documento"), fila.getString("numero_documento"),
                fila.getString("nombre_completo"), fila.getString("telefono"), fila.getString("correo")),
                texto, texto);
    }

    public Optional<Cliente> buscarPorId(Integer id) {
        String sql = """
                SELECT id_cliente, tipo_documento, numero_documento, nombre_completo, telefono, correo
                FROM cliente WHERE id_cliente = ?
                """;
        return jdbcTemplate.query(sql, (fila, numeroFila) -> mapear(fila.getInt("id_cliente"),
                fila.getString("tipo_documento"), fila.getString("numero_documento"),
                fila.getString("nombre_completo"), fila.getString("telefono"), fila.getString("correo")), id)
                .stream().findFirst();
    }

    public Optional<Cliente> buscarPorDocumento(String tipoDocumento, String numeroDocumento) {
        String sql = """
                SELECT id_cliente, tipo_documento, numero_documento, nombre_completo, telefono, correo
                FROM cliente WHERE tipo_documento = ? AND numero_documento = ?
                """;
        return jdbcTemplate.query(sql, (fila, numeroFila) -> mapear(fila.getInt("id_cliente"),
                fila.getString("tipo_documento"), fila.getString("numero_documento"),
                fila.getString("nombre_completo"), fila.getString("telefono"), fila.getString("correo")),
                tipoDocumento, numeroDocumento).stream().findFirst();
    }

    public boolean existeDocumento(String tipoDocumento, String numeroDocumento, Integer idExcluir) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM cliente
                    WHERE tipo_documento = ? AND numero_documento = ?
                      AND (CAST(? AS INTEGER) IS NULL OR id_cliente <> ?)
                )
                """;
        Boolean existe = jdbcTemplate.queryForObject(sql, Boolean.class,
                tipoDocumento, numeroDocumento, idExcluir, idExcluir);
        return Boolean.TRUE.equals(existe);
    }

    public Integer guardar(ClienteFormulario formulario) {
        String sql = """
                INSERT INTO cliente(tipo_documento, numero_documento, nombre_completo, telefono, correo)
                VALUES (?, ?, ?, ?, ?) RETURNING id_cliente
                """;
        return jdbcTemplate.queryForObject(sql, Integer.class, formulario.tipoDocumento(),
                formulario.numeroDocumento(), formulario.nombreCompleto(), formulario.telefono(), formulario.correo());
    }

    public int actualizar(Integer id, ClienteFormulario formulario) {
        String sql = """
                UPDATE cliente
                SET tipo_documento = ?, numero_documento = ?, nombre_completo = ?, telefono = ?, correo = ?
                WHERE id_cliente = ?
                """;
        return jdbcTemplate.update(sql, formulario.tipoDocumento(), formulario.numeroDocumento(),
                formulario.nombreCompleto(), formulario.telefono(), formulario.correo(), id);
    }

    private Cliente mapear(Integer id, String tipo, String numero, String nombre, String telefono, String correo) {
        return new Cliente(id, tipo, numero, nombre, telefono, correo);
    }
}
