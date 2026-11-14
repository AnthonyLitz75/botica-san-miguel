package com.farmacia.repository;

import com.farmacia.model.Medicamento;
import com.farmacia.model.ResumenStockInventario;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.farmacia.dto.MedicamentoFormulario;
import java.util.Optional;
import java.time.Clock;
import java.time.LocalDate;

@Repository
public class MedicamentoRepositorio {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /**
     * El reloj solo fija la fecha de corte del stock vigente (lotes que vencen hoy o después),
     * que se envía como parámetro en lugar de usar CURRENT_DATE, para que las pruebas sean deterministas.
     */
    public MedicamentoRepositorio(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public List<Medicamento> listarActivos() {
        return buscarActivos("");
    }

    public List<Medicamento> buscarActivos(String texto) {
        return buscarPorEstado(texto, true);
    }

    public List<Medicamento> buscarPorEstado(String texto, boolean activo) {
        String sql = """
                SELECT m.id_medicamento, m.codigo, m.nombre, m.concentracion,
                       m.presentacion, m.unidad_control, m.precio_venta,
                       m.stock_minimo, m.activo, m.id_categoria,
                       COALESCE(SUM(CASE WHEN l.fecha_vencimiento >= ? THEN l.stock_actual ELSE 0 END), 0)::INTEGER AS stock_actual
                FROM medicamento m
                LEFT JOIN lote l ON l.id_medicamento = m.id_medicamento
                WHERE m.activo = ?
                AND (
                    POSITION(LOWER(?) IN LOWER(m.nombre)) > 0
                    OR POSITION(LOWER(?) IN LOWER(m.codigo)) > 0
                )
                GROUP BY m.id_medicamento, m.codigo, m.nombre, m.concentracion,
                         m.presentacion, m.unidad_control, m.precio_venta,
                         m.stock_minimo, m.activo, m.id_categoria
                ORDER BY m.codigo, m.id_medicamento
                """;

        return jdbcTemplate.query(sql, (fila, numeroFila) -> new Medicamento(
                fila.getInt("id_medicamento"),
                fila.getString("codigo"),
                fila.getString("nombre"),
                fila.getString("concentracion"),
                fila.getString("presentacion"),
                fila.getString("unidad_control"),
                fila.getBigDecimal("precio_venta"),
                fila.getInt("stock_minimo"),
                fila.getBoolean("activo"),
                fila.getInt("stock_actual"),
                fila.getObject("id_categoria", Integer.class)), LocalDate.now(clock), activo, texto, texto);
    }

    public Integer guardar(MedicamentoFormulario medicamentoFormulario) {
        String sql = """
                INSERT INTO medicamento (codigo, nombre, concentracion,
                                         presentacion, unidad_control,
                                         precio_venta, stock_minimo, activo, id_categoria)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                RETURNING id_medicamento
                """;

        return jdbcTemplate.queryForObject(sql, Integer.class,
                medicamentoFormulario.codigo(),
                medicamentoFormulario.nombre(),
                medicamentoFormulario.concentracion(),
                medicamentoFormulario.presentacion(),
                medicamentoFormulario.unidadControl(),
                medicamentoFormulario.precioVenta(),
                medicamentoFormulario.stockMinimo(),
                medicamentoFormulario.idCategoria());
    }

    public Optional<Medicamento> buscarPorId(Integer id) {
        String sql = """
                SELECT m.id_medicamento, m.codigo, m.nombre, m.concentracion,
                       m.presentacion, m.unidad_control, m.precio_venta,
                       m.stock_minimo, m.activo, m.id_categoria,
                       COALESCE(SUM(CASE WHEN l.fecha_vencimiento >= ? THEN l.stock_actual ELSE 0 END), 0)::INTEGER AS stock_actual
                FROM medicamento m
                LEFT JOIN lote l ON l.id_medicamento = m.id_medicamento
                WHERE m.id_medicamento = ?
                GROUP BY m.id_medicamento, m.codigo, m.nombre, m.concentracion,
                         m.presentacion, m.unidad_control, m.precio_venta,
                         m.stock_minimo, m.activo, m.id_categoria
                """;

        List<Medicamento> resultados = jdbcTemplate.query(
                sql,
                (fila, numeroFila) -> new Medicamento(
                        fila.getInt("id_medicamento"),
                        fila.getString("codigo"),
                        fila.getString("nombre"),
                        fila.getString("concentracion"),
                        fila.getString("presentacion"),
                        fila.getString("unidad_control"),
                        fila.getBigDecimal("precio_venta"),
                        fila.getInt("stock_minimo"),
                        fila.getBoolean("activo"),
                        fila.getInt("stock_actual"),
                        fila.getObject("id_categoria", Integer.class)),
                LocalDate.now(clock), id);

        return resultados.stream().findFirst();
    }

    public int actualizar(Integer id, MedicamentoFormulario formulario) {
        String sql = """
                UPDATE medicamento
                SET codigo = ?,
                    nombre = ?,
                    concentracion = ?,
                    presentacion = ?,
                    unidad_control = ?,
                    precio_venta = ?,
                    stock_minimo = ?,
                    id_categoria = ?
                WHERE id_medicamento = ?
                """;

        return jdbcTemplate.update(
                sql,
                formulario.codigo(),
                formulario.nombre(),
                formulario.concentracion(),
                formulario.presentacion(),
                formulario.unidadControl(),
                formulario.precioVenta(),
                formulario.stockMinimo(),
                formulario.idCategoria(),
                id);
    }

    public int desactivar(Integer id) {
        String sql = """
                UPDATE medicamento
                SET activo = FALSE
                WHERE id_medicamento = ?
                """;

        return jdbcTemplate.update(sql, id);
    }

    public int activar(Integer id) {
        String sql = """
                UPDATE medicamento
                SET activo = TRUE
                WHERE id_medicamento = ?
                """;

        return jdbcTemplate.update(sql, id);
    }

    public List<ResumenStockInventario> listarResumenStock(LocalDate fechaCorte) {
        String sql = """
                WITH recibidas AS (
                    SELECT dm.id_detalle_orden,
                           COALESCE(SUM(dm.cantidad), 0)::INTEGER AS cantidad_recibida
                    FROM detalle_movimiento dm
                    JOIN movimiento_inventario mi ON mi.id_movimiento = dm.id_movimiento
                    WHERE mi.tipo = 'ENTRADA' AND mi.id_orden IS NOT NULL
                    GROUP BY dm.id_detalle_orden
                ), pendientes AS (
                    SELECT d.id_medicamento,
                           SUM(GREATEST(d.cantidad_solicitada - COALESCE(r.cantidad_recibida, 0), 0))::INTEGER
                               AS cantidad_pendiente
                    FROM detalle_orden_compra d
                    JOIN orden_compra o ON o.id_orden = d.id_orden
                    LEFT JOIN recibidas r ON r.id_detalle_orden = d.id_detalle_orden
                    WHERE o.estado IN ('PENDIENTE', 'PARCIALMENTE_RECIBIDA')
                    GROUP BY d.id_medicamento
                ), existencias AS (
                    SELECT l.id_medicamento,
                           COALESCE(SUM(CASE WHEN l.fecha_vencimiento >= ?
                                             THEN l.stock_actual ELSE 0 END), 0)::INTEGER AS stock_disponible
                    FROM lote l
                    GROUP BY l.id_medicamento
                )
                SELECT m.id_medicamento, m.codigo, m.nombre, m.unidad_control,
                       m.stock_minimo,
                       COALESCE(e.stock_disponible, 0)::INTEGER AS stock_disponible,
                       COALESCE(p.cantidad_pendiente, 0)::INTEGER AS pendiente_recibir
                FROM medicamento m
                LEFT JOIN existencias e ON e.id_medicamento = m.id_medicamento
                LEFT JOIN pendientes p ON p.id_medicamento = m.id_medicamento
                WHERE m.activo = TRUE
                ORDER BY m.codigo, m.id_medicamento
                """;

        return jdbcTemplate.query(sql, (fila, numeroFila) -> new ResumenStockInventario(
                fila.getInt("id_medicamento"), fila.getString("codigo"), fila.getString("nombre"),
                fila.getString("unidad_control"), fila.getInt("stock_minimo"),
                fila.getInt("stock_disponible"), fila.getInt("pendiente_recibir")), fechaCorte);
    }

    /**
     * Suma el stock disponible (lotes con vencimiento >= fechaCorte) de medicamentos activos
     * pertenecientes a la lista de categorías dada (CA-3.1).
     */
    @SuppressWarnings("java:S2077")
    public int sumarStockDisponiblePorCategorias(List<Integer> idsCategorias, LocalDate fechaCorte) {
        if (idsCategorias == null || idsCategorias.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(idsCategorias.size(), "?"));
        String sql = """
                SELECT COALESCE(SUM(l.stock_actual), 0)::INTEGER
                FROM medicamento m
                JOIN lote l ON l.id_medicamento = m.id_medicamento
                WHERE m.activo = TRUE
                  AND l.fecha_vencimiento >= ?
                  AND m.id_categoria IN (%s)
                """.formatted(placeholders);

        List<Object> params = new java.util.ArrayList<>();
        params.add(fechaCorte);
        params.addAll(idsCategorias);

        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, params.toArray());
        return total != null ? total : 0;
    }

