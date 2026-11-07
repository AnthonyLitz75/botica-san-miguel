package com.farmacia.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.Lote;
import com.farmacia.model.LoteDisponibleVista;
import com.farmacia.model.Medicamento;
import com.farmacia.service.LotePrioridadServicio;
import com.farmacia.service.LoteServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.MovimientoInventarioServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest({ LoteControlador.class, SalidaInventarioControlador.class })
@Import(SeguridadConfiguracion.class)
class FefoIndicadorVistaTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private LoteServicio lotes;
    @MockitoBean private MedicamentoServicio medicamentos;
    @MockitoBean private MovimientoInventarioServicio movimientos;
    @MockitoBean private LotePrioridadServicio prioridad;
    @MockitoBean private UsuarioDetallesServicio usuarios;

    private final LocalDate primerVencimiento = LocalDate.now().plusDays(10);
    private final List<LoteDisponibleVista> disponibles = List.of(
            new LoteDisponibleVista(2, 4, "MED-004", "Paracetamol 500 mg", "LT-002", primerVencimiento, 8, "tabletas"),
            new LoteDisponibleVista(1, 4, "MED-004", "Paracetamol 500 mg", "LT-001", primerVencimiento.plusDays(20), 12, "tabletas"));

    @BeforeEach
    void prepararCatalogos() {
        when(medicamentos.buscarPorId(4)).thenReturn(Optional.of(new Medicamento(4, "MED-004",
                "Paracetamol 500 mg", "500 mg", "Tableta", "tabletas", BigDecimal.TEN, 10, true)));
        when(lotes.listarPorMedicamento(4)).thenReturn(List.of(
                new Lote(2, 4, "LT-002", primerVencimiento, 8),
                new Lote(1, 4, "LT-001", primerVencimiento.plusDays(20), 12)));
        when(lotes.listarDisponiblesPorMedicamento(4)).thenReturn(disponibles);
        when(lotes.listarDisponiblesConMedicamento()).thenReturn(disponibles);
        when(lotes.buscarPorId(2)).thenReturn(Optional.of(new Lote(2, 4, "LT-002", primerVencimiento, 8)));
        when(prioridad.identificarLotesPrioritarios(disponibles)).thenReturn(Set.of(2));
    }

    @Test
    void muestraElDistintivoEnLaListaDeLotes() throws Exception {
        mvc.perform(get("/medicamentos/4/lotes").sessionAttr(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Prioritario FEFO")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("LT-002")));
    }

    @Test
    void resaltaElLotePrioritarioEnElSelectorDeSalidas() throws Exception {
        mvc.perform(get("/salidas/nueva").sessionAttr(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("★ PRIORITARIO FEFO")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-prioritario=\"true\"")));
    }

    @Test
    void indicaSiElLoteSeleccionadoEsElPrioritario() throws Exception {
        mvc.perform(get("/lotes/2/salida").sessionAttr(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Prioritario FEFO")));
    }

    private org.springframework.security.core.context.SecurityContext contexto() {
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("almacen", null,
                List.of(new SimpleGrantedAuthority("ROLE_ALMACENERO"))));
        return contexto;
    }
}
