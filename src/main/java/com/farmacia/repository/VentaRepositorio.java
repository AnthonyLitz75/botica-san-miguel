package com.farmacia.repository;

import com.farmacia.model.ComprobanteVista;
import com.farmacia.model.DetalleVenta;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VentaRepositorio {
    private final JdbcTemplate jdbcTemplate;

    public VentaRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Siguiente correlativo atómico de la secuencia: B001-00000001, B001-00000002, ... */
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
}
