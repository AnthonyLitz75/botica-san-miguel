package com.farmacia.repository;

import com.farmacia.model.OrdenCompra;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class OrdenCompraRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public OrdenCompraRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OrdenCompra> listar() {
        String sql = """
                SELECT id_orden, numero_orden, id_usuario,
                       id_proveedor, fecha_hora, estado,
                       total_estimado, observaciones
                FROM orden_compra
                ORDER BY fecha_hora DESC, id_orden DESC
                """;

        return jdbcTemplate.query(
                sql,
                (fila, numeroFila) -> new OrdenCompra(
                        fila.getInt("id_orden"),
                        fila.getString("numero_orden"),
                        fila.getInt("id_usuario"),
                        fila.getInt("id_proveedor"),
                        fila.getObject("fecha_hora", LocalDateTime.class),
                        fila.getString("estado"),
                        fila.getBigDecimal("total_estimado"),
                        fila.getString("observaciones")));
    }

    public Optional<OrdenCompra> buscarPorId(Integer idOrden) {
        String sql = """
                SELECT id_orden, numero_orden, id_usuario,
                       id_proveedor, fecha_hora, estado,
                       total_estimado, observaciones
                FROM orden_compra
                WHERE id_orden = ?
                """;

        List<OrdenCompra> ordenes = jdbcTemplate.query(
                sql,
                (fila, numeroFila) -> new OrdenCompra(
                        fila.getInt("id_orden"),
                        fila.getString("numero_orden"),
                        fila.getInt("id_usuario"),
                        fila.getInt("id_proveedor"),
                        fila.getObject("fecha_hora", LocalDateTime.class),
                        fila.getString("estado"),
                        fila.getBigDecimal("total_estimado"),
                        fila.getString("observaciones")),
                idOrden);

        return ordenes.stream().findFirst();
    }

    public Integer guardar(
            String numeroOrden,
            Integer idUsuario,
            Integer idProveedor,
            BigDecimal totalEstimado,
            String observaciones) {

        String sql = """
                INSERT INTO orden_compra (
                    numero_orden,
                    id_usuario,
                    id_proveedor,
                    estado,
                    total_estimado,
                    observaciones
                )
                VALUES (?, ?, ?, 'PENDIENTE', ?, ?)
                RETURNING id_orden
                """;

        return jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                numeroOrden,
                idUsuario,
                idProveedor,
                totalEstimado,
                observaciones);
    }
}