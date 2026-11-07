package com.farmacia.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AlertaInventarioRepositorioPersistenciaTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MedicamentoRepositorio medicamentos;
    @Autowired private LoteRepositorio lotes;

    @Test
    void sumaStockVigenteRestaRecepcionesYConsultaVentanaDeVencimiento() {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        int usuario = jdbc.queryForObject("""
                INSERT INTO usuario(nombre,nombre_usuario,clave_hash,rol)
                VALUES ('Prueba alertas', ?, 'x', 'COMPRAS') RETURNING id_usuario
                """, Integer.class, "alertas-" + sufijo);
        String ruc = "20" + String.format("%09d", Math.abs(System.nanoTime() % 1_000_000_000L));
        int proveedor = jdbc.queryForObject("""
                INSERT INTO proveedor(ruc,razon_social) VALUES (?, 'Proveedor alertas') RETURNING id_proveedor
                """, Integer.class, ruc);
        int medicamento = jdbc.queryForObject("""
                INSERT INTO medicamento(codigo,nombre,presentacion,unidad_control,precio_venta,stock_minimo)
                VALUES (?, 'Medicamento alertas', 'Caja', 'CAJA', 1, 10) RETURNING id_medicamento
                """, Integer.class, "ALT-" + sufijo);
        int lote = jdbc.queryForObject("""
                INSERT INTO lote(id_medicamento,numero_lote,fecha_vencimiento,stock_actual)
                VALUES (?, ?, ?, 4) RETURNING id_lote
                """, Integer.class, medicamento, "ALT-" + sufijo, LocalDate.now().plusDays(90));
        jdbc.update("INSERT INTO lote(id_medicamento,numero_lote,fecha_vencimiento,stock_actual) VALUES (?,?,?,?)",
                medicamento, "ALT-VENCIDO-" + sufijo, LocalDate.now().minusDays(1), 6);

        int orden = jdbc.queryForObject("""
                INSERT INTO orden_compra(numero_orden,id_usuario,id_proveedor,estado,total_estimado)
                VALUES (?, ?, ?, 'PARCIALMENTE_RECIBIDA', 10) RETURNING id_orden
                """, Integer.class, "ALT-" + sufijo, usuario, proveedor);
        int detalle = jdbc.queryForObject("""
                INSERT INTO detalle_orden_compra(id_orden,id_medicamento,cantidad_solicitada,costo_unitario,importe_estimado)
                VALUES (?, ?, 10, 1, 10) RETURNING id_detalle_orden
                """, Integer.class, orden, medicamento);
        int movimiento = jdbc.queryForObject("""
                INSERT INTO movimiento_inventario(id_usuario,id_proveedor,id_orden,tipo,motivo)
                VALUES (?, ?, ?, 'ENTRADA', 'Prueba') RETURNING id_movimiento
                """, Integer.class, usuario, proveedor, orden);
        jdbc.update("INSERT INTO detalle_movimiento(id_movimiento,id_lote,id_detalle_orden,cantidad) VALUES (?,?,?,3)",
                movimiento, lote, detalle);

        var resumen = medicamentos.listarResumenStock(LocalDate.now()).stream()
                .filter(item -> item.idMedicamento() == medicamento).findFirst().orElseThrow();
        var porVencer = lotes.listarPorVencer(LocalDate.now(), LocalDate.now().plusDays(90));
        var vencidos = lotes.listarVencidos(LocalDate.now());

        assertEquals(4, resumen.stockDisponible());
        assertEquals(7, resumen.pendienteRecibir());
        assertTrue(porVencer.stream().anyMatch(item -> item.idLote() == lote && item.diasParaVencer() == 90));
        assertTrue(vencidos.stream().anyMatch(item -> item.numeroLote().equals("ALT-VENCIDO-" + sufijo)));
    }
}
