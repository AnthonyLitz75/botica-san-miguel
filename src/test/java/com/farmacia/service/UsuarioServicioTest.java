package com.farmacia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farmacia.dto.UsuarioClaveFormulario;
import com.farmacia.dto.UsuarioFormulario;
import com.farmacia.model.Usuario;
import com.farmacia.repository.UsuarioRepositorio;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServicioTest {

    @Mock
    private UsuarioRepositorio repositorio;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditoriaServicio auditoria;

    private UsuarioServicio servicio;

    @BeforeEach
    void setUp() {
        servicio = new UsuarioServicio(repositorio, passwordEncoder, auditoria);
    }

    private void mockAdmin(String username) {
        when(repositorio.buscarPorNombreUsuario(username)).thenReturn(
                Optional.of(new Usuario(1, "Admin Principal", username, "{bcrypt}h", "ADMINISTRADOR", true)));
    }

    private void mockNoAdmin(String username, String rol) {
        when(repositorio.buscarPorNombreUsuario(username)).thenReturn(
                Optional.of(new Usuario(2, "Otro Usuario", username, "{bcrypt}h", rol, true)));
    }

    // -------------------------------------------------------------------------
    // US-1: Crear usuario
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Actor no administrador es rechazado con AccessDeniedException")
    void crear_actorNoAdmin_lanzaAccessDenied() {
        mockNoAdmin("almacen1", "ALMACENERO");
        var form = new UsuarioFormulario("Carlos Gomez", "carlos1", "VENDEDOR", "ClaveSegura2026!");

        assertThrows(AccessDeniedException.class, () -> servicio.crear("almacen1", form));
        verify(repositorio, never()).insertar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CA-1.2: Nombre de usuario duplicado lanza IllegalArgumentException")
    void crear_usuarioDuplicado_lanzaIllegalArgument() {
        mockAdmin("admin1");
        when(repositorio.buscarPorNombreUsuario("existente")).thenReturn(
                Optional.of(new Usuario(10, "Existente", "existente", "h", "VENDEDOR", true)));

        var form = new UsuarioFormulario("Nuevo User", "existente", "VENDEDOR", "ClaveSegura2026!");
        var ex = assertThrows(IllegalArgumentException.class, () -> servicio.crear("admin1", form));
        assertTrue(ex.getMessage().toLowerCase().contains("ya existe"));
        verify(repositorio, never()).insertar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CA-1.2: Nombre de usuario con caracteres inválidos o longitud fuera de 4-50 lanza error")
    void crear_usuarioInvalido_lanzaIllegalArgument() {
        mockAdmin("admin1");
        var formCorto = new UsuarioFormulario("User Corto", "abc", "VENDEDOR", "ClaveSegura2026!");
        assertThrows(IllegalArgumentException.class, () -> servicio.crear("admin1", formCorto));

        var formEspacios = new UsuarioFormulario("User Espacio", "user name", "VENDEDOR", "ClaveSegura2026!");
        assertThrows(IllegalArgumentException.class, () -> servicio.crear("admin1", formEspacios));
    }

    @Test
    @DisplayName("CA-1.3: Clave menor a 10 caracteres o sin letra y número lanza error")
    void crear_claveInvalida_lanzaIllegalArgument() {
        mockAdmin("admin1");

        // Menor a 10
        var formCorta = new UsuarioFormulario("User Test", "usertest1", "VENDEDOR", "C1ave!");
        assertThrows(IllegalArgumentException.class, () -> servicio.crear("admin1", formCorta));

        // Sin números
        var formSinNumero = new UsuarioFormulario("User Test", "usertest1", "VENDEDOR", "Sololetrasyespacios!");
        assertThrows(IllegalArgumentException.class, () -> servicio.crear("admin1", formSinNumero));

        // Sin letras
        var formSinLetra = new UsuarioFormulario("User Test", "usertest1", "VENDEDOR", "1234567890123");
        assertThrows(IllegalArgumentException.class, () -> servicio.crear("admin1", formSinLetra));
    }

    @Test
    @DisplayName("CA-1.3: Rol no permitido lanza error")
    void crear_rolInvalido_lanzaIllegalArgument() {
        mockAdmin("admin1");
        var form = new UsuarioFormulario("User Test", "usertest1", "SUPERUSER", "ClaveSegura2026!");
        assertThrows(IllegalArgumentException.class, () -> servicio.crear("admin1", form));
    }

    @Test
    @DisplayName("CA-1.1 y CA-6.1: Crear usuario válido codifica clave, persiste y audita sin filtrar clave")
    void crear_usuarioValido_persisteYAudita() {
        mockAdmin("admin1");
        when(repositorio.buscarPorNombreUsuario("vendedor_nuevo")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("ClaveSegura2026!")).thenReturn("{bcrypt}hashSeguro123");
        when(repositorio.insertar("Pedro Soto", "vendedor_nuevo", "{bcrypt}hashSeguro123", "VENDEDOR")).thenReturn(15);

        var form = new UsuarioFormulario("Pedro Soto", "vendedor_nuevo", "VENDEDOR", "ClaveSegura2026!");
        Integer idGenerado = servicio.crear("admin1", form);

        assertEquals(15, idGenerado);
        verify(repositorio).insertar("Pedro Soto", "vendedor_nuevo", "{bcrypt}hashSeguro123", "VENDEDOR");
        verify(auditoria).registrarCambio(eq(AccionAuditoria.CREAR), eq("USUARIO"), eq(15), contains("vendedor_nuevo"));
    }

    // -------------------------------------------------------------------------
    // US-2: Editar y cambiar rol
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CA-3.2: No se puede cambiar de rol al único administrador activo")
    void actualizar_cambiarRolUltimoAdmin_lanzaIllegalState() {
        mockAdmin("admin1");
        when(repositorio.buscarPorId(1)).thenReturn(
                Optional.of(new Usuario(1, "Admin Principal", "admin1", "h", "ADMINISTRADOR", true)));
        when(repositorio.contarAdministradoresActivos()).thenReturn(1);

        var form = new UsuarioFormulario("Admin Principal", "admin1", "VENDEDOR", null);
        assertThrows(IllegalStateException.class, () -> servicio.actualizar("admin1", 1, form));
        verify(repositorio, never()).actualizar(anyInt(), any(), any());
    }

    @Test
    @DisplayName("CA-2.1 y CA-6.1: Actualizar nombre y rol de usuario no-único-admin actualiza y audita")
    void actualizar_usuarioNormal_actualizaYAudita() {
        mockAdmin("admin1");
        when(repositorio.buscarPorId(2)).thenReturn(
                Optional.of(new Usuario(2, "Luis Perez", "luis2", "h", "VENDEDOR", true)));
        when(repositorio.actualizar(2, "Luis Fernando Perez", "ALMACENERO")).thenReturn(true);

        var form = new UsuarioFormulario("Luis Fernando Perez", "luis2", "ALMACENERO", null);
        servicio.actualizar("admin1", 2, form);

        verify(repositorio).actualizar(2, "Luis Fernando Perez", "ALMACENERO");
        verify(auditoria).registrarCambio(eq(AccionAuditoria.CAMBIAR_ROL), eq("USUARIO"), eq(2), anyString());
    }

    // -------------------------------------------------------------------------
    // US-2: Restablecer clave
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CA-1.3: Restablecer con clave que no cumple reglas lanza IllegalArgument")
    void cambiarClave_claveCorta_lanzaError() {
        mockAdmin("admin1");
        var form = new UsuarioClaveFormulario("debil");
        assertThrows(IllegalArgumentException.class, () -> servicio.cambiarClave("admin1", 5, form));
        verify(repositorio, never()).actualizarClave(anyInt(), anyString());
    }

    @Test
    @DisplayName("CA-2.2 y CA-6.1: Restablecer clave válida codifica y audita sin filtrar claves")
    void cambiarClave_valida_actualizaYAudita() {
        mockAdmin("admin1");
        when(repositorio.buscarPorId(5)).thenReturn(
                Optional.of(new Usuario(5, "Usuario Cinco", "cinco", "hViejo", "VENDEDOR", true)));
        when(passwordEncoder.encode("NuevaPassword2026!")).thenReturn("{bcrypt}nuevoHash999");
        when(repositorio.actualizarClave(5, "{bcrypt}nuevoHash999")).thenReturn(true);

        servicio.cambiarClave("admin1", 5, new UsuarioClaveFormulario("NuevaPassword2026!"));

        verify(repositorio).actualizarClave(5, "{bcrypt}nuevoHash999");
        verify(auditoria).registrarCambio(eq(AccionAuditoria.CAMBIAR_CLAVE), eq("USUARIO"), eq(5), anyString());
    }

    // -------------------------------------------------------------------------
    // US-3: Desactivar y activar
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CA-3.2: Desactivarse a uno mismo lanza IllegalArgumentException")
    void desactivar_aUnoMismo_lanzaError() {
        mockAdmin("admin1");
        when(repositorio.buscarPorId(1)).thenReturn(
                Optional.of(new Usuario(1, "Admin Principal", "admin1", "h", "ADMINISTRADOR", true)));

        assertThrows(IllegalArgumentException.class, () -> servicio.desactivar("admin1", 1));
        verify(repositorio, never()).cambiarEstado(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("CA-3.2: Desactivar al último administrador activo lanza IllegalStateException")
    void desactivar_alUltimoAdminActivo_lanzaError() {
        mockAdmin("admin_ejecutor");
        when(repositorio.buscarPorId(2)).thenReturn(
                Optional.of(new Usuario(2, "Otro Admin", "admin_otro", "h", "ADMINISTRADOR", true)));
        when(repositorio.contarAdministradoresActivos()).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> servicio.desactivar("admin_ejecutor", 2));
        verify(repositorio, never()).cambiarEstado(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("CA-3.1 y CA-6.1: Desactivar usuario válido actualiza estado a false y audita")
    void desactivar_valido_desactivaYAudita() {
        mockAdmin("admin1");
        when(repositorio.buscarPorId(3)).thenReturn(
                Optional.of(new Usuario(3, "Vendedor Tres", "vend3", "h", "VENDEDOR", true)));
        when(repositorio.cambiarEstado(3, false)).thenReturn(true);

        servicio.desactivar("admin1", 3);

        verify(repositorio).cambiarEstado(3, false);
        verify(auditoria).registrarCambio(eq(AccionAuditoria.DESACTIVAR), eq("USUARIO"), eq(3), anyString());
    }

    @Test
    @DisplayName("CA-6.1: Activar usuario actualiza estado a true y audita")
    void activar_valido_activaYAudita() {
        mockAdmin("admin1");
        when(repositorio.buscarPorId(3)).thenReturn(
                Optional.of(new Usuario(3, "Vendedor Tres", "vend3", "h", "VENDEDOR", false)));
        when(repositorio.cambiarEstado(3, true)).thenReturn(true);

        servicio.activar("admin1", 3);

        verify(repositorio).cambiarEstado(3, true);
        verify(auditoria).registrarCambio(eq(AccionAuditoria.ACTIVAR), eq("USUARIO"), eq(3), anyString());
    }
}
