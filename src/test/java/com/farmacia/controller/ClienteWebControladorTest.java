package com.farmacia.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.Cliente;
import com.farmacia.service.ClienteServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClienteWebControlador.class)
@Import(SeguridadConfiguracion.class)
class ClienteWebControladorTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ClienteServicio servicio;
    @MockitoBean private UsuarioDetallesServicio usuarios;

    @Test
    void listaClientesPorCriterioYEnlazaAlFormulario() throws Exception {
        when(servicio.listar("Ana" )).thenReturn(List.of(
                new Cliente(8, "DNI", "12345678", "Ana Torres", "987 654 321", "ana@example.com")));

        mvc.perform(get("/clientes").param("buscar", " Ana ").sessionAttr(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto("vendedor", "VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana Torres")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DNI")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nuevo cliente")));
        verify(servicio).listar("Ana");
    }

    @Test
    void editaClienteConSusDatosActuales() throws Exception {
        when(servicio.buscarPorId(8)).thenReturn(Optional.of(
                new Cliente(8, "RUC", "20123456789", "Botica Cliente S.A.C.", null, "contacto@example.com")));

        mvc.perform(get("/clientes/8/editar").sessionAttr(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto("admin", "ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Editar cliente")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"20123456789\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Botica Cliente S.A.C.")));
    }

    @Test
    void deniegaElModuloACompras() throws Exception {
        mvc.perform(get("/clientes").sessionAttr(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto("compras", "COMPRAS")))
                .andExpect(status().isForbidden());
    }

    private SecurityContext contexto(String usuario, String rol) {
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
        return contexto;
    }
}
