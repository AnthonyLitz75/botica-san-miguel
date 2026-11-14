package com.farmacia.service;

import com.farmacia.model.AsignacionLoteFefo;
import com.farmacia.model.Cliente;
import com.farmacia.model.ComprobanteVista;
import com.farmacia.model.DetalleVenta;
import com.farmacia.model.LineaPreparacionVentaVista;
import com.farmacia.model.Usuario;
import com.farmacia.repository.ClienteRepositorio;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.MovimientoInventarioRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import com.farmacia.repository.VentaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Spec 003 (US-1, US-2, US-3): reglas de VentaServicio con repositorios simulados. */
class VentaServicioTest {
    /** Auditoría simulada (spec 014). */
    private final AuditoriaServicio auditoria = org.mockito.Mockito.mock(AuditoriaServicio.class);
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);
    private static final BigDecimal PRECIO = new BigDecimal("8.50");

    private UsuarioRepositorio usuarios;
    private ClienteRepositorio clientes;
    private LotePrioridadServicio prioridad;
    private LoteRepositorio lotes;
    private VentaRepositorio ventas;
    private MovimientoInventarioRepositorio movimientos;
    private VentaServicio servicio;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepositorio.class);
        clientes = mock(ClienteRepositorio.class);
        prioridad = mock(LotePrioridadServicio.class);
        lotes = mock(LoteRepositorio.class);
        ventas = mock(VentaRepositorio.class);
        movimientos = mock(MovimientoInventarioRepositorio.class);
        servicio = new VentaServicio(usuarios, clientes, prioridad, lotes, ventas, movimientos, RELOJ, auditoria);
        when(usuarios.buscarPorNombreUsuario("vendedor")).thenReturn(Optional.of(usuario(7, "VENDEDOR")));
        when(ventas.siguienteNumeroComprobante()).thenReturn("B001-00000012");
        when(ventas.insertar(eq(7), any(), any(), any(), any(), eq("B001-00000012"))).thenReturn(12);
        when(movimientos.registrarSalidaPorVenta(7, 12, "Venta B001-00000012")).thenReturn(40);
    }

    @Test
    @DisplayName("Camino feliz: descuenta el lote, guarda venta, detalle y salida con el precio de la línea")
    void confirmaUnaVentaDeUnLote() {
        when(prioridad.planificarSalida(5, 4)).thenReturn(List.of(tramo(3, 30, 4)));
        when(lotes.descontarStockVigente(3, 4, HOY)).thenReturn(1);

        var resultado = servicio.confirmar("vendedor", null, List.of(linea(5, 4)));

        assertEquals(12, resultado.idVenta());
        assertEquals("B001-00000012", resultado.numeroComprobante());
        verify(ventas).insertar(7, null, new BigDecimal("34.00"), new BigDecimal("0.00"),
                new BigDecimal("34.00"), "B001-00000012");
        verify(ventas).insertarDetalle(12, new DetalleVenta(3, 4, PRECIO, new BigDecimal("0.00"),
                new BigDecimal("34.00")));
        verify(movimientos).registrarDetalle(40, 3, 4);
    }

    @Test
    @DisplayName("CA-1.2: 15 unidades en lotes 10 + 5; se descuentan en orden ascendente de id_lote")
    void reparteEnDosLotesYDescuentaEnOrdenDeIdLote() {
        when(prioridad.planificarSalida(5, 15)).thenReturn(List.of(tramo(9, 10, 10), tramo(3, 20, 5)));
        when(lotes.descontarStockVigente(anyInt(), anyInt(), eq(HOY))).thenReturn(1);

        servicio.confirmar("vendedor", null, List.of(linea(5, 15)));

        InOrder orden = inOrder(lotes);
        orden.verify(lotes).descontarStockVigente(3, 5, HOY);
        orden.verify(lotes).descontarStockVigente(9, 10, HOY);
        verify(ventas).insertarDetalle(eq(12), eq(new DetalleVenta(9, 10, PRECIO, new BigDecimal("0.00"),
                new BigDecimal("85.00"))));
        verify(ventas).insertarDetalle(eq(12), eq(new DetalleVenta(3, 5, PRECIO, new BigDecimal("0.00"),
                new BigDecimal("42.50"))));
    }

    @Test
    @DisplayName("Spec 004 CA-3.1: descuento 5.00 en 10 unidades (lotes 4 y 6) → 2.00 y 3.00; total = subtotal − descuento")
    void guardaElDescuentoRepartidoPorTramo() {
        var conDescuento = new LineaPreparacionVentaVista(5, "MED-001", "Paracetamol", 10, PRECIO,
                new BigDecimal("5.00"), new BigDecimal("80.00"), List.of());
        when(prioridad.planificarSalida(5, 10)).thenReturn(List.of(tramo(3, 4, 4), tramo(9, 20, 6)));
        when(lotes.descontarStockVigente(anyInt(), anyInt(), eq(HOY))).thenReturn(1);

        servicio.confirmar("vendedor", null, List.of(conDescuento));

        verify(ventas).insertar(7, null, new BigDecimal("85.00"), new BigDecimal("5.00"),
                new BigDecimal("80.00"), "B001-00000012");
        verify(ventas).insertarDetalle(12, new DetalleVenta(3, 4, PRECIO, new BigDecimal("2.00"),
                new BigDecimal("32.00")));
        verify(ventas).insertarDetalle(12, new DetalleVenta(9, 6, PRECIO, new BigDecimal("3.00"),
                new BigDecimal("48.00")));
    }

    @Test
    @DisplayName("Un descuento mayor que la línea se rechaza antes de tocar el stock")
    void rechazaDescuentoInvalidoDeLaLinea() {
        var invalida = new LineaPreparacionVentaVista(5, "MED-001", "Paracetamol", 1, PRECIO,
                new BigDecimal("9.00"), new BigDecimal("-0.50"), List.of());

        assertThrows(IllegalArgumentException.class, () -> servicio.confirmar("vendedor", null, List.of(invalida)));
        verifyNoInteractions(lotes, ventas, movimientos);
    }

    @Test
    @DisplayName("Sin stock vigente al re-planificar: rechazo sin descontar ni guardar")
    void rechazaSiLaPlanificacionYaNoAlcanza() {
        when(prioridad.planificarSalida(5, 4)).thenThrow(new IllegalArgumentException("sin stock"));

        var error = assertThrows(StockInsuficienteException.class,
                () -> servicio.confirmar("vendedor", null, List.of(linea(5, 4))));

        assertTrue(error.getMessage().contains("Paracetamol"), error.getMessage());
        verifyNoInteractions(lotes, ventas, movimientos);
    }

    @Test
    @DisplayName("CA-2.1: si un descuento afecta 0 filas (stock cambió) se rechaza antes de guardar la venta")
    void rechazaSiElStockCambioAlDescontar() {
        when(prioridad.planificarSalida(5, 15)).thenReturn(List.of(tramo(3, 10, 10), tramo(9, 20, 5)));
        when(lotes.descontarStockVigente(3, 10, HOY)).thenReturn(1);
        when(lotes.descontarStockVigente(9, 5, HOY)).thenReturn(0);

        var error = assertThrows(StockInsuficienteException.class,
                () -> servicio.confirmar("vendedor", null, List.of(linea(5, 15))));

        assertEquals("El stock cambió; vuelve a preparar la venta", error.getMessage());
        verify(ventas, never()).insertar(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(movimientos);
    }

    @Test
    @DisplayName("CA-1.3: una preparación vacía no se confirma")
    void rechazaPreparacionVacia() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> servicio.confirmar("vendedor", null, List.of()));
        assertEquals("La venta no tiene productos", error.getMessage());
        verifyNoInteractions(prioridad, lotes, ventas, movimientos);
    }

    @Test
    @DisplayName("CA-1.4: un cliente inexistente se rechaza sin tocar el stock")
    void rechazaClienteInexistente() {
        when(clientes.buscarPorId(99)).thenReturn(Optional.empty());

        var error = assertThrows(IllegalArgumentException.class,
                () -> servicio.confirmar("vendedor", 99, List.of(linea(5, 4))));

        assertEquals("El cliente seleccionado no existe", error.getMessage());
        verifyNoInteractions(prioridad, lotes, ventas, movimientos);
    }

    @Test
    @DisplayName("CA-1.4: con cliente existente la venta queda a su nombre")
    void guardaElCliente() {
        when(clientes.buscarPorId(5)).thenReturn(Optional.of(
                new Cliente(5, "DNI", "12345678", "Ana Pérez", null, null)));
        when(prioridad.planificarSalida(5, 1)).thenReturn(List.of(tramo(3, 30, 1)));
        when(lotes.descontarStockVigente(3, 1, HOY)).thenReturn(1);
        when(ventas.insertar(eq(7), eq(5), any(), any(), any(), eq("B001-00000012"))).thenReturn(12);

        servicio.confirmar("vendedor", 5, List.of(linea(5, 1)));

        verify(ventas).insertar(eq(7), eq(5), any(), any(), any(), eq("B001-00000012"));
    }

    @Test
    @DisplayName("Rol sin permiso de venta: AccessDeniedException")
    void rechazaRolSinPermiso() {
        when(usuarios.buscarPorNombreUsuario("almacen")).thenReturn(Optional.of(usuario(8, "ALMACENERO")));

        assertThrows(AccessDeniedException.class,
                () -> servicio.confirmar("almacen", null, List.of(linea(5, 1))));
        verifyNoInteractions(prioridad, lotes, ventas, movimientos);
    }

    @Test
    @DisplayName("CA-3.4: el vendedor solo ve sus comprobantes; el administrador ve todos")
    void visibilidadDelComprobante() {
        var comprobante = new ComprobanteVista(12, "B001-00000012", LocalDateTime.of(2026, 11, 10, 9, 0),
                7, "Vendedor", null, null, List.of(), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN);
        when(ventas.buscarComprobante(12)).thenReturn(Optional.of(comprobante));
        when(usuarios.buscarPorNombreUsuario("otro")).thenReturn(Optional.of(usuario(9, "VENDEDOR")));
        when(usuarios.buscarPorNombreUsuario("admin")).thenReturn(Optional.of(usuario(1, "ADMINISTRADOR")));

        assertEquals(comprobante, servicio.obtenerComprobante(12, "vendedor").orElseThrow());
        assertEquals(comprobante, servicio.obtenerComprobante(12, "admin").orElseThrow());
        assertThrows(AccessDeniedException.class, () -> servicio.obtenerComprobante(12, "otro"));
        assertTrue(servicio.obtenerComprobante(404, "admin").isEmpty());
        assertEquals("Público general", comprobante.clienteVisible());
    }

    private static LineaPreparacionVentaVista linea(int idMedicamento, int cantidad) {
        return new LineaPreparacionVentaVista(idMedicamento, "MED-001", "Paracetamol", cantidad, PRECIO,
                PRECIO.multiply(BigDecimal.valueOf(cantidad)), List.of());
    }

    private static AsignacionLoteFefo tramo(int idLote, int disponible, int asignada) {
        return new AsignacionLoteFefo(idLote, "LT-" + idLote, HOY.plusDays(30), disponible, asignada);
    }

    private static Usuario usuario(int id, String rol) {
        return new Usuario(id, "Usuario " + rol, "u" + id, "hash", rol, true);
    }
}
