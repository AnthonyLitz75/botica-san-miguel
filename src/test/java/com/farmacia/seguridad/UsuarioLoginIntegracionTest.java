package com.farmacia.seguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.dto.UsuarioClaveFormulario;
import com.farmacia.dto.UsuarioFormulario;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import com.farmacia.service.UsuarioServicio;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class UsuarioLoginIntegracionTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminUsername;

    @BeforeEach
    void setUpAdmin() {
        adminUsername = "adm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES ('Admin Integracion', ?, ?, 'ADMINISTRADOR', true)
                """, adminUsername, passwordEncoder.encode("AdminPass2026!"));
    }

    @Test
    @DisplayName("CA-1.1: Usuario creado con servicio puede iniciar sesión correctamente con su clave")
    void usuarioCreadoPuedeIniciarSesion() throws Exception {
        String username = "vend_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String clavePlana = "ClaveSegura2026!";

        var form = new UsuarioFormulario("Vendedor Prueba", username, "VENDEDOR", clavePlana);
        Integer idGenerado = usuarioServicio.crear(adminUsername, form);

        mvc.perform(formLogin("/login").user(username).password(clavePlana))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(username).withRoles("VENDEDOR"));
    }

    @Test
    @DisplayName("CA-3.1: Usuario desactivado no puede iniciar sesión (cuenta deshabilitada)")
    void usuarioDesactivadoNoPuedeIniciarSesion() throws Exception {
        String username = "vend_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String clavePlana = "ClaveSegura2026!";

        var form = new UsuarioFormulario("Vendedor Desactivado", username, "VENDEDOR", clavePlana);
        Integer idGenerado = usuarioServicio.crear(adminUsername, form);

        // Desactivar cuenta
        usuarioServicio.desactivar(adminUsername, idGenerado);

        // Intento de inicio de sesión debe ser rechazado
        mvc.perform(formLogin("/login").user(username).password(clavePlana))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("CA-2.2: Restablecer clave invalida la clave anterior y permite ingresar con la nueva")
    void restablecerClaveInvalidaAnteriorYPermiteNueva() throws Exception {
        String username = "vend_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String claveVieja = "ClaveVieja2026!";
        String claveNueva = "NuevaClaveSuper2026!";

        var form = new UsuarioFormulario("Vendedor Password", username, "VENDEDOR", claveVieVieja(claveVieja));
        Integer idGenerado = usuarioServicio.crear(adminUsername, form);

        // Restablecer clave
        usuarioServicio.cambiarClave(adminUsername, idGenerado, new UsuarioClaveFormulario(claveNueva));

        // Intento con clave vieja debe fallar
        mvc.perform(formLogin("/login").user(username).password(claveVieja))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());

        // Intento con clave nueva debe tener éxito
        mvc.perform(formLogin("/login").user(username).password(claveNueva))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(username).withRoles("VENDEDOR"));
    }

    private String claveVieVieja(String clave) {
        return clave;
    }
}
