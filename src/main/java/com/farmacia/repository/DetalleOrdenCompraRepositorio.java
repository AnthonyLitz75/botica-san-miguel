package com.farmacia.repository;

import com.farmacia.model.DetalleOrdenCompra;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import com.farmacia.model.DetalleOrdenCompraVista;
import com.farmacia.model.LineaRecepcionVista;

import java.util.List;

@Repository
public class DetalleOrdenCompraRepositorio {

        private final JdbcTemplate jdbcTemplate;

        public DetalleOrdenCompraRepositorio(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
        }

        public List<DetalleOrdenCompra> listarPorOrden(Integer idOrden) {
                String sql = """
                                SELECT id_detalle_orden, id_orden, id_medicamento,
                                       cantidad_solicitada, costo_unitario,
                                       importe_estimado
                                FROM detalle_orden_compra
                                WHERE id_orden = ?
                                ORDER BY id_detalle_orden
                                """;

                return jdbcTemplate.query(
                                sql,
                                (fila, numeroFila) -> new DetalleOrdenCompra(
                                                fila.getInt("id_detalle_orden"),
                                                fila.getInt("id_orden"),
                                                fila.getInt("id_medicamento"),
                                                fila.getInt("cantidad_solicitada"),
                                                fila.getBigDecimal("costo_unitario"),
                                                fila.getBigDecimal("importe_estimado")),
                                idOrden);
        }

        public void guardar(
                        Integer idOrden,
                        Integer idMedicamento,
                        Integer cantidadSolicitada,
                        BigDecimal costoUnitario) {

                BigDecimal importeEstimado = costoUnitario.multiply(
                                BigDecimal.valueOf(cantidadSolicitada));

                String sql = """
                                INSERT INTO detalle_orden_compra (
                                    id_orden,
                                    id_medicamento,
                                    cantidad_solicitada,
                                    costo_unitario,
                                    importe_estimado
                                )
                                VALUES (?, ?, ?, ?, ?)
                                """;

                jdbcTemplate.update(
                                sql,
                                idOrden,
                                idMedicamento,
                                cantidadSolicitada,
                                costoUnitario,
                                importeEstimado);
        }

        public List<LineaRecepcionVista> listarLineasRecepcion(Integer idOrden) {
                String sql = """
                                SELECT d.id_detalle_orden, d.id_medicamento, m.codigo, m.nombre,
                                       d.cantidad_solicitada,
                                       COALESCE(SUM(CASE WHEN mi.tipo = 'ENTRADA' AND mi.id_orden = d.id_orden
                                           THEN dm.cantidad ELSE 0 END), 0)::INTEGER AS cantidad_recibida
                                FROM detalle_orden_compra d
                                JOIN medicamento m ON m.id_medicamento = d.id_medicamento
                                LEFT JOIN detalle_movimiento dm ON dm.id_detalle_orden = d.id_detalle_orden
                                LEFT JOIN movimiento_inventario mi ON mi.id_movimiento = dm.id_movimiento
                                WHERE d.id_orden = ?
                                GROUP BY d.id_detalle_orden, d.id_medicamento, m.codigo, m.nombre, d.cantidad_solicitada
                                ORDER BY d.id_detalle_orden
                                """;
                return jdbcTemplate.query(sql, (fila, numeroFila) -> {
                        int solicitada = fila.getInt("cantidad_solicitada");
                        int recibida = fila.getInt("cantidad_recibida");
                        return new LineaRecepcionVista(fila.getInt("id_detalle_orden"),
                                fila.getInt("id_medicamento"), fila.getString("codigo"),
                                fila.getString("nombre"), solicitada, recibida, solicitada - recibida);
                }, idOrden);
        }

        public List<DetalleOrdenCompraVista> listarVistaPorOrden(
                        Integer idOrden) {

                String sql = """
                                WITH recibidas AS (
                                    SELECT dm.id_detalle_orden, SUM(dm.cantidad)::INTEGER AS cantidad_recibida
                                    FROM detalle_movimiento dm
                                    JOIN movimiento_inventario mi ON mi.id_movimiento = dm.id_movimiento
                                    WHERE mi.tipo = 'ENTRADA' AND mi.id_orden = ?
                                    GROUP BY dm.id_detalle_orden
                                )
                                SELECT d.id_detalle_orden, d.id_medicamento,
                                       m.codigo, m.nombre, m.unidad_control,
                                       d.cantidad_solicitada, d.costo_unitario,
                                       d.importe_estimado,
                                       COALESCE(r.cantidad_recibida, 0)::INTEGER AS cantidad_recibida,
                                       (d.cantidad_solicitada - COALESCE(r.cantidad_recibida, 0))::INTEGER AS cantidad_pendiente
                                FROM detalle_orden_compra d
                                JOIN medicamento m
                                  ON m.id_medicamento = d.id_medicamento
                                LEFT JOIN recibidas r ON r.id_detalle_orden = d.id_detalle_orden
                                WHERE d.id_orden = ?
                                ORDER BY d.id_detalle_orden
                                """;

                return jdbcTemplate.query(
                                sql,
                                (fila, numeroFila) -> new DetalleOrdenCompraVista(
                                                fila.getInt("id_detalle_orden"),
                                                fila.getInt("id_medicamento"),
                                                fila.getString("codigo"),
                                                fila.getString("nombre"),
                                                fila.getString("unidad_control"),
                                                fila.getInt("cantidad_solicitada"),
                                                fila.getInt("cantidad_recibida"),
                                                fila.getInt("cantidad_pendiente"),
                                                fila.getBigDecimal("costo_unitario"),
                                                fila.getBigDecimal("importe_estimado")),
                                idOrden,
                                idOrden);
        }
}
