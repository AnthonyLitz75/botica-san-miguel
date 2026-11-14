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

    @Test
    void renderizaEnlaceDarDeBajaParaAlmaceneroYLoOcultaParaVendedor() throws Exception {
        var loteVencido = new com.farmacia.model.AlertaVencimientoVista(
                42, 1, "MED-001", "Paracetamol", "LOTE-VENC-01",
                java.time.LocalDate.now().minusDays(5), 10, "CAJA", -5L, "VENCIDO"
        );
        when(servicio.consultarAlertas()).thenReturn(new PanelAlertasVista(List.of(), List.of(), List.of(loteVencido), List.of()));
        when(servicio.necesidadesReposicion()).thenReturn(List.of());

        // Almacenero ve el enlace "Dar de baja" hacia /lotes/42/salida?motivo=Baja%20por%20vencimiento
        var ctxAlmacen = SecurityContextHolder.createEmptyContext();
        ctxAlmacen.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("almacenero", null,
                List.of(new SimpleGrantedAuthority("ROLE_ALMACENERO"))));

        mvc.perform(get("/alertas").sessionAttr(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctxAlmacen))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dar de baja")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/lotes/42/salida?motivo=Baja%20por%20vencimiento")));

        // Vendedor NO ve el enlace, ve "Requiere rol de Almacén"
        var ctxVendedor = SecurityContextHolder.createEmptyContext();
        ctxVendedor.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("vendedor", null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDEDOR"))));

        mvc.perform(get("/alertas").sessionAttr(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctxVendedor))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/lotes/42/salida?motivo="))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Requiere rol de Almacén")));
    }
}
