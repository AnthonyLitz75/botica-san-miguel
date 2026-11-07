package com.farmacia.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.PanelAlertasVista;
import com.farmacia.service.AlertaInventarioServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PanelAlertaControlador.class)
@Import(SeguridadConfiguracion.class)
class PanelAlertaControladorTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AlertaInventarioServicio servicio;
    @MockitoBean private UsuarioDetallesServicio usuarios;

    @Test
    void renderizaPanelVacioParaPersonalAutorizado() throws Exception {
        when(servicio.consultarAlertas()).thenReturn(new PanelAlertasVista(List.of(), List.of(), List.of(), List.of()));
        when(servicio.necesidadesReposicion()).thenReturn(List.of());
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("vendedor", null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDEDOR"))));

        mvc.perform(get("/alertas").sessionAttr(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alertas de inventario")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Necesidades de reposición")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Próximos a vencer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No hay necesidades de reposición pendientes.")));
    }
}
