package com.farmacia.repository;

import com.farmacia.model.Lote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

import java.time.LocalDate;
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
}