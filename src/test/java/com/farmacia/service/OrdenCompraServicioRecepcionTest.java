package com.farmacia.service;

import com.farmacia.dto.DetalleRecepcionFormulario;
import com.farmacia.dto.RecepcionOrdenFormulario;
import com.farmacia.model.OrdenCompra;
import com.farmacia.model.Usuario;
import com.farmacia.model.LineaRecepcionVista;
import com.farmacia.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrdenCompraServicioRecepcionTest {
    /** Auditoría simulada (spec 014). */
    private final AuditoriaServicio auditoria = org.mockito.Mockito.mock(AuditoriaServicio.class);
    private OrdenCompraRepositorio ordenes;
    private DetalleOrdenCompraRepositorio detalles;
    private UsuarioRepositorio usuarios;
    private ProveedorRepositorio proveedores;
    private MedicamentoRepositorio medicamentos;
    private LoteRepositorio lotes;
    private MovimientoInventarioRepositorio movimientos;
    private OrdenCompraServicio servicio;

    @BeforeEach
    void preparar() {
        ordenes = mock(OrdenCompraRepositorio.class);
        detalles = mock(DetalleOrdenCompraRepositorio.class);
        usuarios = mock(UsuarioRepositorio.class);
        proveedores = mock(ProveedorRepositorio.class);
        medicamentos = mock(MedicamentoRepositorio.class);
        lotes = mock(LoteRepositorio.class);
        movimientos = mock(MovimientoInventarioRepositorio.class);
        servicio = new OrdenCompraServicio(ordenes, detalles, usuarios, proveedores, medicamentos,
                lotes, movimientos, java.time.Clock.systemDefaultZone(), auditoria);
    }

    @Test
    void registraRecepcionParcialYActualizaEstado() {
        prepararOrden("PENDIENTE", 20, 0);
        when(movimientos.registrarEntrada(7, 9, "Recepción de compra")).thenReturn(31);
        when(ordenes.actualizarEstado(eq(9), anyString())).thenReturn(1);
        when(lotes.registrarOIncrementar(12, "L-2026", LocalDate.now().plusMonths(12), 8)).thenReturn(55);

        servicio.registrarRecepcion(9, "almacen", formulario(8));

        verify(movimientos).registrarDetalleEntrada(31, 55, 101, 8);
        verify(ordenes).actualizarEstado(9, "PARCIALMENTE_RECIBIDA");
    }

    @Test
    void completaOrdenCuandoSeRecibeElSaldo() {
        prepararOrden("PARCIALMENTE_RECIBIDA", 20, 12);
        when(movimientos.registrarEntrada(7, 9, "Recepción de compra")).thenReturn(31);
        when(lotes.registrarOIncrementar(12, "L-2026", LocalDate.now().plusMonths(12), 8)).thenReturn(55);
        when(ordenes.actualizarEstado(9, "RECIBIDA")).thenReturn(1);

        servicio.registrarRecepcion(9, "almacen", formulario(8));

        verify(ordenes).actualizarEstado(9, "RECIBIDA");
    }

    @Test
    void rechazaCantidadMayorAlPendienteAntesDeEscribir() {
        prepararOrden("PENDIENTE", 20, 15);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarRecepcion(9, "almacen", formulario(6)));

        verifyNoInteractions(movimientos, lotes);
    }

    @Test
    void rechazaLineaQueNoPerteneceALaOrden() {
        prepararOrden("PENDIENTE", 20, 0);
        var ajena = new DetalleRecepcionFormulario(999, "L-2026", LocalDate.now().plusMonths(12), 2);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarRecepcion(9, "almacen", new RecepcionOrdenFormulario(List.of(ajena), null)));
        verifyNoInteractions(movimientos, lotes);
    }

    @Test
    void rechazaUsuarioSinRolDeAlmacen() {
        prepararOrden("PENDIENTE", 20, 0);
        when(usuarios.buscarPorNombreUsuario("compras")).thenReturn(Optional.of(usuario("COMPRAS")));

        assertThrows(AccessDeniedException.class,
                () -> servicio.registrarRecepcion(9, "compras", formulario(2)));
    }

    @Test
    void soloCancelaOrdenPendienteSinRecepciones() {
        prepararOrden("PENDIENTE", 20, 0);
        when(usuarios.buscarPorNombreUsuario("compras")).thenReturn(Optional.of(usuario("COMPRAS")));
        when(ordenes.actualizarEstado(9, "CANCELADA")).thenReturn(1);
        servicio.cancelarOrden(9, "compras");
        verify(ordenes).actualizarEstado(9, "CANCELADA");
    }

    @Test
    void noCancelaOrdenParcialmenteRecibida() {
        prepararOrden("PARCIALMENTE_RECIBIDA", 20, 1);
        when(usuarios.buscarPorNombreUsuario("compras")).thenReturn(Optional.of(usuario("COMPRAS")));
        assertThrows(IllegalArgumentException.class, () -> servicio.cancelarOrden(9, "compras"));
        verify(ordenes, never()).actualizarEstado(anyInt(), anyString());
    }

    @Test
    void elRelojInyectadoDefineElVencimientoMinimoDeUnaRecepcion() {
        var reloj = java.time.Clock.fixed(java.time.Instant.parse("2026-11-10T15:00:00Z"), java.time.ZoneId.of("UTC"));
        var conReloj = new OrdenCompraServicio(ordenes, detalles, usuarios, proveedores, medicamentos,
                lotes, movimientos, reloj, auditoria);
        prepararOrden("PENDIENTE", 20, 0);

        assertEquals(LocalDate.of(2026, 11, 11), conReloj.fechaMinimaVencimientoRecepcion());
        var venceHoy = new RecepcionOrdenFormulario(List.of(new DetalleRecepcionFormulario(
                101, "L-2026", LocalDate.of(2026, 11, 10), 2)), null);
        assertThrows(IllegalArgumentException.class, () -> conReloj.registrarRecepcion(9, "almacen", venceHoy));
        verify(movimientos, never()).registrarEntrada(anyInt(), anyInt(), anyString());
    }

    private RecepcionOrdenFormulario formulario(int cantidad) {
        return new RecepcionOrdenFormulario(List.of(new DetalleRecepcionFormulario(
                101, "L-2026", LocalDate.now().plusMonths(12), cantidad)), null);
    }

    private void prepararOrden(String estado, int solicitada, int recibida) {
        when(usuarios.buscarPorNombreUsuario("almacen")).thenReturn(Optional.of(usuario("ALMACENERO")));
        when(ordenes.buscarPorIdParaActualizar(9)).thenReturn(Optional.of(new OrdenCompra(
                9, "OC-9", 5, 3, LocalDateTime.now(), estado, BigDecimal.TEN, null)));
        when(detalles.listarLineasRecepcion(9)).thenReturn(List.of(new LineaRecepcionVista(
                101, 12, "MED-12", "Paracetamol", solicitada, recibida, solicitada - recibida)));
    }

    private Usuario usuario(String rol) {
        return new Usuario(7, "Usuario prueba", "prueba", "hash", rol, true);
    }
}
