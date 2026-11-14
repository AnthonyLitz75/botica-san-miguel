package com.farmacia.seguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.dto.UsuarioFormulario;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import com.farmacia.service.UsuarioServicio;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class UsuarioSesionInvalidaIntegracionTest extends AbstractRepositorioIntegrationTest {

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
    @DisplayName("CA-5.1: Desactivar usuario con sesión abierta invalida su siguiente petición y redirige a /login?desactivado")
    void desactivarUsuarioInvalidaSesionAbierta() throws Exception {
        String username = "vend_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String clavePlana = "ClaveSegura2026!";

        var form = new UsuarioFormulario("Vendedor Activo", username, "VENDEDOR", clavePlana);
        Integer idGenerado = usuarioServicio.crear(adminUsername, form);

        // 1. Vendedor inicia sesión vía formLogin y obtiene su sesión activa
        var loginResult = mvc.perform(formLogin("/login").user(username).password(clavePlana))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(username))
                .andReturn();

        MockHttpSession sesionVendedor = (MockHttpSession) loginResult.getRequest().getSession();

        // 2. Comprobar que la sesión funciona normalmente en una ruta protegida
        mvc.perform(get("/medicamentos").session(sesionVendedor))
                .andExpect(status().isOk());

        // 3. Administrador desactiva la cuenta del usuario en otra sesión / llamada al servicio
        usuarioServicio.desactivar(adminUsername, idGenerado);

        // 4. Siguiente petición con la sesión abierta del vendedor: debe ser rechazada y redirigir a /login?desactivado
        mvc.perform(get("/medicamentos").session(sesionVendedor))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?desactivado"));
    }

    @Test
    @DisplayName("CA-5.2: Cambiar rol de usuario con sesión abierta invalida su sesión y exige nuevo login")
    void cambiarRolInvalidaSesionAbierta() throws Exception {
        String username = "rol_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String clavePlana = "ClaveSegura2026!";

        var form = new UsuarioFormulario("Usuario Con Rol", username, "VENDEDOR", clavePlana);
        Integer idGenerado = usuarioServicio.crear(adminUsername, form);

        // 1. Inicia sesión como VENDEDOR
        var loginResult = mvc.perform(formLogin("/login").user(username).password(clavePlana))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(username))
                .andReturn();

        MockHttpSession sesionUsuario = (MockHttpSession) loginResult.getRequest().getSession();

        // 2. Accede con éxito a /medicamentos
        mvc.perform(get("/medicamentos").session(sesionUsuario))
                .andExpect(status().isOk());

        // 3. Administrador cambia su rol de VENDEDOR a COMPRAS
        var formEdicion = new UsuarioFormulario("Usuario Con Rol Modificado", username, "COMPRAS", null);
        usuarioServicio.actualizar(adminUsername, idGenerado, formEdicion);

        // 4. Siguiente petición con la sesión antigua: invalidada y redirigida a login
        mvc.perform(get("/medicamentos").session(sesionUsuario))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?desactivado"));

        // 5. Tras iniciar sesión nuevamente, obtiene una nueva sesión con su nuevo rol COMPRAS
        var nuevoLogin = mvc.perform(formLogin("/login").user(username).password(clavePlana))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(username).withRoles("COMPRAS"))
                .andReturn();

        MockHttpSession nuevaSesion = (MockHttpSession) nuevoLogin.getRequest().getSession();
        mvc.perform(get("/medicamentos").session(nuevaSesion))
                .andExpect(status().isOk());
    }
}