    /**
     * Cuenta cuántos medicamentos activos de la rama de categorías se encuentran en alerta (stock bajo mínimo o agotado).
     */
    @SuppressWarnings("java:S2077")
    public int contarAlertasPorCategorias(List<Integer> idsCategorias, LocalDate fechaCorte) {
        if (idsCategorias == null || idsCategorias.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(idsCategorias.size(), "?"));
        String sql = """
                WITH stock_disp AS (
                    SELECT m.id_medicamento, m.stock_minimo,
                           COALESCE(SUM(CASE WHEN l.fecha_vencimiento >= ? THEN l.stock_actual ELSE 0 END), 0)::INTEGER AS stock_disponible
                    FROM medicamento m
                    LEFT JOIN lote l ON l.id_medicamento = m.id_medicamento
                    WHERE m.activo = TRUE
                      AND m.id_categoria IN (%s)
                    GROUP BY m.id_medicamento, m.stock_minimo
                )
                SELECT COUNT(*)::INTEGER
                FROM stock_disp
                WHERE stock_disponible <= stock_minimo
                """.formatted(placeholders);

        List<Object> params = new java.util.ArrayList<>();
        params.add(fechaCorte);
        params.addAll(idsCategorias);

        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, params.toArray());
        return total != null ? total : 0;
    }

    /**
     * Busca medicamentos activos filtrados por texto y restringidos a un conjunto de categorías (CA-3.2).
     */
    @SuppressWarnings("java:S2077")
    public List<Medicamento> buscarPorEstadoYCategorias(String texto, boolean activo, List<Integer> idsCategorias) {
        if (idsCategorias == null || idsCategorias.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(idsCategorias.size(), "?"));
        String sql = """
                SELECT m.id_medicamento, m.codigo, m.nombre, m.concentracion,
                       m.presentacion, m.unidad_control, m.precio_venta,
                       m.stock_minimo, m.activo, m.id_categoria,
                       COALESCE(SUM(CASE WHEN l.fecha_vencimiento >= ? THEN l.stock_actual ELSE 0 END), 0)::INTEGER AS stock_actual
                FROM medicamento m
                LEFT JOIN lote l ON l.id_medicamento = m.id_medicamento
                WHERE m.activo = ?
                  AND m.id_categoria IN (%s)
                  AND (
                      POSITION(LOWER(?) IN LOWER(m.nombre)) > 0
                      OR POSITION(LOWER(?) IN LOWER(m.codigo)) > 0
                  )
                GROUP BY m.id_medicamento, m.codigo, m.nombre, m.concentracion,
                         m.presentacion, m.unidad_control, m.precio_venta,
                         m.stock_minimo, m.activo, m.id_categoria
                ORDER BY m.codigo, m.id_medicamento
                """.formatted(placeholders);

        List<Object> params = new java.util.ArrayList<>();
        params.add(LocalDate.now(clock));
        params.add(activo);
        params.addAll(idsCategorias);
        params.add(texto != null ? texto : "");
        params.add(texto != null ? texto : "");

        return jdbcTemplate.query(sql, (fila, numeroFila) -> new Medicamento(
                fila.getInt("id_medicamento"),
                fila.getString("codigo"),
                fila.getString("nombre"),
                fila.getString("concentracion"),
                fila.getString("presentacion"),
                fila.getString("unidad_control"),
                fila.getBigDecimal("precio_venta"),
                fila.getInt("stock_minimo"),
                fila.getBoolean("activo"),
                fila.getInt("stock_actual"),
                fila.getObject("id_categoria", Integer.class)), params.toArray());
    }
}
