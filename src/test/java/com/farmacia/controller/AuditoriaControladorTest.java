package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.EventoAuditoria;
import com.farmacia.service.AuditoriaServicio;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Spec 014, US-4: pantalla /auditoria, exportación CSV y permisos. */
@WebMvcTest(AuditoriaControlador.class)
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class AuditoriaControladorTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private AuditoriaServicio servicio;
    @MockitoBean private UsuarioDetallesServicio usuarios;

    @Test
    @DisplayName("CA-4.1: el administrador ve los eventos con filtros, rango efectivo y menú")
    void listado() throws Exception {
        var evento = new EventoAuditoria(1L, LocalDateTime.of(2026, 11, 13, 9, 30), 7, "admin_demo", "EDITAR",
                "MEDICAMENTO", "4", "EXITO", "precio_venta: 8.50 → 9.00", "127.0.0.1");
        when(servicio.consultar(eq("admin"), any(), anyInt())).thenReturn(new AuditoriaServicio.Consulta(
                List.of(evento), 1, 1, 1, LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 13)));

        mvc.perform(get("/auditoria").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("auditoria/listado"))
                .andExpect(model().attribute("puedeConsultarAuditoria", true))
                .andExpect(content().string(containsString("precio_venta: 8.50 → 9.00")))
                .andExpect(content().string(containsString("value=\"2026-11-07\"")))
                .andExpect(content().string(containsString("Exportar CSV")));
    }

    @Test
    @DisplayName("CA-4.2: un rango inválido muestra el mensaje de validación")
    void rangoInvalido() throws Exception {
        when(servicio.consultar(eq("admin"), any(), anyInt()))
                .thenThrow(new IllegalArgumentException("La fecha final no puede ser anterior a la inicial"));

        mvc.perform(get("/auditoria").param("desde", "2026-11-13").param("hasta", "2026-11-01")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La fecha final no puede ser anterior a la inicial")));
    }

    @Test
    @DisplayName("CA-4.3: el CSV se descarga como text/csv UTF-8 con BOM")
    void exportacionCsv() throws Exception {
        when(servicio.exportarCsv(eq("admin"), any())).thenReturn("fecha_hora,usuario\r\n");

        mvc.perform(get("/auditoria.csv").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("auditoria.csv")))
                .andExpect(content().bytes(("﻿" + "fecha_hora,usuario\r\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"VENDEDOR", "ALMACENERO", "COMPRAS"})
    @DisplayName("CA-4.4: los demás roles reciben 403 en la pantalla y en el CSV")
    void otrosRoles(String rol) throws Exception {
        mvc.perform(get("/auditoria").with(user("u").roles(rol))).andExpect(status().isForbidden());
        mvc.perform(get("/auditoria.csv").with(user("u").roles(rol))).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
}
