package com.farmacia.repository;

import com.farmacia.dto.FiltroVentasFormulario;
import com.farmacia.model.ComprobanteVista;
import com.farmacia.model.DetalleVenta;
import com.farmacia.model.VentaResumenVista;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class VentaRepositorio {
    private final JdbcTemplate jdbcTemplate;

    public VentaRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Siguiente correlativo atómico de la secuencia: B001-00000001, B001-00000002,
     * ...
     */
    public String siguienteNumeroComprobante() {
        return jdbcTemplate.queryForObject(
                "SELECT 'B001-' || lpad(nextval('seq_numero_comprobante')::text, 8, '0')",
                String.class);
    }

    public Integer insertar(Integer idUsuario, Integer idCliente, BigDecimal subtotal,
            BigDecimal descuentoTotal, BigDecimal total, String numeroComprobante) {
        String sql = """
                INSERT INTO venta (id_usuario, id_cliente, subtotal, descuento_total, total, numero_comprobante)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id_venta
                """;
        return jdbcTemplate.queryForObject(sql, Integer.class,
                idUsuario, idCliente, subtotal, descuentoTotal, total, numeroComprobante);
    }

    public void insertarDetalle(Integer idVenta, DetalleVenta detalle) {
        String sql = """
                INSERT INTO detalle_venta (id_venta, id_lote, cantidad, precio_unitario, descuento_importe, importe)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, idVenta, detalle.idLote(), detalle.cantidad(),
                detalle.precioUnitario(), detalle.descuentoImporte(), detalle.importe());
    }

    public Optional<ComprobanteVista> buscarComprobante(Integer idVenta) {
        String sqlLineas = """
                SELECT m.codigo, m.nombre, l.numero_lote, d.cantidad,
                       d.precio_unitario, d.descuento_importe, d.importe
                FROM detalle_venta d
                JOIN lote l ON l.id_lote = d.id_lote
                JOIN medicamento m ON m.id_medicamento = l.id_medicamento
                WHERE d.id_venta = ?
                ORDER BY d.id_detalle_venta
                """;
        List<ComprobanteVista.Linea> lineas = jdbcTemplate.query(sqlLineas,
                (fila, numero) -> new ComprobanteVista.Linea(
                        fila.getString("codigo"),
                        fila.getString("nombre"),
                        fila.getString("numero_lote"),
                        fila.getInt("cantidad"),
                        fila.getBigDecimal("precio_unitario"),
                        fila.getBigDecimal("descuento_importe"),
                        fila.getBigDecimal("importe")),
                idVenta);

        String sqlCabecera = """
                SELECT v.id_venta, v.numero_comprobante, v.fecha_hora, v.id_usuario,
                       u.nombre AS vendedor, c.nombre_completo AS cliente,
                       CASE WHEN c.id_cliente IS NULL THEN NULL
                            ELSE c.tipo_documento || ' ' || c.numero_documento END AS documento_cliente,
                       v.subtotal, v.descuento_total, v.total
                FROM venta v
                JOIN usuario u ON u.id_usuario = v.id_usuario
                LEFT JOIN cliente c ON c.id_cliente = v.id_cliente
                WHERE v.id_venta = ?
                """;
        return jdbcTemplate.query(sqlCabecera,
                (fila, numero) -> new ComprobanteVista(
                        fila.getInt("id_venta"),
                        fila.getString("numero_comprobante"),
                        fila.getObject("fecha_hora", LocalDateTime.class),
                        fila.getInt("id_usuario"),
                        fila.getString("vendedor"),
                        fila.getString("cliente"),
                        fila.getString("documento_cliente"),
                        lineas,
                        fila.getBigDecimal("subtotal"),
                        fila.getBigDecimal("descuento_total"),
                        fila.getBigDecimal("total")),
                idVenta).stream().findFirst();
    }

    private static final RowMapper<VentaResumenVista> MAPEO_RESUMEN = (fila, numero) -> new VentaResumenVista(
            fila.getInt("id_venta"),
            fila.getString("numero_comprobante"),
            fila.getObject("fecha_hora", LocalDateTime.class),
            fila.getInt("id_usuario"),
            fila.getString("vendedor"),
            fila.getString("cliente"),
            fila.getBigDecimal("subtotal"),
            fila.getBigDecimal("descuento_total"),
            fila.getBigDecimal("total"));

    /**
     * Lista ventas paginadas con filtros opcionales (spec 005, CA-1.1, 1.2, 1.3,
     * 1.6).
     */
    public List<VentaResumenVista> listar(FiltroVentasFormulario filtro, int limite, int offset) {
        List<Object> parametros = new ArrayList<>();
        String sql = """
                SELECT v.id_venta, v.numero_comprobante, v.fecha_hora, v.id_usuario,
                       u.nombre AS vendedor, c.nombre_completo AS cliente,
                       v.subtotal, v.descuento_total, v.total
                FROM venta v
                JOIN usuario u ON u.id_usuario = v.id_usuario
                LEFT JOIN cliente c ON c.id_cliente = v.id_cliente
                """ + condiciones(filtro, parametros) + """
                 ORDER BY v.fecha_hora DESC, v.id_venta DESC
                 LIMIT ? OFFSET ?
                """;
        parametros.add(limite);
        parametros.add(offset);
        return jdbcTemplate.query(sql, MAPEO_RESUMEN, parametros.toArray());
    }

    /**
     * Cuenta el total de ventas que cumplen el filtro (spec 005, CA-1.6).
     */
    public int contar(FiltroVentasFormulario filtro) {
        List<Object> parametros = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM venta v" + condiciones(filtro, parametros);
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, parametros.toArray());
        return total == null ? 0 : total;
    }

    /**
     * Calcula la suma acumulada de total de todas las ventas del filtro (spec 005,
     * CA-1.5).
     */
    

    public BigDecimal sumarTotal(FiltroVentasFormulario filtro) {
        List<Object> parametros = new ArrayList<>();
        String sql = "SELECT COALESCE(SUM(v.total), 0) FROM venta v" + condiciones(filtro, parametros);
        BigDecimal total = jdbcTemplate.queryForObject(sql, BigDecimal.class, parametros.toArray());
        return total == null ? BigDecimal.ZERO : total;
    }

    private static String condiciones(FiltroVentasFormulario filtro, List<Object> parametros) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (filtro != null) {
            if (filtro.desde() != null) {
                where.append(" AND v.fecha_hora >= ?");
                parametros.add(filtro.desde().atStartOfDay());
            }
            if (filtro.hasta() != null) {
                where.append(" AND v.fecha_hora < ?");
                parametros.add(filtro.hasta().plusDays(1).atStartOfDay());
            }
            if (filtro.idVendedor() != null) {
                where.append(" AND v.id_usuario = ?");
                parametros.add(filtro.idVendedor());
            }
            if (tieneTexto(filtro.comprobante())) {
                where.append(" AND v.numero_comprobante ILIKE ?");
                parametros.add(filtro.comprobante().strip() + "%");
            }
        }
        return where.toString();
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
