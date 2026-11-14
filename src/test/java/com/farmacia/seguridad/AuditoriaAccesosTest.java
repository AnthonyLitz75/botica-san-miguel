package com.farmacia.seguridad;

import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Spec 014, US-2 (CA-2.1 a 2.4) y CA-1.2: eventos de acceso con login real. */
@AutoConfigureMockMvc
class AuditoriaAccesosTest extends AbstractRepositorioIntegrationTest {
    private static final String CLAVE = "ClaveSegura2026";

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder codificador;

    private String usuario;

    @BeforeEach
    void crearUsuario() {
        usuario = "acc-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol) VALUES (?,?,?,?)",
                "Usuario acceso", usuario, codificador.encode(CLAVE), "VENDEDOR");
    }

    private List<Map<String, Object>> eventos(String nombre) {
        return jdbcTemplate.queryForList(
                "SELECT accion, resultado, detalle FROM auditoria WHERE nombre_usuario = ? ORDER BY id_auditoria",
                nombre);
    }

    @Test
    @DisplayName("CA-2.1: un inicio de sesión correcto registra INICIO_SESION con EXITO")
    void inicioDeSesionCorrecto() throws Exception {
        mvc.perform(formLogin("/login").user(usuario).password(CLAVE));

        var registro = eventos(usuario);
        assertEquals(1, registro.size(), registro.toString());
        assertEquals("INICIO_SESION", registro.getFirst().get("accion"));
        assertEquals("EXITO", registro.getFirst().get("resultado"));
    }

    @Test
    @DisplayName("CA-2.2 y CA-1.2: una clave incorrecta registra INICIO_SESION_FALLIDO sin guardar la clave")
    void claveIncorrecta() throws Exception {
        mvc.perform(formLogin("/login").user(usuario).password("ClaveEquivocada99"));

        var registro = eventos(usuario);
        assertEquals(1, registro.size(), registro.toString());
        assertEquals("INICIO_SESION_FALLIDO", registro.getFirst().get("accion"));
        assertEquals("FALLO", registro.getFirst().get("resultado"));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM auditoria
                WHERE detalle LIKE '%ClaveEquivocada99%' OR detalle LIKE '%{bcrypt}%' OR detalle ILIKE '%password%'
                """, Integer.class));
    }

    @Test
    @DisplayName("CA-2.2: un usuario inexistente también se registra con el nombre escrito")
    void usuarioInexistente() throws Exception {
        String inexistente = "nadie-" + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(formLogin("/login").user(inexistente).password("Cualquiera123"));

        var registro = eventos(inexistente);
        assertEquals(1, registro.size(), registro.toString());
        assertEquals("INICIO_SESION_FALLIDO", registro.getFirst().get("accion"));
    }

    @Test
    @DisplayName("CA-2.3: cerrar sesión registra CIERRE_SESION")
    void cierreDeSesion() throws Exception {
        mvc.perform(post("/logout").with(user(usuario).roles("VENDEDOR")).with(csrf()));

        var registro = eventos(usuario);
        assertTrue(registro.stream().anyMatch(e -> "CIERRE_SESION".equals(e.get("accion"))), registro.toString());
    }

    @Test
    @DisplayName("CA-2.4: un 403 por rol registra ACCESO_DENEGADO con método y ruta")
    void accesoDenegadoPorRol() throws Exception {
        mvc.perform(get("/proveedores/nuevo").with(user(usuario).roles("VENDEDOR")));

        var registro = eventos(usuario);
        assertEquals(1, registro.size(), registro.toString());
        assertEquals("ACCESO_DENEGADO", registro.getFirst().get("accion"));
        assertEquals("DENEGADO", registro.getFirst().get("resultado"));
        assertEquals("GET /proveedores/nuevo", registro.getFirst().get("detalle"));
    }

    @Test
    @DisplayName("Una visita sin sesión (redirección al login) no se registra como acceso denegado")
    void anonimoNoGeneraRuido() throws Exception {
        int antes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE accion = 'ACCESO_DENEGADO'", Integer.class);
        mvc.perform(get("/medicamentos"));
        assertEquals(antes, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE accion = 'ACCESO_DENEGADO'", Integer.class));
    }
}
