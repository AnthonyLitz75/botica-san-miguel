package com.farmacia.repository;

import com.farmacia.dto.FiltroMovimientosFormulario;
import com.farmacia.model.DetalleMovimientoInventario;
import com.farmacia.model.MovimientoInventario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MovimientoInventarioRepositorio {

        private final JdbcTemplate jdbcTemplate;

        private static final RowMapper<MovimientoInventario> MAPPER_MOVIMIENTO = (fila, numeroFila) -> {
                Object raw = fila.getObject("cantidad_medicamento");
                Integer cantidadMedicamento = (raw instanceof Number n) ? n.intValue() : null;
                return new MovimientoInventario(
                        fila.getInt("id_movimiento"),
                        fila.getInt("id_usuario"),
                        fila.getObject("id_proveedor", Integer.class),
                        fila.getObject("id_venta", Integer.class),
                        fila.getObject("id_orden", Integer.class),
                        fila.getString("tipo"),
                        fila.getObject("fecha_hora", LocalDateTime.class),
                        fila.getString("motivo"),
                        fila.getString("origen"),
                        fila.getString("responsable"),
                        cantidadMedicamento);
        };

        public MovimientoInventarioRepositorio(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
        }

        /**
         * Lista movimientos paginados con filtros avanzados (spec 005, US-2, US-3, RF-002).
         */
        public List<MovimientoInventario> listar(FiltroMovimientosFormulario filtro, int limite, int offset) {
                List<Object> parametros = new ArrayList<>();
                String selectCantidad;
                if (filtro != null && filtro.idMedicamento() != null) {
                        selectCantidad = """
                                , (SELECT COALESCE(SUM(d.cantidad), 0)::integer
                                   FROM detalle_movimiento d
                                   JOIN lote l ON l.id_lote = d.id_lote
                                   WHERE d.id_movimiento = m.id_movimiento
                                     AND l.id_medicamento = ?) AS cantidad_medicamento
                                """;
                        parametros.add(filtro.idMedicamento());
                } else {
                        selectCantidad = ", NULL::integer AS cantidad_medicamento\n";
                }

                String sql = """
                                SELECT m.id_movimiento, m.id_usuario, o.id_proveedor,
                                       m.id_venta, m.id_orden, m.tipo, m.fecha_hora, m.motivo,
                                       CASE 
                                           WHEN m.tipo = 'ENTRADA' AND m.id_orden IS NOT NULL THEN 'Recepción de compra' 
                                           WHEN m.tipo = 'ENTRADA' THEN 'Existencia inicial' 
                                           WHEN m.id_venta IS NOT NULL THEN 'Venta' 
                                           ELSE 'Otra salida' 
                                       END AS origen,
                                       u.nombre AS responsable
                                """ + selectCantidad + """
                                FROM movimiento_inventario m
                                LEFT JOIN orden_compra o ON o.id_orden = m.id_orden
                                LEFT JOIN usuario u ON u.id_usuario = m.id_usuario
                                """ + condiciones(filtro, parametros) + """
                                 ORDER BY m.fecha_hora DESC, m.id_movimiento DESC
                                 LIMIT ? OFFSET ?
                                """;
                parametros.add(limite);
                parametros.add(offset);
                return jdbcTemplate.query(sql, MAPPER_MOVIMIENTO, parametros.toArray());
        }

        public List<MovimientoInventario> listar(FiltroMovimientosFormulario filtro) {
                int pagina = (filtro != null && filtro.pagina() != null && filtro.pagina() > 0) ? filtro.pagina() : 1;
                int offset = (pagina - 1) * 20;
                return listar(filtro, 20, offset);
        }

        /**
         * Cuenta el total de movimientos que cumplen el filtro (spec 005, CA-3.4).
         */
        public int contar(FiltroMovimientosFormulario filtro) {
                List<Object> parametros = new ArrayList<>();
                String sql = "SELECT COUNT(*) FROM movimiento_inventario m" + condiciones(filtro, parametros);
                Integer total = jdbcTemplate.queryForObject(sql, Integer.class, parametros.toArray());
                return total == null ? 0 : total;
        }

        /**
         * Conserva el comportamiento original sin filtros delegando a listar() completo (CA-2.3).
         */
        public List<MovimientoInventario> listar() {
                return listar(FiltroMovimientosFormulario.vacio(), Integer.MAX_VALUE, 0);
        }

        private static String condiciones(FiltroMovimientosFormulario filtro, List<Object> parametros) {
                StringBuilder where = new StringBuilder(" WHERE 1 = 1");
                if (filtro != null) {
                        if (tieneTexto(filtro.tipo())) {
                                where.append(" AND m.tipo = ?");
                                parametros.add(filtro.tipo().strip().toUpperCase());
                        }
                        if (filtro.desde() != null) {
                                where.append(" AND m.fecha_hora >= ?");
                                parametros.add(filtro.desde().atStartOfDay());
                        }
                        if (filtro.hasta() != null) {
                                where.append(" AND m.fecha_hora < ?");
                                parametros.add(filtro.hasta().plusDays(1).atStartOfDay());
                        }
                        if (tieneTexto(filtro.motivo())) {
                                where.append(" AND m.motivo ILIKE ?");
                                parametros.add("%" + filtro.motivo().strip() + "%");
                        }
                        if (filtro.idUsuario() != null) {
                                where.append(" AND m.id_usuario = ?");
                                parametros.add(filtro.idUsuario());
                        }
                        if (filtro.idMedicamento() != null) {
                                where.append("""
                                     AND EXISTS (
                                         SELECT 1 FROM detalle_movimiento d
                                         JOIN lote l ON l.id_lote = d.id_lote
                                         WHERE d.id_movimiento = m.id_movimiento
                                           AND l.id_medicamento = ?
                                     )
                                    """);
                                parametros.add(filtro.idMedicamento());
                        }
                        if (tieneTexto(filtro.numeroLote())) {
                                where.append("""
                                     AND EXISTS (
                                         SELECT 1 FROM detalle_movimiento d
                                         JOIN lote l ON l.id_lote = d.id_lote
                                         WHERE d.id_movimiento = m.id_movimiento
                                           AND l.numero_lote ILIKE ?
                                     )
                                    """);
                                parametros.add("%" + filtro.numeroLote().strip() + "%");
                        }
                }
                return where.toString();
        }

        private static boolean tieneTexto(String valor) {
                return valor != null && !valor.isBlank();
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
                                SELECT m.id_movimiento, m.id_usuario, o.id_proveedor,
                                       m.id_venta, m.id_orden, m.tipo, m.fecha_hora, m.motivo,
                                       CASE 
                                           WHEN m.tipo = 'ENTRADA' AND m.id_orden IS NOT NULL THEN 'Recepción de compra' 
                                           WHEN m.tipo = 'ENTRADA' THEN 'Existencia inicial' 
                                           WHEN m.id_venta IS NOT NULL THEN 'Venta' 
                                           ELSE 'Otra salida' 
                                       END AS origen,
                                       u.nombre AS responsable,
                                       NULL::integer AS cantidad_medicamento
                                FROM movimiento_inventario m
                                LEFT JOIN orden_compra o ON o.id_orden = m.id_orden
                                LEFT JOIN usuario u ON u.id_usuario = m.id_usuario
                                WHERE m.id_movimiento = ?
                                """;

                List<MovimientoInventario> movimientos = jdbcTemplate.query(sql, MAPPER_MOVIMIENTO, idMovimiento);
                return movimientos.stream().findFirst();
        }

        /**
         * Registra la cabecera de una recepción de compra. El proveedor no se guarda en el movimiento
         * (3FN): se obtiene por {@code orden_compra}.
         */
        public Integer registrarEntrada(Integer idUsuario, Integer idOrden, String motivo) {
                String sql = """
                                INSERT INTO movimiento_inventario (id_usuario, id_orden, tipo, motivo)
                                VALUES (?, ?, 'ENTRADA', ?)
                                RETURNING id_movimiento
                                """;
                return jdbcTemplate.queryForObject(sql, Integer.class, idUsuario, idOrden, motivo);
        }

        /**
         * Registra una entrada inicial (spec 006, RF-003, US-1) sin orden de compra.
         */
        public Integer registrarEntradaInicial(Integer idUsuario, String motivo) {
                String sql = """
                                INSERT INTO movimiento_inventario (id_usuario, tipo, motivo)
                                VALUES (?, 'ENTRADA', ?)
                                RETURNING id_movimiento
                                """;
                return jdbcTemplate.queryForObject(sql, Integer.class, idUsuario, motivo);
        }

        public void registrarDetalleEntrada(Integer idMovimiento, Integer idLote,
                        Integer idDetalleOrden, Integer cantidad) {
                String sql = """
                                INSERT INTO detalle_movimiento (id_movimiento, id_lote, id_detalle_orden, cantidad)
                                VALUES (?, ?, ?, ?)
                                """;
                jdbcTemplate.update(sql, idMovimiento, idLote, idDetalleOrden, cantidad);
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

        /** Cabecera de la salida que genera una venta confirmada (exactamente una por venta: id_venta es UNIQUE). */
        public Integer registrarSalidaPorVenta(Integer idUsuario, Integer idVenta, String motivo) {
                String sql = """
                                INSERT INTO movimiento_inventario (id_usuario, id_venta, tipo, motivo)
                                VALUES (?, ?, 'SALIDA', ?)
                                RETURNING id_movimiento
                                """;
                return jdbcTemplate.queryForObject(sql, Integer.class, idUsuario, idVenta, motivo);
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