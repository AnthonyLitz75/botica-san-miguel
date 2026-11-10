package com.farmacia.repository;

import com.farmacia.model.Medicamento;
import com.farmacia.model.ResumenStockInventario;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.farmacia.dto.MedicamentoFormulario;
import java.util.Optional;
import java.time.LocalDate;

@Repository
public class MedicamentoRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public MedicamentoRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
                       m.stock_minimo, m.activo,
                       COALESCE(SUM(CASE WHEN l.fecha_vencimiento >= CURRENT_DATE THEN l.stock_actual ELSE 0 END), 0)::INTEGER AS stock_actual
                FROM medicamento m
                LEFT JOIN lote l ON l.id_medicamento = m.id_medicamento
                WHERE m.activo = ?
                AND (
                    POSITION(LOWER(?) IN LOWER(m.nombre)) > 0
                    OR POSITION(LOWER(?) IN LOWER(m.codigo)) > 0
                )
                GROUP BY m.id_medicamento, m.codigo, m.nombre, m.concentracion,
                         m.presentacion, m.unidad_control, m.precio_venta,
                         m.stock_minimo, m.activo
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
                fila.getInt("stock_actual")), activo, texto, texto);
    }

    public Integer guardar(MedicamentoFormulario medicamentoFormulario) {
        String sql = """
                INSERT INTO medicamento (codigo, nombre, concentracion,
                                         presentacion, unidad_control,
                                         precio_venta, stock_minimo, activo)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                RETURNING id_medicamento
                """;

        return jdbcTemplate.queryForObject(sql, Integer.class,
                medicamentoFormulario.codigo(),
                medicamentoFormulario.nombre(),
                medicamentoFormulario.concentracion(),
                medicamentoFormulario.presentacion(),
                medicamentoFormulario.unidadControl(),
                medicamentoFormulario.precioVenta(),
                medicamentoFormulario.stockMinimo());
    }

    public Optional<Medicamento> buscarPorId(Integer id) {
        String sql = """
                SELECT m.id_medicamento, m.codigo, m.nombre, m.concentracion,
                       m.presentacion, m.unidad_control, m.precio_venta,
                       m.stock_minimo, m.activo,
                       COALESCE(SUM(CASE WHEN l.fecha_vencimiento >= CURRENT_DATE THEN l.stock_actual ELSE 0 END), 0)::INTEGER AS stock_actual
                FROM medicamento m
                LEFT JOIN lote l ON l.id_medicamento = m.id_medicamento
                WHERE m.id_medicamento = ?
                GROUP BY m.id_medicamento, m.codigo, m.nombre, m.concentracion,
                         m.presentacion, m.unidad_control, m.precio_venta,
                         m.stock_minimo, m.activo
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
                        fila.getInt("stock_actual")),
                id);

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
                    stock_minimo = ?
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
}
