package com.farmacia.repository;

import com.farmacia.model.MovimientoInventario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.farmacia.model.DetalleMovimientoInventario;
import java.util.Optional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MovimientoInventarioRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public MovimientoInventarioRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MovimientoInventario> listar() {
        String sql = """
                SELECT id_movimiento, id_usuario, id_proveedor,
                       id_venta, id_orden, tipo, fecha_hora, motivo
                FROM movimiento_inventario
                ORDER BY fecha_hora DESC, id_movimiento DESC
                """;

        return jdbcTemplate.query(
                sql,
                (fila, numeroFila) -> new MovimientoInventario(
                        fila.getInt("id_movimiento"),
                        fila.getInt("id_usuario"),
                        fila.getObject("id_proveedor", Integer.class),
                        fila.getObject("id_venta", Integer.class),
                        fila.getObject("id_orden", Integer.class),
                        fila.getString("tipo"),
                        fila.getObject("fecha_hora", LocalDateTime.class),
                        fila.getString("motivo")));
    }

    public List<DetalleMovimientoInventario> listarDetalles(Integer idMovimiento) {
        String sql = """
                SELECT l.id_lote,
                       m.codigo AS codigo_medicamento,
                       m.nombre AS nombre_medicamento,
                       l.numero_lote,
                       m.unidad_control,
                       d.cantidad
                FROM detalle_movimiento d
                JOIN lote l ON l.id_lote = d.id_lote
                JOIN medicamento m ON m.id_medicamento = l.id_medicamento
                WHERE d.id_movimiento = ?
                ORDER BY m.nombre, l.numero_lote, l.id_lote
                """;

        return jdbcTemplate.query(
                sql,
                (fila, numeroFila) -> new DetalleMovimientoInventario(
                        fila.getInt("id_lote"),
                        fila.getString("codigo_medicamento"),
                        fila.getString("nombre_medicamento"),
                        fila.getString("numero_lote"),
                        fila.getString("unidad_control"),
                        fila.getInt("cantidad")),
                idMovimiento);
    }

    public Optional<MovimientoInventario> buscarPorId(Integer idMovimiento) {
        String sql = """
                SELECT id_movimiento, id_usuario, id_proveedor,
                       id_venta, id_orden, tipo, fecha_hora, motivo
                FROM movimiento_inventario
                WHERE id_movimiento = ?
                """;

        List<MovimientoInventario> movimientos = jdbcTemplate.query(
                sql,
                (fila, numeroFila) -> new MovimientoInventario(
                        fila.getInt("id_movimiento"),
                        fila.getInt("id_usuario"),
                        fila.getObject("id_proveedor", Integer.class),
                        fila.getObject("id_venta", Integer.class),
                        fila.getObject("id_orden", Integer.class),
                        fila.getString("tipo"),
                        fila.getObject("fecha_hora", LocalDateTime.class),
                        fila.getString("motivo")),
                idMovimiento);

        return movimientos.stream().findFirst();
    }

    public Integer registrarSalida(Integer idUsuario, String motivo) {
        String sql = """
                INSERT INTO movimiento_inventario (
                    id_usuario,
                    tipo,
                    motivo
                )
                VALUES (?, 'SALIDA', ?)
                RETURNING id_movimiento
                """;

        return jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                idUsuario,
                motivo);
    }

    public void registrarDetalle(
            Integer idMovimiento,
            Integer idLote,
            Integer cantidad) {

        String sql = """
                INSERT INTO detalle_movimiento (
                    id_movimiento,
                    id_lote,
                    cantidad
                )
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                idMovimiento,
                idLote,
                cantidad);
    }
}