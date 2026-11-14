package com.farmacia.service;

import com.farmacia.dto.SalidaInventarioFormulario;
import com.farmacia.model.DetalleMovimientoInventario;
import com.farmacia.model.Lote;
import com.farmacia.model.MovimientoInventario;
import com.farmacia.model.Usuario;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.MovimientoInventarioRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MovimientoInventarioServicioTest {
    /** Auditoría simulada (spec 014). */
    private final AuditoriaServicio auditoria = org.mockito.Mockito.mock(AuditoriaServicio.class);

    private MovimientoInventarioRepositorio movimientoRepositorio;
    private LoteRepositorio loteRepositorio;
    private UsuarioRepositorio usuarioRepositorio;
    private MovimientoInventarioServicio servicio;

    @BeforeEach
    void setUp() {
        movimientoRepositorio = mock(MovimientoInventarioRepositorio.class);
        loteRepositorio = mock(LoteRepositorio.class);
        usuarioRepositorio = mock(UsuarioRepositorio.class);
        servicio = new MovimientoInventarioServicio(movimientoRepositorio, loteRepositorio, usuarioRepositorio, auditoria);
    }

    // -------------------------------------------------------------------------
    // 1. Pruebas de Lectura / Consulta
    // -------------------------------------------------------------------------

    @Test
    void listarDelegaEnRepositorioYDevuelveLista() {
        // Arrange
        var mov1 = new MovimientoInventario(1, 2, null, null, null, "SALIDA", LocalDateTime.now(), "Merma");
        var mov2 = new MovimientoInventario(2, 2, 3, null, 4, "ENTRADA", LocalDateTime.now(), "Compra");
        when(movimientoRepositorio.listar()).thenReturn(List.of(mov1, mov2));

        // Act
        List<MovimientoInventario> resultado = servicio.listar();

        // Assert
        assertEquals(2, resultado.size());
        assertEquals(mov1, resultado.get(0));
        assertEquals(mov2, resultado.get(1));
        verify(movimientoRepositorio).listar();
    }

    @Test
    void listarDetallesDelegaEnRepositorioConIdEspecifico() {
        // Arrange
        var detalle = new DetalleMovimientoInventario(7, "MED-01", "Amoxicilina", "L-001", "CAJA", 4);
        when(movimientoRepositorio.listarDetalles(15)).thenReturn(List.of(detalle));

        // Act
        List<DetalleMovimientoInventario> resultado = servicio.listarDetalles(15);

        // Assert
        assertEquals(1, resultado.size());
        assertEquals("MED-01", resultado.getFirst().codigoMedicamento());
        verify(movimientoRepositorio).listarDetalles(15);
    }

    @Test
    void buscarPorIdDevuelveMovimientoCuandoExiste() {
        // Arrange
        var mov = new MovimientoInventario(10, 2, null, null, null, "SALIDA", LocalDateTime.now(), "Deterioro");
        when(movimientoRepositorio.buscarPorId(10)).thenReturn(Optional.of(mov));

        // Act
        Optional<MovimientoInventario> resultado = servicio.buscarPorId(10);

        // Assert
        assertTrue(resultado.isPresent());
        assertEquals("Deterioro", resultado.get().motivo());
        verify(movimientoRepositorio).buscarPorId(10);
    }

    @Test
    void buscarPorIdDevuelveVacioCuandoNoExiste() {
        // Arrange
        when(movimientoRepositorio.buscarPorId(999)).thenReturn(Optional.empty());

        // Act
        Optional<MovimientoInventario> resultado = servicio.buscarPorId(999);

        // Assert
        assertTrue(resultado.isEmpty());
        verify(movimientoRepositorio).buscarPorId(999);
    }

    // -------------------------------------------------------------------------
    // 2. Pruebas de Registro Exitoso de Salida
    // -------------------------------------------------------------------------

    @Test
    void registraSalidaExitosamenteConRolAlmacenero() {
        // Arrange
        int idLote = 8;
        int cantidad = 6;
        String motivoConEspacios = "  Ajuste por rotura de empaque  ";
        String motivoNormalizado = "Ajuste por rotura de empaque";

        var usuario = new Usuario(3, "Carlos Almacén", "calmacen", "hash", "ALMACENERO", true);
        var lote = new Lote(idLote, 5, "LT-2026-X", LocalDate.now().plusMonths(8), 20);

        when(usuarioRepositorio.buscarPorNombreUsuario("calmacen")).thenReturn(Optional.of(usuario));
        when(loteRepositorio.buscarPorId(idLote)).thenReturn(Optional.of(lote));
        when(loteRepositorio.descontarStock(idLote, cantidad)).thenReturn(1);
        when(movimientoRepositorio.registrarSalida(3, motivoNormalizado)).thenReturn(105);

        // Act
        Integer idMovimiento = servicio.registrarSalida(idLote, "calmacen",
                new SalidaInventarioFormulario(cantidad, motivoConEspacios));

        // Assert
        assertEquals(105, idMovimiento);
        verify(usuarioRepositorio).buscarPorNombreUsuario("calmacen");
        verify(loteRepositorio).buscarPorId(idLote);
        verify(loteRepositorio).descontarStock(idLote, cantidad);
        verify(movimientoRepositorio).registrarSalida(3, motivoNormalizado);
        verify(movimientoRepositorio).registrarDetalle(105, idLote, cantidad);
    }

    @Test
    void registraSalidaExitosamenteConRolAdministrador() {
        // Arrange
        int idLote = 12;
        int cantidad = 2;
        String motivo = "Baja autorizada por gerencia";

        var usuario = new Usuario(1, "Super Admin", "admin", "hash", "ADMINISTRADOR", true);
        var lote = new Lote(idLote, 9, "LT-ADM", LocalDate.now().plusMonths(12), 10);

        when(usuarioRepositorio.buscarPorNombreUsuario("admin")).thenReturn(Optional.of(usuario));
        when(loteRepositorio.buscarPorId(idLote)).thenReturn(Optional.of(lote));
        when(loteRepositorio.descontarStock(idLote, cantidad)).thenReturn(1);
        when(movimientoRepositorio.registrarSalida(1, motivo)).thenReturn(200);

        // Act
        Integer idMovimiento = servicio.registrarSalida(idLote, "admin",
                new SalidaInventarioFormulario(cantidad, motivo));

        // Assert
        assertEquals(200, idMovimiento);
        verify(loteRepositorio).descontarStock(idLote, cantidad);
        verify(movimientoRepositorio).registrarSalida(1, motivo);
        verify(movimientoRepositorio).registrarDetalle(200, idLote, cantidad);
    }

    // -------------------------------------------------------------------------
    // 3. Validaciones de Cantidad y Motivo (Casos Negativos)
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = { 0, -1, -50 })
    void rechazaCantidadNulaOMenorIgualACero(Integer cantidadInvalida) {
        // Arrange
        var formulario = new SalidaInventarioFormulario(cantidadInvalida, "Merma justificada");

        // Act & Assert
        var excepcion = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarSalida(1, "almacen", formulario));
        assertEquals("La cantidad debe ser mayor que cero", excepcion.getMessage());

        verifyNoInteractions(usuarioRepositorio, loteRepositorio, movimientoRepositorio);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    void rechazaMotivoNuloVacioOEnBlanco(String motivoInvalido) {
        // Arrange
        var formulario = new SalidaInventarioFormulario(5, motivoInvalido);

        // Act & Assert
        var excepcion = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarSalida(1, "almacen", formulario));
        assertEquals("El motivo es obligatorio y admite hasta 250 caracteres", excepcion.getMessage());

        verifyNoInteractions(usuarioRepositorio, loteRepositorio, movimientoRepositorio);
    }

    @Test
    void rechazaMotivoQueSupera250Caracteres() {
        // Arrange
        String motivoLargo = "A".repeat(251);
        var formulario = new SalidaInventarioFormulario(5, motivoLargo);

        // Act & Assert
        var excepcion = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarSalida(1, "almacen", formulario));
        assertEquals("El motivo es obligatorio y admite hasta 250 caracteres", excepcion.getMessage());

        verifyNoInteractions(usuarioRepositorio, loteRepositorio, movimientoRepositorio);
    }

    // -------------------------------------------------------------------------
    // 4. Validaciones de Usuario y Permisos RBAC
    // -------------------------------------------------------------------------

    @Test
    void rechazaUsuarioInexistente() {
        // Arrange
        when(usuarioRepositorio.buscarPorNombreUsuario("no_existe")).thenReturn(Optional.empty());
        var formulario = new SalidaInventarioFormulario(3, "Merma");

        // Act & Assert
        var excepcion = assertThrows(AccessDeniedException.class,
                () -> servicio.registrarSalida(1, "no_existe", formulario));
        assertEquals("Usuario no autorizado", excepcion.getMessage());

        verifyNoInteractions(loteRepositorio);
        verify(movimientoRepositorio, never()).registrarSalida(anyInt(), anyString());
    }

    @Test
    void rechazaUsuarioInactivo() {
        // Arrange
        var usuarioInactivo = new Usuario(4, "Juan Pérez", "jperez", "hash", "ALMACENERO", false);
        when(usuarioRepositorio.buscarPorNombreUsuario("jperez")).thenReturn(Optional.of(usuarioInactivo));
        var formulario = new SalidaInventarioFormulario(3, "Merma");

        // Act & Assert
        var excepcion = assertThrows(AccessDeniedException.class,
                () -> servicio.registrarSalida(1, "jperez", formulario));
        assertEquals("Usuario no autorizado", excepcion.getMessage());

        verifyNoInteractions(loteRepositorio);
        verify(movimientoRepositorio, never()).registrarSalida(anyInt(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "VENDEDOR", "COMPRAS", "INVITADO", "OTRO" })
    void rechazaUsuarioConRolNoAutorizado(String rolNoAutorizado) {
        // Arrange
        var usuario = new Usuario(5, "Usuario Otro Rol", "urol", "hash", rolNoAutorizado, true);
        when(usuarioRepositorio.buscarPorNombreUsuario("urol")).thenReturn(Optional.of(usuario));
        var formulario = new SalidaInventarioFormulario(3, "Merma");

        // Act & Assert
        var excepcion = assertThrows(AccessDeniedException.class,
                () -> servicio.registrarSalida(1, "urol", formulario));
        assertEquals("Usuario no autorizado", excepcion.getMessage());

        verifyNoInteractions(loteRepositorio);
        verify(movimientoRepositorio, never()).registrarSalida(anyInt(), anyString());
    }

    // -------------------------------------------------------------------------
    // 5. Validaciones de Lote y Stock
    // -------------------------------------------------------------------------

    @Test
    void rechazaLoteInexistente() {
        // Arrange
        var usuario = new Usuario(2, "Almacenero", "almacen", "hash", "ALMACENERO", true);
        when(usuarioRepositorio.buscarPorNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        when(loteRepositorio.buscarPorId(99)).thenReturn(Optional.empty());

        var formulario = new SalidaInventarioFormulario(4, "Lote dañado");

        // Act & Assert
        var excepcion = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarSalida(99, "almacen", formulario));
        assertEquals("Lote no encontrado", excepcion.getMessage());

        verify(loteRepositorio, never()).descontarStock(anyInt(), anyInt());
        verify(movimientoRepositorio, never()).registrarSalida(anyInt(), anyString());
    }

    @Test
    void rechazaSalidaCuandoStockEsInsuficiente() {
        // Arrange
        int idLote = 7;
        int cantidadSolicitada = 15;
        var usuario = new Usuario(2, "Almacenero", "almacen", "hash", "ALMACENERO", true);
        var lote = new Lote(idLote, 4, "LT-007", LocalDate.now().plusMonths(3), 10);

        when(usuarioRepositorio.buscarPorNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        when(loteRepositorio.buscarPorId(idLote)).thenReturn(Optional.of(lote));
        // descontarStock retorna 0 si stock_actual < cantidadSolicitada
        when(loteRepositorio.descontarStock(idLote, cantidadSolicitada)).thenReturn(0);

        var formulario = new SalidaInventarioFormulario(cantidadSolicitada, "Merma");

        // Act & Assert
        var excepcion = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarSalida(idLote, "almacen", formulario));
        assertEquals("No hay existencias suficientes para registrar la salida", excepcion.getMessage());

        verify(loteRepositorio).descontarStock(idLote, cantidadSolicitada);
        verify(movimientoRepositorio, never()).registrarSalida(anyInt(), anyString());
        verify(movimientoRepositorio, never()).registrarDetalle(anyInt(), anyInt(), anyInt());
    }
}
