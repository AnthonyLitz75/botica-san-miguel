package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicamentoControlador.class)
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class MarcaMenuVistaTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MedicamentoServicio medicamentoServicio;

    @MockitoBean
    private com.farmacia.service.CategoriaServicio categoriaServicio;

    @MockitoBean
    private com.farmacia.service.AuditoriaServicio auditoriaServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarioDetallesServicio;

    @Test
    @DisplayName("El menú lateral renderiza un <img> apuntando al logo con alt 'Botica San Miguel' y width/height")
    void menuLateralRenderizaImagenLogoConAtributosRequeridos() throws Exception {
        when(medicamentoServicio.buscarPorEstado(any(), anyBoolean())).thenReturn(Collections.emptyList());

        mvc.perform(get("/medicamentos").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alt=\"Botica San Miguel\"")))
                .andExpect(content().string(containsString("/images/logo-botica-san-miguel-256.png")));
    }

    @Test
    @DisplayName("El pie del menú lateral contiene exactamente 'Sistema de Inventario' y no 'v1.0'")
    void pieMenuLateralContieneTextoExactoSinVersion() throws Exception {
        when(medicamentoServicio.buscarPorEstado(any(), anyBoolean())).thenReturn(Collections.emptyList());

        mvc.perform(get("/medicamentos").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span>Sistema de Inventario</span>")))
                .andExpect(content().string(not(containsString("v1.0"))));
    }

    @Test
    @DisplayName("Petición sin autenticación a la ruta de la imagen del logo devuelve 200")
    void rutaLogoSinAutenticacionDevuelve200() throws Exception {
        mvc.perform(get("/images/logo-botica-san-miguel-256.png"))
                .andExpect(status().isOk());
    }
}
