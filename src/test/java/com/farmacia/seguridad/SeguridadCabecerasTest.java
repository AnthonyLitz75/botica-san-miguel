package com.farmacia.seguridad;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.controller.AccesoControlador;
import com.farmacia.controller.NavegacionCompartida;
import com.farmacia.service.UsuarioDetallesServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 024 / SEG-02: Verificación de cabeceras de seguridad HTTP en la web.
 * Valida la presencia de Referrer-Policy strict-origin-when-cross-origin
 * preservando las cabeceras por defecto (X-Content-Type-Options, X-Frame-Options).
 */
@WebMvcTest(AccesoControlador.class)
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class SeguridadCabecerasTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private UsuarioDetallesServicio usuarios;

    @Test
    @DisplayName("GET /login incluye cabecera Referrer-Policy strict-origin-when-cross-origin y conserva cabeceras por defecto")
    void loginIncluyeReferrerPolicyYConservaCabecerasPorDefecto() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("GET /login no incluye script LiveReload sin perfil dev activo")
    void loginNoIncluyeLiveReloadSinPerfilDev() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("livereload"))));
    }

    @Nested
    @ActiveProfiles("dev")
    class ConPerfilDevActivo {

        @Autowired private Environment environment;

        @Test
        @DisplayName("GET /login no incluye 'livereload' ni '35729' aunque el perfil dev esté activo")
        void loginNoIncluyeLiveReloadNiPuertoConPerfilDev() throws Exception {
            assertTrue(Arrays.asList(environment.getActiveProfiles()).contains("dev"));
            mvc.perform(get("/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsStringIgnoringCase("livereload"))))
                    .andExpect(content().string(not(containsString("35729"))));
        }
    }
}
