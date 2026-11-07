package com.farmacia.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.LineaRecepcionVista;
import com.farmacia.model.Medicamento;
import com.farmacia.model.OrdenCompra;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.OrdenCompraServicio;
import com.farmacia.service.ProveedorServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@WebMvcTest(OrdenCompraControlador.class)
@Import(SeguridadConfiguracion.class)
class OrdenCompraRecepcionVistaTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private OrdenCompraServicio ordenes;
    @MockitoBean private ProveedorServicio proveedores;
    @MockitoBean private MedicamentoServicio medicamentos;
    @MockitoBean private UsuarioDetallesServicio usuarios;

    @BeforeEach
    void prepararOrden() {
        org.mockito.Mockito.when(ordenes.buscarPorId(4)).thenReturn(Optional.of(
                new OrdenCompra(4, "OC-0004", 1, 3, LocalDateTime.now(), "PENDIENTE", BigDecimal.TEN, null)));
        org.mockito.Mockito.when(ordenes.listarLineasRecepcion(4)).thenReturn(List.of(
                new LineaRecepcionVista(9, 5, "MED-005", "Paracetamol 500 mg", 10, 4, 6)));
    }

    @Test
    void renderizaFormularioDeRecepcionParaAlmacenero() throws Exception {
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("almacen", null,
                List.of(new SimpleGrantedAuthority("ROLE_ALMACENERO"))));
        mvc.perform(get("/ordenes-compra/4/recepcion").sessionAttr(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar recepción")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paracetamol 500 mg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unidades por recibir")));
    }

    @Test
    void precargaMedicamentoYCantidadDesdeSugerenciaDeReposicion() throws Exception {
        org.mockito.Mockito.when(medicamentos.listarActivos()).thenReturn(List.of(
                new Medicamento(6, "MED-006", "Ibuprofeno 400 mg", "400 mg", "Tableta", "unidades",
                        BigDecimal.TEN, 20, true)));
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("compras", null,
                List.of(new SimpleGrantedAuthority("ROLE_COMPRAS"))));

        mvc.perform(get("/ordenes-compra/nuevo").param("medicamentoId", "6").param("cantidad", "12")
                        .sessionAttr(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MED-006 — Ibuprofeno 400 mg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"12\"")));
    }

}
