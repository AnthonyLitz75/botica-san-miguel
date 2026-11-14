package com.farmacia.repository;

import com.farmacia.model.CompraReporteVista;
import com.farmacia.model.InventarioReporteVista;
import com.farmacia.model.LoteInventarioReporteVista;
import com.farmacia.model.MedicamentoRotacionVista;
import com.farmacia.model.MermaVista;
import com.farmacia.model.VentasDiaVista;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReporteRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public ReporteRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VentasDiaVista> ventasPorDia(LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT
                    CAST(fecha_hora AS DATE) AS fecha,
                    COUNT(id_venta) AS cantidad_ventas,
                    COALESCE(SUM(subtotal), 0) AS subtotal,
                    COALESCE(SUM(descuento_total), 0) AS descuento,
                    COALESCE(SUM(total), 0) AS total
                FROM venta
                WHERE fecha_hora >= ? AND fecha_hora < ?
                GROUP BY CAST(fecha_hora AS DATE)
                ORDER BY fecha ASC
                """;

        Timestamp inicio = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp fin = Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());

        return jdbcTemplate.query(sql, (rs, rowNum) -> new VentasDiaVista(
                rs.getDate("fecha").toLocalDate(),
                rs.getLong("cantidad_ventas"),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("descuento"),
                rs.getBigDecimal("total")),
                inicio, fin);
    }

    public List<MedicamentoRotacionVista> masVendidos(LocalDate desde, LocalDate hasta, int limite) {
        String sql = """
                SELECT
                    m.id_medicamento,
                    m.codigo,
                    m.nombre,
                    COALESCE(SUM(dv.cantidad), 0) AS unidades_vendidas,
                    COALESCE(SUM(dv.importe), 0) AS total_vendido
                FROM detalle_venta dv
                JOIN venta v ON dv.id_venta = v.id_venta
                JOIN lote l ON dv.id_lote = l.id_lote
                JOIN medicamento m ON l.id_medicamento = m.id_medicamento
                WHERE v.fecha_hora >= ? AND v.fecha_hora < ?
                GROUP BY m.id_medicamento, m.codigo, m.nombre
                ORDER BY unidades_vendidas DESC, m.nombre ASC
                LIMIT ?
                """;

        Timestamp inicio = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp fin = Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());

        return jdbcTemplate.query(sql, (rs, rowNum) -> new MedicamentoRotacionVista(
                rs.getInt("id_medicamento"),
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getLong("unidades_vendidas"),
                rs.getBigDecimal("total_vendido")),
                inicio, fin, limite);
    }

    public List<MermaVista> mermas(LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT
                    m.id_medicamento,
                    m.codigo,
                    m.nombre,
                    COALESCE(SUM(dm.cantidad), 0) AS unidades_baja
                FROM detalle_movimiento dm
                JOIN movimiento_inventario mi ON dm.id_movimiento = mi.id_movimiento
                JOIN lote l ON dm.id_lote = l.id_lote
                JOIN medicamento m ON l.id_medicamento = m.id_medicamento
                WHERE mi.tipo = 'SALIDA'
                  AND mi.id_venta IS NULL
                  AND mi.fecha_hora >= ? AND mi.fecha_hora < ?
                GROUP BY m.id_medicamento, m.codigo, m.nombre
                ORDER BY unidades_baja DESC, m.nombre ASC
                """;

        Timestamp inicio = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp fin = Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());

        return jdbcTemplate.query(sql, (rs, rowNum) -> new MermaVista(
                rs.getInt("id_medicamento"),
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getLong("unidades_baja")),
                inicio, fin);
    }

    public List<InventarioReporteVista> reporteInventario(LocalDate fechaCorte, String estadoFiltro) {
        String sqlLotes = """
                SELECT
                    l.id_lote,
                    l.id_medicamento,
                    l.numero_lote,
                    l.fecha_vencimiento,
                    l.stock_actual
                FROM lote l
                JOIN medicamento m ON l.id_medicamento = m.id_medicamento
                WHERE m.activo = TRUE AND l.stock_actual > 0
                ORDER BY l.fecha_vencimiento ASC
                """;

        Map<Integer, List<LoteInventarioReporteVista>> lotesPorMed = new HashMap<>();
        jdbcTemplate.query(sqlLotes, rs -> {
            int idMed = rs.getInt("id_medicamento");
            LocalDate fv = rs.getDate("fecha_vencimiento").toLocalDate();
            long dias = ChronoUnit.DAYS.between(fechaCorte, fv);
            var loteVista = new LoteInventarioReporteVista(
                    rs.getInt("id_lote"),
                    rs.getString("numero_lote"),
                    fv,
                    rs.getInt("stock_actual"),
                    dias);
            lotesPorMed.computeIfAbsent(idMed, k -> new ArrayList<>()).add(loteVista);
        });

        String sqlMedicamentos = """
                SELECT
                    m.id_medicamento,
                    m.codigo,
                    m.nombre,
                    m.unidad_control,
                    m.stock_minimo,
                    COALESCE(SUM(CASE WHEN l.fecha_vencimiento >= ? THEN l.stock_actual ELSE 0 END), 0)::INTEGER AS stock_disponible,
                    COALESCE(SUM(CASE WHEN l.fecha_vencimiento < ? THEN l.stock_actual ELSE 0 END), 0)::INTEGER AS stock_vencido
                FROM medicamento m
                LEFT JOIN lote l ON m.id_medicamento = l.id_medicamento
                WHERE m.activo = TRUE
                GROUP BY m.id_medicamento, m.codigo, m.nombre, m.unidad_control, m.stock_minimo
                ORDER BY m.nombre ASC
                """;

        List<InventarioReporteVista> lista = jdbcTemplate.query(sqlMedicamentos, (rs, rowNum) -> {
            int idMed = rs.getInt("id_medicamento");
            int stockDisp = rs.getInt("stock_disponible");
            int stockVenc = rs.getInt("stock_vencido");
            int stockMin = rs.getInt("stock_minimo");

            String estado;
            if (stockDisp == 0) {
                estado = "AGOTADO";
            } else if (stockDisp <= stockMin) {
                estado = "STOCK_BAJO";
            } else {
                estado = "OK";
            }

            List<LoteInventarioReporteVista> lotes = lotesPorMed.getOrDefault(idMed, List.of());

            return new InventarioReporteVista(
                    idMed,
                    rs.getString("codigo"),
                    rs.getString("nombre"),
                    rs.getString("unidad_control"),
                    stockDisp,
                    stockVenc,
                    stockMin,
                    estado,
                    lotes);
        }, fechaCorte, fechaCorte);

        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            return lista.stream()
                    .filter(i -> i.estado().equalsIgnoreCase(estadoFiltro.trim()))
                    .toList();
        }
        return lista;
    }

    public List<CompraReporteVista> reporteCompras(
            LocalDate desde,
            LocalDate hasta,
            String estadoFiltro,
            Integer idProveedor) {

        StringBuilder sql = new StringBuilder("""
                WITH solicitadas AS (
                    SELECT id_orden, SUM(cantidad_solicitada)::INTEGER AS total_solicitadas
                    FROM detalle_orden_compra
                    GROUP BY id_orden
                ),
                recibidas AS (
                    SELECT mi.id_orden, SUM(dm.cantidad)::INTEGER AS total_recibidas
                    FROM movimiento_inventario mi
                    JOIN detalle_movimiento dm ON mi.id_movimiento = dm.id_movimiento
                    WHERE mi.tipo = 'ENTRADA' AND mi.id_orden IS NOT NULL
                    GROUP BY mi.id_orden
                )
                SELECT
                    oc.id_orden,
                    oc.numero_orden,
                    oc.fecha_hora::DATE AS fecha_emision,
                    p.razon_social AS proveedor,
                    oc.estado,
                    oc.total_estimado,
                    COALESCE(s.total_solicitadas, 0) AS unidades_solicitadas,
                    COALESCE(r.total_recibidas, 0) AS unidades_recibidas
                FROM orden_compra oc
                JOIN proveedor p ON oc.id_proveedor = p.id_proveedor
                LEFT JOIN solicitadas s ON oc.id_orden = s.id_orden
                LEFT JOIN recibidas r ON oc.id_orden = r.id_orden
                WHERE oc.fecha_hora >= ? AND oc.fecha_hora < ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(desde.atStartOfDay()));
        params.add(Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));

        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            sql.append(" AND oc.estado = ?");
            params.add(estadoFiltro.trim().toUpperCase());
        }

        if (idProveedor != null && idProveedor > 0) {
            sql.append(" AND oc.id_proveedor = ?");
            params.add(idProveedor);
        }

        sql.append(" ORDER BY oc.fecha_hora DESC, oc.id_orden DESC");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            int solicitadas = rs.getInt("unidades_solicitadas");
            int recibidas = rs.getInt("unidades_recibidas");
            int pendientes = Math.max(solicitadas - recibidas, 0);

            return new CompraReporteVista(
                    rs.getInt("id_orden"),
                    rs.getString("numero_orden"),
                    rs.getDate("fecha_emision").toLocalDate(),
                    rs.getString("proveedor"),
                    rs.getString("estado"),
                    rs.getBigDecimal("total_estimado"),
                    solicitadas,
                    recibidas,
                    pendientes);
        }, params.toArray());
    }
}
