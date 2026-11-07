package com.farmacia.service;

import com.farmacia.dto.DetalleRecepcionFormulario;
import com.farmacia.dto.RecepcionOrdenFormulario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrdenCompraServicioRecepcionPersistenciaTest {
    @Autowired private OrdenCompraServicio servicio;
    @Autowired private JdbcTemplate jdbc;
    private int usuarioId;
    private int usuarioComprasId;
    private int proveedorId;
    private int medicamentoId;
    private int ordenId;
    private int detalleId;
    private String numeroLote;
    private String nombreUsuario;
    private String nombreUsuarioCompras;

    @BeforeEach
    void crearDatosAislados() {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        nombreUsuario = "b1-" + sufijo;
        usuarioId = jdbc.queryForObject("INSERT INTO usuario(nombre,nombre_usuario,clave_hash,rol) VALUES (?,?,?,'ALMACENERO') RETURNING id_usuario",
                Integer.class, "Prueba B1", nombreUsuario, "test");
        nombreUsuarioCompras = "b1-compras-" + sufijo;
        usuarioComprasId = jdbc.queryForObject("INSERT INTO usuario(nombre,nombre_usuario,clave_hash,rol) VALUES (?,?,?,'COMPRAS') RETURNING id_usuario",
                Integer.class, "Compras B1", nombreUsuarioCompras, "test");
        String ruc = "20" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
        proveedorId = jdbc.queryForObject("INSERT INTO proveedor(ruc,razon_social) VALUES (?,?) RETURNING id_proveedor",
                Integer.class, ruc, "Proveedor B1 " + sufijo);
        medicamentoId = jdbc.queryForObject("INSERT INTO medicamento(codigo,nombre,presentacion,unidad_control,precio_venta) VALUES (?,?,?,'UNIDAD',1) RETURNING id_medicamento",
                Integer.class, "B1-" + sufijo, "Medicamento B1", "Caja");
        ordenId = jdbc.queryForObject("INSERT INTO orden_compra(numero_orden,id_usuario,id_proveedor) VALUES (?,?,?) RETURNING id_orden",
                Integer.class, "OC-B1-" + sufijo, usuarioId, proveedorId);
        detalleId = jdbc.queryForObject("INSERT INTO detalle_orden_compra(id_orden,id_medicamento,cantidad_solicitada,costo_unitario,importe_estimado) VALUES (?,?,10,1,10) RETURNING id_detalle_orden",
                Integer.class, ordenId, medicamentoId);
        numeroLote = "LT-" + sufijo;
    }

    @AfterEach
    void eliminarDatosAislados() {
        if (ordenId != 0) {
            jdbc.update("DELETE FROM detalle_movimiento WHERE id_movimiento IN (SELECT id_movimiento FROM movimiento_inventario WHERE id_orden = ?) OR id_detalle_orden = ?", ordenId, detalleId);
            jdbc.update("DELETE FROM movimiento_inventario WHERE id_orden = ?", ordenId);
            jdbc.update("DELETE FROM detalle_orden_compra WHERE id_orden = ?", ordenId);
            jdbc.update("DELETE FROM orden_compra WHERE id_orden = ?", ordenId);
        }
        if (medicamentoId != 0) {
            jdbc.update("DELETE FROM lote WHERE id_medicamento = ?", medicamentoId);
            jdbc.update("DELETE FROM medicamento WHERE id_medicamento = ?", medicamentoId);
        }
        if (proveedorId != 0) jdbc.update("DELETE FROM proveedor WHERE id_proveedor = ?", proveedorId);
        if (usuarioId != 0) jdbc.update("DELETE FROM usuario WHERE id_usuario = ?", usuarioId);
        else if (nombreUsuario != null) jdbc.update("DELETE FROM usuario WHERE nombre_usuario = ?", nombreUsuario);
        if (usuarioComprasId != 0) jdbc.update("DELETE FROM usuario WHERE id_usuario = ?", usuarioComprasId);
        else if (nombreUsuarioCompras != null) jdbc.update("DELETE FROM usuario WHERE nombre_usuario = ?", nombreUsuarioCompras);
    }

    @Test
    void registraRecepcionesParcialYFinalConLotesYMovimientos() {
        LocalDate vencimiento = LocalDate.now().plusMonths(18);
        servicio.registrarRecepcion(ordenId, nombreUsuario, formulario(4, vencimiento));
        assertEquals("PARCIALMENTE_RECIBIDA", estadoOrden());
        assertEquals(4, stock(vencimiento));
        assertEquals(6, servicio.listarLineasRecepcion(ordenId).getFirst().cantidadPendiente());
        var progresoParcial = servicio.listarDetallesVista(ordenId).getFirst();
        assertEquals(4, progresoParcial.cantidadRecibida());
        assertEquals(6, progresoParcial.cantidadPendiente());

        servicio.registrarRecepcion(ordenId, nombreUsuario, formulario(6, vencimiento));
        assertEquals("RECIBIDA", estadoOrden());
        assertEquals(10, stock(vencimiento));
        var progresoCompleto = servicio.listarDetallesVista(ordenId).getFirst();
        assertEquals(10, progresoCompleto.cantidadRecibida());
        assertEquals(0, progresoCompleto.cantidadPendiente());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_inventario WHERE id_orden = ? AND tipo = 'ENTRADA'", Integer.class, ordenId));
    }

    @Test
    void revierteMovimientoSiElLoteExistenteTieneOtroVencimiento() {
        LocalDate vencimientoGuardado = LocalDate.now().plusMonths(18);
        LocalDate vencimientoRecibido = LocalDate.now().plusMonths(24);
        jdbc.update("INSERT INTO lote(id_medicamento,numero_lote,fecha_vencimiento,stock_actual) VALUES (?,?,?,5)",
                medicamentoId, numeroLote, vencimientoGuardado);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarRecepcion(ordenId, nombreUsuario, formulario(3, vencimientoRecibido)));

        assertEquals("PENDIENTE", estadoOrden());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_inventario WHERE id_orden = ?", Integer.class, ordenId));
        assertEquals(5, stock(vencimientoGuardado));
    }

    @Test
    void cancelaOrdenPendienteSinRecepciones() {
        servicio.cancelarOrden(ordenId, nombreUsuarioCompras);

        assertEquals("CANCELADA", estadoOrden());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_inventario WHERE id_orden = ?", Integer.class, ordenId));
    }

    private RecepcionOrdenFormulario formulario(int cantidad, LocalDate vencimiento) {
        return new RecepcionOrdenFormulario(List.of(new DetalleRecepcionFormulario(
                detalleId, numeroLote, vencimiento, cantidad)), null);
    }

    private String estadoOrden() {
        return jdbc.queryForObject("SELECT estado FROM orden_compra WHERE id_orden = ?", String.class, ordenId);
    }

    private int stock(LocalDate vencimiento) {
        return jdbc.queryForObject("SELECT stock_actual FROM lote WHERE id_medicamento = ? AND numero_lote = ? AND fecha_vencimiento = ?",
                Integer.class, medicamentoId, numeroLote, vencimiento);
    }
}
