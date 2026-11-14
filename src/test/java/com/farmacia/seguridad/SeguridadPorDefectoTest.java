package com.farmacia.seguridad;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.controller.AccesoControlador;
import com.farmacia.controller.NavegacionCompartida;
import com.farmacia.controller.PanelAlertaControlador;
import com.farmacia.service.AlertaInventarioServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 013, CA-1.1 y CA-1.4: las rutas no declaradas se deniegan y "/" lleva al inicio. */
@WebMvcTest({AccesoControlador.class, PanelAlertaControlador.class})
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class SeguridadPorDefectoTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private AlertaInventarioServicio alertas;
    @MockitoBean private UsuarioDetallesServicio usuarios;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ADMINISTRADOR", "VENDEDOR", "ALMACENERO", "COMPRAS"})
    @DisplayName("CA-1.1: una ruta sin regla responde 403 a cualquier rol")
    void rutaSinReglaSeDeniega(String rol) throws Exception {
        mvc.perform(get("/ruta-sin-regla").with(user("u").roles(rol)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ADMINISTRADOR", "VENDEDOR", "ALMACENERO", "COMPRAS"})
    @DisplayName("CA-1.1: un método no previsto sobre una ruta existente responde 403")
    void metodoNoPrevistoSeDeniega(String rol) throws Exception {
        mvc.perform(post("/alertas").with(user("u").roles(rol)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA-1.4: '/' lleva a /medicamentos con sesión")
    void raizConSesionVaAlInicio() throws Exception {
        mvc.perform(get("/").with(user("u").roles("VENDEDOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/medicamentos"));
    }

    @Test
    @DisplayName("CA-1.4: '/' sin sesión lleva al login")
    void raizSinSesionVaAlLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
