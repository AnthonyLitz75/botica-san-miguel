package com.farmacia.repository;

import com.farmacia.model.Lote;
import com.farmacia.model.LoteDisponibleVista;
import com.farmacia.model.AlertaVencimientoVista;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class LoteRepositorio {

        private final JdbcTemplate jdbcTemplate;

        public LoteRepositorio(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
        }

        public List<Lote> listarPorMedicamento(Integer idMedicamento) {
                String sql = """
                                SELECT id_lote, id_medicamento, numero_lote,
                                       fecha_vencimiento, stock_actual
                                FROM lote
                                WHERE id_medicamento = ?
                                ORDER BY fecha_vencimiento, id_lote
                                """;

                return jdbcTemplate.query(
                                sql,
                                (fila, numeroFila) -> new Lote(
                                                fila.getInt("id_lote"),
                                                fila.getInt("id_medicamento"),
                                                fila.getString("numero_lote"),
                                                fila.getObject("fecha_vencimiento", LocalDate.class),
                                                fila.getInt("stock_actual")),
                                idMedicamento);
        }

        public Optional<Lote> buscarPorId(Integer idLote) {
                String sql = """
                                SELECT id_lote, id_medicamento, numero_lote,
                                       fecha_vencimiento, stock_actual
                                FROM lote
                                WHERE id_lote = ?
                                """;

                List<Lote> lotes = jdbcTemplate.query(
                                sql,
                                (fila, numeroFila) -> new Lote(
                                                fila.getInt("id_lote"),
                                                fila.getInt("id_medicamento"),
                                                fila.getString("numero_lote"),
                                                fila.getObject("fecha_vencimiento", LocalDate.class),
                                                fila.getInt("stock_actual")),
                                idLote);

                return lotes.stream().findFirst();
        }

        public int descontarStock(Integer idLote, Integer cantidad) {
                if (cantidad == null || cantidad <= 0) {
                        throw new IllegalArgumentException(
                                        "La cantidad debe ser mayor que cero");
                }

                String sql = """
                                UPDATE lote
                                SET stock_actual = stock_actual - ?
                                WHERE id_lote = ?
                                  AND stock_actual >= ?
                                """;

                return jdbcTemplate.update(
                                sql,
                                cantidad,
                                idLote,
                                cantidad);
        }

        public Integer registrarOIncrementar(Integer idMedicamento, String numeroLote,
                        LocalDate fechaVencimiento, Integer cantidad) {
                String sql = """
                                INSERT INTO lote (id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
                                VALUES (?, ?, ?, ?)
                                ON CONFLICT (id_medicamento, numero_lote)
                                DO UPDATE SET stock_actual = lote.stock_actual + EXCLUDED.stock_actual
                                WHERE lote.fecha_vencimiento = EXCLUDED.fecha_vencimiento
                                RETURNING id_lote
                                """;
                try {
                        return jdbcTemplate.queryForObject(sql, Integer.class,
                                idMedicamento, numeroLote, fechaVencimiento, cantidad);
                } catch (org.springframework.dao.EmptyResultDataAccessException excepcion) {
                        throw new IllegalArgumentException("El número de lote ya existe con otra fecha de vencimiento");
                }
        }

        public List<LoteDisponibleVista> listarDisponiblesConMedicamento() {
                String sql = """
                                SELECT l.id_lote, l.id_medicamento, l.numero_lote,
                                       l.fecha_vencimiento, l.stock_actual,
                                       m.codigo AS codigo_medicamento,
                                       m.nombre AS nombre_medicamento,
                                       m.unidad_control
                                FROM lote l
                                JOIN medicamento m ON m.id_medicamento = l.id_medicamento
                                WHERE l.stock_actual > 0 AND m.activo = TRUE
                                ORDER BY m.nombre ASC, l.fecha_vencimiento ASC, l.id_lote ASC
                                """;

                return jdbcTemplate.query(
                                sql,
                                (fila, numeroFila) -> new LoteDisponibleVista(
                                                fila.getInt("id_lote"),
                                                fila.getInt("id_medicamento"),
                                                fila.getString("codigo_medicamento"),
                                                fila.getString("nombre_medicamento"),
                                                fila.getString("numero_lote"),
                                                fila.getObject("fecha_vencimiento", LocalDate.class),
                                                fila.getInt("stock_actual"),
                                                fila.getString("unidad_control")));
        }

        public List<LoteDisponibleVista> listarDisponiblesPorMedicamento(Integer idMedicamento) {
                String sql = """
                                SELECT l.id_lote, l.id_medicamento, l.numero_lote,
                                       l.fecha_vencimiento, l.stock_actual,
                                       m.codigo AS codigo_medicamento,
                                       m.nombre AS nombre_medicamento,
                                       m.unidad_control
                                FROM lote l
                                JOIN medicamento m ON m.id_medicamento = l.id_medicamento
                                WHERE l.id_medicamento = ?
                                  AND l.stock_actual > 0
                                  AND m.activo = TRUE
                                ORDER BY l.id_lote
                                """;
                return jdbcTemplate.query(sql, (fila, numeroFila) -> new LoteDisponibleVista(
                        fila.getInt("id_lote"), fila.getInt("id_medicamento"),
                        fila.getString("codigo_medicamento"), fila.getString("nombre_medicamento"),
                        fila.getString("numero_lote"), fila.getObject("fecha_vencimiento", LocalDate.class),
                        fila.getInt("stock_actual"), fila.getString("unidad_control")), idMedicamento);
        }

        public List<AlertaVencimientoVista> listarVencidos(LocalDate fechaCorte) {
                String sql = """
                                SELECT l.id_lote, l.id_medicamento, m.codigo, m.nombre,
                                       l.numero_lote, l.fecha_vencimiento, l.stock_actual, m.unidad_control
                                FROM lote l
                                JOIN medicamento m ON m.id_medicamento = l.id_medicamento
                                WHERE m.activo = TRUE AND l.stock_actual > 0
                                  AND l.fecha_vencimiento < ?
                                ORDER BY l.fecha_vencimiento, m.nombre, l.id_lote
                                """;
                return jdbcTemplate.query(sql, (fila, numeroFila) -> crearAlertaVencimiento(fila,
                        fechaCorte, "VENCIDO"), fechaCorte);
        }

        public List<AlertaVencimientoVista> listarPorVencer(LocalDate desde, LocalDate hasta) {
                String sql = """
                                SELECT l.id_lote, l.id_medicamento, m.codigo, m.nombre,
                                       l.numero_lote, l.fecha_vencimiento, l.stock_actual, m.unidad_control
                                FROM lote l
                                JOIN medicamento m ON m.id_medicamento = l.id_medicamento
                                WHERE m.activo = TRUE AND l.stock_actual > 0
                                  AND l.fecha_vencimiento >= ? AND l.fecha_vencimiento <= ?
                                ORDER BY l.fecha_vencimiento, m.nombre, l.id_lote
                                """;
                return jdbcTemplate.query(sql, (fila, numeroFila) -> crearAlertaVencimiento(fila,
                        desde, "POR_VENCER"), desde, hasta);
        }

        private AlertaVencimientoVista crearAlertaVencimiento(
                        ResultSet fila, LocalDate hoy, String tipo) throws SQLException {
                LocalDate fecha = fila.getObject("fecha_vencimiento", LocalDate.class);
                return new AlertaVencimientoVista(
                        fila.getInt("id_lote"), fila.getInt("id_medicamento"),
                        fila.getString("codigo"), fila.getString("nombre"),
                        fila.getString("numero_lote"), fecha, fila.getInt("stock_actual"),
                        fila.getString("unidad_control"), ChronoUnit.DAYS.between(hoy, fecha), tipo);
        }
}
