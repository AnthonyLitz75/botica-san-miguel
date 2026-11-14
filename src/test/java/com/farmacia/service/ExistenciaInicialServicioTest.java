package com.farmacia.service;

import com.farmacia.dto.ExistenciaInicialFormulario;
import com.farmacia.model.Lote;
import com.farmacia.model.Medicamento;
import com.farmacia.model.Usuario;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.MedicamentoRepositorio;
import com.farmacia.repository.MovimientoInventarioRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExistenciaInicialServicioTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneId.of("UTC"));
    private final LocalDate hoy = LocalDate.now(clock);

    private MovimientoInventarioRepositorio movimientoRepositorio;
    private LoteRepositorio loteRepositorio;
    private UsuarioRepositorio usuarioRepositorio;
    private MedicamentoRepositorio medicamentoRepositorio;
    private AuditoriaServicio auditoria;
    private MovimientoInventarioServicio servicio;

    @BeforeEach
    void setUp() {
        movimientoRepositorio = mock(MovimientoInventarioRepositorio.class);
        loteRepositorio = mock(LoteRepositorio.class);
        usuarioRepositorio = mock(UsuarioRepositorio.class);
        medicamentoRepositorio = mock(MedicamentoRepositorio.class);
        auditoria = mock(AuditoriaServicio.class);

        servicio = new MovimientoInventarioServicio(
                movimientoRepositorio,
                loteRepositorio,
                usuarioRepositorio,
                medicamentoRepositorio,
                clock,
                auditoria);
    }

    private Usuario usuario(String rol, boolean activo) {
        return new Usuario(1, "Carlos Almacén", "almacen1", "hash", rol, activo);
    }

    private Medicamento medicamento(boolean activo) {
        return new Medicamento(10, "MED-01", "Paracetamol 500mg", "Paracetamol", "500mg", "TABLETA",
                new BigDecimal("0.50"), 10, activo, 0);
    }

    // -------------------------------------------------------------------------
    // Roles (CA-1.6)
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"VENDEDOR", "COMPRAS"})
    @DisplayName("Roles no autorizados lanzan AccessDeniedException")
    void rolNoAutorizado_lanzaAccessDeniedException(String rol) {
        when(usuarioRepositorio.buscarPorNombreUsuario("user1"))
                .thenReturn(Optional.of(usuario(rol, true)));

        var form = new ExistenciaInicialFormulario(10, "LOTE-1", hoy.plusDays(30), 10, "Inicial", false);

        assertThrows(AccessDeniedException.class, () -> servicio.registrarExistenciaInicial("user1", form));
        verifyNoInteractions(movimientoRepositorio, loteRepositorio);
    }

    @Test
    @DisplayName("Usuario inactivo lanza AccessDeniedException")
    void usuarioInactivo_lanzaAccessDeniedException() {
        when(usuarioRepositorio.buscarPorNombreUsuario("admin1"))
                .thenReturn(Optional.of(usuario("ADMINISTRADOR", false)));

        var form = new ExistenciaInicialFormulario(10, "LOTE-1", hoy.plusDays(30), 10, "Inicial", false);

        assertThrows(AccessDeniedException.class, () -> servicio.registrarExistenciaInicial("admin1", form));
    }

    // -------------------------------------------------------------------------
    // Validaciones de datos (CA-1.4)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Medicamento inexistente lanza IllegalArgumentException")
    void medicamentoInexistente_lanzaIllegalArgumentException() {
        when(usuarioRepositorio.buscarPorNombreUsuario("almacen1"))
                .thenReturn(Optional.of(usuario("ALMACENERO", true)));
        when(medicamentoRepositorio.buscarPorId(999)).thenReturn(Optional.empty());

        var form = new ExistenciaInicialFormulario(999, "LOTE-1", hoy.plusDays(30), 10, "Inicial", false);

        var ex = assertThrows(IllegalArgumentException.class, () -> servicio.registrarExistenciaInicial("almacen1", form));
        assertTrue(ex.getMessage().contains("no existe"));
    }

    @Test
    @DisplayName("Medicamento inactivo lanza IllegalArgumentException")
    void medicamentoInactivo_lanzaIllegalArgumentException() {
        when(usuarioRepositorio.buscarPorNombreUsuario("almacen1"))
                .thenReturn(Optional.of(usuario("ALMACENERO", true)));
        when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(medicamento(false)));

        var form = new ExistenciaInicialFormulario(10, "LOTE-1", hoy.plusDays(30), 10, "Inicial", false);

        var ex = assertThrows(IllegalArgumentException.class, () -> servicio.registrarExistenciaInicial("almacen1", form));
        assertTrue(ex.getMessage().contains("inactivo"));
    }

    @Test
    @DisplayName("Vencimiento no futuro (hoy o pasado) lanza IllegalArgumentException")
    void vencimientoNoFuturo_lanzaIllegalArgumentException() {
        when(usuarioRepositorio.buscarPorNombreUsuario("almacen1"))
                .thenReturn(Optional.of(usuario("ALMACENERO", true)));
        when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(medicamento(true)));

        var formHoy = new ExistenciaInicialFormulario(10, "LOTE-1", hoy, 10, "Inicial", false);

        var ex = assertThrows(IllegalArgumentException.class, () -> servicio.registrarExistenciaInicial("almacen1", formHoy));
        assertTrue(ex.getMessage().contains("posterior a hoy"));
    }

    @Test
    @DisplayName("Cantidad menor o igual a cero lanza IllegalArgumentException")
    void cantidadInvalida_lanzaIllegalArgumentException() {
        when(usuarioRepositorio.buscarPorNombreUsuario("almacen1"))
                .thenReturn(Optional.of(usuario("ALMACENERO", true)));
        when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(medicamento(true)));

        var formCero = new ExistenciaInicialFormulario(10, "LOTE-1", hoy.plusDays(30), 0, "Inicial", false);

        var ex = assertThrows(IllegalArgumentException.class, () -> servicio.registrarExistenciaInicial("almacen1", formCero));
        assertTrue(ex.getMessage().contains("mayor que cero"));
    }

    // -------------------------------------------------------------------------
    // Reglas de Lote Preexistente (CA-1.2, CA-1.3)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CA-1.3: Lote existente con fecha de vencimiento distinta lanza IllegalArgumentException")
    void loteExistenteConFechaDistinta_lanzaIllegalArgumentException() {
        when(usuarioRepositorio.buscarPorNombreUsuario("almacen1"))
                .thenReturn(Optional.of(usuario("ALMACENERO", true)));
        when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(medicamento(true)));

        var loteExistente = new Lote(50, 10, "LOTE-DUP", hoy.plusDays(60), 20);
        when(loteRepositorio.buscarPorMedicamentoYNumero(10, "LOTE-DUP")).thenReturn(Optional.of(loteExistente));

        // Intento con fecha diferente: hoy + 90
        var form = new ExistenciaInicialFormulario(10, "LOTE-DUP", hoy.plusDays(90), 10, "Inicial", true);

        var ex = assertThrows(IllegalArgumentException.class, () -> servicio.registrarExistenciaInicial("almacen1", form));
        assertTrue(ex.getMessage().contains("otra fecha de vencimiento"));
        verify(movimientoRepositorio, never()).registrarEntradaInicial(anyInt(), anyString());
    }

    @Test
    @DisplayName("CA-1.2: Lote existente con misma fecha sin confirmarAcumulacion lanza IllegalArgumentException")
    void loteExistenteSinConfirmarAcumulacion_lanzaIllegalArgumentException() {
        when(usuarioRepositorio.buscarPorNombreUsuario("almacen1"))
                .thenReturn(Optional.of(usuario("ALMACENERO", true)));
        when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(medicamento(true)));

        var fechaComun = hoy.plusDays(60);
        var loteExistente = new Lote(50, 10, "LOTE-DUP", fechaComun, 20);
        when(loteRepositorio.buscarPorMedicamentoYNumero(10, "LOTE-DUP")).thenReturn(Optional.of(loteExistente));

        var formSinConfirmar = new ExistenciaInicialFormulario(10, "LOTE-DUP", fechaComun, 15, "Inicial", false);

        var ex = assertThrows(IllegalArgumentException.class, () -> servicio.registrarExistenciaInicial("almacen1", formSinConfirmar));
        assertTrue(ex.getMessage().contains("Acumular"));
        verify(movimientoRepositorio, never()).registrarEntradaInicial(anyInt(), anyString());
    }

    // -------------------------------------------------------------------------
    // Flujos Exitosos (CA-1.1, CA-1.2, CA-1.8)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CA-1.1: Lote nuevo crea lote, movimiento ENTRADA inicial y auditoría EXISTENCIA_INICIAL")
    void loteNuevo_creaLoteMovimientoYAuditoria() {
        when(usuarioRepositorio.buscarPorNombreUsuario("almacen1"))
                .thenReturn(Optional.of(usuario("ALMACENERO", true)));
        when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(medicamento(true)));
        when(loteRepositorio.buscarPorMedicamentoYNumero(10, "LOTE-NEW")).thenReturn(Optional.empty());

        when(loteRepositorio.registrarOIncrementar(10, "LOTE-NEW", hoy.plusDays(40), 25)).thenReturn(101);
        when(movimientoRepositorio.registrarEntradaInicial(1, "Existencias iniciales")).thenReturn(201);

        var form = new ExistenciaInicialFormulario(10, "LOTE-NEW", hoy.plusDays(40), 25, "Existencias iniciales", false);

        Integer idMovimiento = servicio.registrarExistenciaInicial("almacen1", form);

        assertEquals(201, idMovimiento);
        verify(loteRepositorio).registrarOIncrementar(10, "LOTE-NEW", hoy.plusDays(40), 25);
        verify(movimientoRepositorio).registrarEntradaInicial(1, "Existencias iniciales");
        verify(movimientoRepositorio).registrarDetalle(201, 101, 25);
        verify(auditoria).registrarCambio(eq(AccionAuditoria.EXISTENCIA_INICIAL), eq("LOTE"), eq(101), anyString());
    }

    @Test
    @DisplayName("CA-1.2: Lote existente con misma fecha y confirmarAcumulacion=true acumula y registra")
    void loteExistenteConConfirmacion_acumulaStockYRegistra() {
        when(usuarioRepositorio.buscarPorNombreUsuario("admin1"))
                .thenReturn(Optional.of(usuario("ADMINISTRADOR", true)));
        when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(medicamento(true)));

        var fecha = hoy.plusDays(60);
        var loteExistente = new Lote(50, 10, "LOTE-DUP", fecha, 20);
        when(loteRepositorio.buscarPorMedicamentoYNumero(10, "LOTE-DUP")).thenReturn(Optional.of(loteExistente));

        when(loteRepositorio.registrarOIncrementar(10, "LOTE-DUP", fecha, 15)).thenReturn(50);
        when(movimientoRepositorio.registrarEntradaInicial(1, "Carga complementaria")).thenReturn(305);

        var form = new ExistenciaInicialFormulario(10, "LOTE-DUP", fecha, 15, "Carga complementaria", true);

        Integer idMovimiento = servicio.registrarExistenciaInicial("admin1", form);

        assertEquals(305, idMovimiento);
        verify(loteRepositorio).registrarOIncrementar(10, "LOTE-DUP", fecha, 15);
        verify(movimientoRepositorio).registrarEntradaInicial(1, "Carga complementaria");
        verify(movimientoRepositorio).registrarDetalle(305, 50, 15);
        verify(auditoria).registrarCambio(eq(AccionAuditoria.EXISTENCIA_INICIAL), eq("LOTE"), eq(50), anyString());
    }
}
