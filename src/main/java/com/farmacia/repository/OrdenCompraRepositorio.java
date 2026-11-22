package com.farmacia.repository;

import com.farmacia.model.OrdenCompraListadoVista;

import com.farmacia.model.OrdenCompra;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class OrdenCompraRepositorio {

        private final JdbcTemplate jdbcTemplate;

        public OrdenCompraRepositorio(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
        }

        public int contar(String estado) {
                StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM orden_compra oc");
                List<Object> parametros = new ArrayList<>();
                if (estado != null && !estado.isBlank()) {
                        sql.append(" WHERE oc.estado = ?");
                        parametros.add(estado.strip());
                }
                Integer total = jdbcTemplate.queryForObject(sql.toString(), Integer.class, parametros.toArray());
                return total != null ? total : 0;
        }

        public List<OrdenCompraListadoVista> listarVista(String estado, int limite, int offset) {
                StringBuilder sql = new StringBuilder("""
                                SELECT oc.id_orden, oc.numero_orden, oc.id_usuario, oc.id_proveedor,
                                       p.razon_social AS razon_social_proveedor,
                                       oc.fecha_hora, oc.estado, oc.total_estimado, oc.observaciones
                                FROM orden_compra oc
                                LEFT JOIN proveedor p ON oc.id_proveedor = p.id_proveedor
                                """);

                List<Object> parametros = new ArrayList<>();
                if (estado != null && !estado.isBlank()) {
                        sql.append(" WHERE oc.estado = ?");
                        parametros.add(estado.strip());
                }
                sql.append(" ORDER BY oc.fecha_hora DESC, oc.id_orden DESC LIMIT ? OFFSET ?");
                parametros.add(limite);
                parametros.add(offset);

                return jdbcTemplate.query(
                                sql.toString(),
                                (fila, numeroFila) -> new OrdenCompraListadoVista(
                                                fila.getInt("id_orden"),
                                                fila.getString("numero_orden"),
                                                fila.getInt("id_usuario"),
                                                fila.getInt("id_proveedor"),
                                                fila.getString("razon_social_proveedor"),
                                                fila.getObject("fecha_hora", LocalDateTime.class),
                                                fila.getString("estado"),
                                                fila.getBigDecimal("total_estimado"),
                                                fila.getString("observaciones")),
                                parametros.toArray());
        }

        public List<OrdenCompraListadoVista> listarVista(String estado) {
                StringBuilder sql = new StringBuilder("""
                                SELECT oc.id_orden, oc.numero_orden, oc.id_usuario, oc.id_proveedor,
                                       p.razon_social AS razon_social_proveedor,
                                       oc.fecha_hora, oc.estado, oc.total_estimado, oc.observaciones
                                FROM orden_compra oc
                                LEFT JOIN proveedor p ON oc.id_proveedor = p.id_proveedor
                                """);

                List<Object> parametros = new ArrayList<>();
                if (estado != null && !estado.isBlank()) {
                        sql.append(" WHERE oc.estado = ?");
                        parametros.add(estado.strip());
                }
                sql.append(" ORDER BY oc.fecha_hora DESC, oc.id_orden DESC");

                return jdbcTemplate.query(
                                sql.toString(),
                                (fila, numeroFila) -> new OrdenCompraListadoVista(
                                                fila.getInt("id_orden"),
                                                fila.getString("numero_orden"),
                                                fila.getInt("id_usuario"),
                                                fila.getInt("id_proveedor"),
                                                fila.getString("razon_social_proveedor"),
                                                fila.getObject("fecha_hora", LocalDateTime.class),
                                                fila.getString("estado"),
                                                fila.getBigDecimal("total_estimado"),
                                                fila.getString("observaciones")),
                                parametros.toArray());
        }

        public List<OrdenCompraListadoVista> listarVista() {
                return listarVista(null);
        }

        public Optional<OrdenCompraListadoVista> buscarVistaPorId(Integer idOrden) {
                String sql = """
                                SELECT oc.id_orden, oc.numero_orden, oc.id_usuario, oc.id_proveedor,
                                       p.razon_social AS razon_social_proveedor,
                                       oc.fecha_hora, oc.estado, oc.total_estimado, oc.observaciones
                                FROM orden_compra oc
                                LEFT JOIN proveedor p ON oc.id_proveedor = p.id_proveedor
                                WHERE oc.id_orden = ?
                                """;

                List<OrdenCompraListadoVista> ordenes = jdbcTemplate.query(
                                sql,
                                (fila, numeroFila) -> new OrdenCompraListadoVista(
                                                fila.getInt("id_orden"),
                                                fila.getString("numero_orden"),
                                                fila.getInt("id_usuario"),
                                                fila.getInt("id_proveedor"),
                                                fila.getString("razon_social_proveedor"),
                                                fila.getObject("fecha_hora", LocalDateTime.class),
                                                fila.getString("estado"),
                                                fila.getBigDecimal("total_estimado"),
                                                fila.getString("observaciones")),
                                idOrden);

                return ordenes.stream().findFirst();
        }

        public Map<String, Integer> contarPorEstado() {
                String sql = """
                                SELECT estado, COUNT(*) as cantidad
                                FROM orden_compra
                                GROUP BY estado
                                """;
                Map<String, Integer> conteos = new HashMap<>();
                conteos.put("TODAS", 0);
                conteos.put("PENDIENTE", 0);
                conteos.put("PARCIALMENTE_RECIBIDA", 0);
                conteos.put("RECIBIDA", 0);
                conteos.put("CANCELADA", 0);

                jdbcTemplate.query(sql, rs -> {
                        String estado = rs.getString("estado");
                        int cantidad = rs.getInt("cantidad");
                        conteos.put(estado, cantidad);
                });

                int total = conteos.values().stream().mapToInt(Integer::intValue).sum();
                conteos.put("TODAS", total);
                return conteos;
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

        public Optional<OrdenCompra> buscarPorIdParaActualizar(Integer idOrden) {
                String sql = """
                                SELECT id_orden, numero_orden, id_usuario, id_proveedor,
                                       fecha_hora, estado, total_estimado, observaciones
                                FROM orden_compra WHERE id_orden = ? FOR UPDATE
                                """;
                List<OrdenCompra> ordenes = jdbcTemplate.query(sql,
                        (fila, numeroFila) -> new OrdenCompra(fila.getInt("id_orden"),
                                fila.getString("numero_orden"), fila.getInt("id_usuario"),
                                fila.getInt("id_proveedor"), fila.getObject("fecha_hora", LocalDateTime.class),
                                fila.getString("estado"), fila.getBigDecimal("total_estimado"),
                                fila.getString("observaciones")), idOrden);
                return ordenes.stream().findFirst();
        }

        public int actualizarEstado(Integer idOrden, String estado) {
                String sql = """
                                UPDATE orden_compra SET estado = ?
                                WHERE id_orden = ? AND estado IN ('PENDIENTE', 'PARCIALMENTE_RECIBIDA')
                                """;
                return jdbcTemplate.update(sql, estado, idOrden);
        }

        public boolean tieneRecepciones(Integer idOrden) {
                String sql = """
                                SELECT EXISTS (
                                    SELECT 1 FROM movimiento_inventario
                                    WHERE id_orden = ? AND tipo = 'ENTRADA'
                                )
                                """;
                return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, idOrden));
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
