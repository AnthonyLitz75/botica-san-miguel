package com.farmacia.seguridad;

import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Spec 020, US-1 (CA-1.1 a CA-1.3): registro de último acceso en login real. */
@AutoConfigureMockMvc
class UsuarioUltimoAccesoLoginTest extends AbstractRepositorioIntegrationTest {

    private static final String CLAVE = "ClaveValida2026!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder codificador;

    private String usuario;

    @BeforeEach
    void crearUsuario() {
        usuario = "uacc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES (?, ?, ?, 'VENDEDOR', true)
                """, "Usuario Acceso Test", usuario, codificador.encode(CLAVE));
    }

    private LocalDateTime obtenerUltimoAcceso(String nombreUsuario) {
        return jdbcTemplate.queryForObject(
                "SELECT ultimo_acceso FROM usuario WHERE nombre_usuario = ?",
                LocalDateTime.class,
                nombreUsuario);
    }

    @Test
    @DisplayName("CA-1.1: login correcto por formulario actualiza ultimo_acceso")
    void loginCorrectoActualizaUltimoAcceso() throws Exception {
        assertNull(obtenerUltimoAcceso(usuario), "Inicialmente debe ser NULL");

        mvc.perform(formLogin("/login").user(usuario).password(CLAVE));

        assertNotNull(obtenerUltimoAcceso(usuario), "Tras login exitoso, debe tener fecha/hora de acceso");
    }

    @Test
    @DisplayName("CA-1.2: login fallido por formulario no actualiza ultimo_acceso")
    void loginFallidoNoActualizaUltimoAcceso() throws Exception {
        assertNull(obtenerUltimoAcceso(usuario), "Inicialmente debe ser NULL");

        mvc.perform(formLogin("/login").user(usuario).password("ClaveInvalida999"));

        assertNull(obtenerUltimoAcceso(usuario), "Un intento fallido no debe modificar ultimo_acceso");
    }

    @Test
    @DisplayName("CA-1.3: peticion autenticada por API Basic (sin sesion) no modifica ultimo_acceso")
    void peticionApiBasicNoModificaUltimoAcceso() throws Exception {
        assertNull(obtenerUltimoAcceso(usuario), "Inicialmente debe ser NULL");

        mvc.perform(get("/api/medicamentos").with(httpBasic(usuario, CLAVE)));

        assertNull(obtenerUltimoAcceso(usuario), "HTTP Basic en la API no debe actualizar ultimo_acceso");
    }
}
