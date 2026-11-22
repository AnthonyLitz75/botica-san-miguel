package com.farmacia.repository;

import com.farmacia.dto.ClienteFormulario;
import com.farmacia.model.Cliente;
import com.farmacia.model.ClienteResumenVista;
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

    public List<ClienteResumenVista> listar(String texto) {
        String sql = """
                SELECT c.id_cliente, c.tipo_documento, c.numero_documento, c.nombre_completo, c.telefono, c.correo,
                       COUNT(v.id_venta) AS compras
                FROM cliente c
                LEFT JOIN venta v ON v.id_cliente = c.id_cliente
                WHERE POSITION(LOWER(?) IN LOWER(c.nombre_completo)) > 0
                   OR POSITION(LOWER(?) IN LOWER(c.numero_documento)) > 0
                GROUP BY c.id_cliente, c.tipo_documento, c.numero_documento, c.nombre_completo, c.telefono, c.correo
                ORDER BY c.nombre_completo, c.id_cliente
                """;
        return jdbcTemplate.query(sql, (fila, numeroFila) -> new ClienteResumenVista(fila.getInt("id_cliente"),
                fila.getString("tipo_documento"), fila.getString("numero_documento"),
                fila.getString("nombre_completo"), fila.getString("telefono"), fila.getString("correo"),
                fila.getLong("compras")),
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

    public Optional<String> buscarTitularPorCorreo(String correo, Integer idExcluir) {
        if (correo == null || correo.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT nombre_completo
                FROM cliente
                WHERE correo = ? AND (CAST(? AS INTEGER) IS NULL OR id_cliente <> ?)
                LIMIT 1
                """;
        List<String> resultados = jdbcTemplate.query(
                sql, (rs, rowNum) -> rs.getString("nombre_completo"), correo, idExcluir, idExcluir);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    public Optional<String> buscarTitularPorTelefono(String telefono, Integer idExcluir) {
        if (telefono == null || telefono.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT nombre_completo
                FROM cliente
                WHERE telefono = ? AND (CAST(? AS INTEGER) IS NULL OR id_cliente <> ?)
                LIMIT 1
                """;
        List<String> resultados = jdbcTemplate.query(
                sql, (rs, rowNum) -> rs.getString("nombre_completo"), telefono, idExcluir, idExcluir);
        return resultados.isEmpty() ? Optional.empty() : Optional.of(resultados.get(0));
    }

    private Cliente mapear(Integer id, String tipo, String numero, String nombre, String telefono, String correo) {
        return new Cliente(id, tipo, numero, nombre, telefono, correo);
    }
}
