package com.farmacia.service;

import com.farmacia.model.Usuario;
import com.farmacia.repository.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas unitarias de UsuarioDetallesServicio")
class UsuarioDetallesServicioTest {

    @Mock
    private UsuarioRepositorio repositorio;

    private UsuarioDetallesServicio servicio;

    @BeforeEach
    void setUp() {
        servicio = new UsuarioDetallesServicio(repositorio);
    }

    private Usuario crearUsuario(Integer id, String nombre, String username,
                                 String hash, String rol, boolean activo) {
        return new Usuario(
                id,
                nombre,
                username,
                hash,
                rol,
                activo
        );
    }

    // =========================================================================
    // 1. Pruebas de usuario existente y carga de credenciales
    // =========================================================================
    @Nested
    @DisplayName("1. loadUserByUsername con usuario existente")
    class UsuarioExistente {

        @Test
        @DisplayName("Debería mapear correctamente un usuario activo con credenciales y autoridad ROLE_")
        void deberiaCargarUsuarioActivoConCredencialesYRol() {
            // Dado
            String username = "admin";
            String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
            Usuario usuario = crearUsuario(1, "Administrador del Sistema", username, passwordHash, "ADMINISTRADOR", true);
            when(repositorio.buscarPorNombreUsuario(username)).thenReturn(Optional.of(usuario));

            // Cuando
            UserDetails userDetails = servicio.loadUserByUsername(username);

            // Entonces
            assertNotNull(userDetails);
            assertEquals(username, userDetails.getUsername());
            assertEquals(passwordHash, userDetails.getPassword());
            assertTrue(userDetails.isEnabled(), "Un usuario activo debe tener isEnabled() == true");
            assertTrue(userDetails.isAccountNonExpired());
            assertTrue(userDetails.isAccountNonLocked());
            assertTrue(userDetails.isCredentialsNonExpired());

            // Verificación de autoridades (Spring Security prefija ROLE_)
            assertTrue(userDetails.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .anyMatch(a -> a.equals("ROLE_ADMINISTRADOR")),
                    "Las autoridades deben incluir ROLE_ADMINISTRADOR");

            verify(repositorio, times(1)).buscarPorNombreUsuario(username);
            verifyNoMoreInteractions(repositorio);
        }

        @Test
        @DisplayName("Debería deshabilitar la cuenta cuando el usuario está inactivo (activo == false)")
        void deberiaDeshabilitarCuentaCuandoUsuarioEstaInactivo() {
            // Dado
            String username = "vendedor_inactivo";
            String passwordHash = "$2a$10$hashInactivo";
            Usuario usuarioInactivo = crearUsuario(2, "Vendedor Desactivado", username, passwordHash, "VENDEDOR", false);
            when(repositorio.buscarPorNombreUsuario(username)).thenReturn(Optional.of(usuarioInactivo));

            // Cuando
            UserDetails userDetails = servicio.loadUserByUsername(username);

            // Entonces
            assertNotNull(userDetails);
            assertEquals(username, userDetails.getUsername());
            assertFalse(userDetails.isEnabled(), "Un usuario con activo == false debe tener isEnabled() == false");

            assertTrue(userDetails.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .anyMatch(a -> a.equals("ROLE_VENDEDOR")),
                    "Las autoridades deben incluir ROLE_VENDEDOR");

            verify(repositorio, times(1)).buscarPorNombreUsuario(username);
            verifyNoMoreInteractions(repositorio);
        }

        @ParameterizedTest(name = "Mapeo de rol: {0} -> ROLE_{0}")
        @ValueSource(strings = {"ADMINISTRADOR", "COMPRAS", "ALMACENERO", "VENDEDOR"})
        @DisplayName("Debería mapear correctamente los cuatro roles operativos de la farmacia")
        void deberiaMapearCorrectamenteLosRolesDelSistema(String rol) {
            // Dado
            String username = "usuario_" + rol.toLowerCase();
            Usuario usuario = crearUsuario(10, "Usuario Prueba", username, "$2a$10$dummyHash", rol, true);
            when(repositorio.buscarPorNombreUsuario(username)).thenReturn(Optional.of(usuario));

            // Cuando
            UserDetails userDetails = servicio.loadUserByUsername(username);

            // Entonces
            assertNotNull(userDetails);
            assertEquals(username, userDetails.getUsername());
            assertTrue(userDetails.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .anyMatch(a -> a.equals("ROLE_" + rol)),
                    "Debe otorgarse la autoridad ROLE_" + rol);

            verify(repositorio, times(1)).buscarPorNombreUsuario(username);
            verifyNoMoreInteractions(repositorio);
        }
    }

    // =========================================================================
    // 2. Pruebas de usuario inexistente o casos límite
    // =========================================================================
    @Nested
    @DisplayName("2. loadUserByUsername con usuario inexistente o casos límite")
    class UsuarioInexistente {

        @Test
        @DisplayName("Debería lanzar UsernameNotFoundException con mensaje exacto cuando el usuario no existe")
        void deberiaLanzarExcepcionCuandoUsuarioNoExiste() {
            // Dado
            String usernameInexistente = "no_existe";
            when(repositorio.buscarPorNombreUsuario(usernameInexistente)).thenReturn(Optional.empty());

            // Cuando & Entonces
            UsernameNotFoundException excepcion = assertThrows(
                    UsernameNotFoundException.class,
                    () -> servicio.loadUserByUsername(usernameInexistente),
                    "Debe lanzar UsernameNotFoundException cuando el repositorio retorna Optional.empty()"
            );

            assertEquals("Usuario no encontrado", excepcion.getMessage());
            verify(repositorio, times(1)).buscarPorNombreUsuario(usernameInexistente);
            verifyNoMoreInteractions(repositorio);
        }

        @ParameterizedTest(name = "Búsqueda con username nulo o vacío: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("Debería delegar la búsqueda al repositorio y lanzar excepción si retorna vacío")
        void deberiaDelegarYFallarCuandoUsernameEsNuloOVacio(String usernameVacio) {
            // Dado
            when(repositorio.buscarPorNombreUsuario(usernameVacio)).thenReturn(Optional.empty());

            // Cuando & Entonces
            UsernameNotFoundException excepcion = assertThrows(
                    UsernameNotFoundException.class,
                    () -> servicio.loadUserByUsername(usernameVacio)
            );

            assertEquals("Usuario no encontrado", excepcion.getMessage());
            verify(repositorio, times(1)).buscarPorNombreUsuario(usernameVacio);
            verifyNoMoreInteractions(repositorio);
        }
    }
}
