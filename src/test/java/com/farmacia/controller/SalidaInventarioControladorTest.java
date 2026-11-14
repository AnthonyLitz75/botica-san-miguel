package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.Lote;
import com.farmacia.model.LoteDisponibleVista;
import com.farmacia.model.Medicamento;
import com.farmacia.service.LotePrioridadServicio;
import com.farmacia.service.LoteServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.MovimientoInventarioServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(SalidaInventarioControlador.class)
@Import(SeguridadConfiguracion.class)
class SalidaInventarioControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LoteServicio loteServicio;

    @MockitoBean
    private MedicamentoServicio medicamentoServicio;

    @MockitoBean
    private MovimientoInventarioServicio movimientoServicio;

    @MockitoBean
    private LotePrioridadServicio prioridadServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    // -------------------------------------------------------------------------
    // 1. GET /salidas/nueva anónimo: 302 a /login
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 1: Solicitud anónima a /salidas/nueva redirige a /login (302)")
    void nuevaSalidaDirecta_sinAutenticacion_redirigeALogin() throws Exception {
        mvc.perform(get("/salidas/nueva"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));

        verify(loteServicio, never()).listarDisponiblesConMedicamento();
    }

    // -------------------------------------------------------------------------
    // 2. GET /salidas/nueva con VENDEDOR: 403 Forbidden
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 2: Usuario con rol VENDEDOR recibe 403 Forbidden en /salidas/nueva")
    void nuevaSalidaDirecta_conRolVendedor_retorna403Forbidden() throws Exception {
        mvc.perform(get("/salidas/nueva").with(user("vendedor1").roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verify(loteServicio, never()).listarDisponiblesConMedicamento();
    }

    // -------------------------------------------------------------------------
    // 3. GET /salidas/nueva con ALMACENERO: 200 y modelo correcto
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 3: Usuario con rol ALMACENERO accede a formulario directo (200 OK y modelo poblado)")
    void nuevaSalidaDirecta_conAlmacenero_retornaFormularioDirecto() throws Exception {
        var loteVista = new LoteDisponibleVista(
                5, 1, "MED-01", "Amoxicilina 500mg", "LOT-100", LocalDate.now().plusMonths(6), 30, "CAJA"
        );
        when(loteServicio.listarDisponiblesConMedicamento()).thenReturn(List.of(loteVista));
        when(prioridadServicio.identificarLotesPrioritarios(any())).thenReturn(Set.of(5));

        mvc.perform(get("/salidas/nueva").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isOk())
                .andExpect(view().name("salidas/formulario-directo"))
                .andExpect(model().attributeExists("lotes", "idsLotesPrioritarios", "formulario"));

        verify(loteServicio).listarDisponiblesConMedicamento();
    }

    // -------------------------------------------------------------------------
    // 4. POST /salidas/nueva sin CSRF: 403 Forbidden
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 4: POST /salidas/nueva sin CSRF es rechazado con 403 Forbidden")
    void guardarSalidaDirecta_sinCsrf_retorna403Forbidden() throws Exception {
        mvc.perform(post("/salidas/nueva")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .param("idLote", "5")
                        .param("cantidad", "2")
                        .param("motivo", "Ajuste merma"))
                .andExpect(status().isForbidden());

        verify(movimientoServicio, never()).registrarSalida(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 5. POST /salidas/nueva con validación fallida: 200 y errores
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 5: POST /salidas/nueva con datos inválidos retorna 200 y errores en campos")
    void guardarSalidaDirecta_conErroresValidacion_retornaFormularioConErrores() throws Exception {
        when(loteServicio.listarDisponiblesConMedicamento()).thenReturn(List.of());
        when(prioridadServicio.identificarLotesPrioritarios(any())).thenReturn(Set.of());

        mvc.perform(post("/salidas/nueva")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf())
                        .param("cantidad", "0")
                        .param("motivo", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("salidas/formulario-directo"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("formulario", "idLote", "cantidad", "motivo"));

        verify(movimientoServicio, never()).registrarSalida(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 6. POST /salidas/nueva con excepción de negocio: formulario y rechazo global
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 6: POST /salidas/nueva con stock insuficiente retorna formulario con error global salida.invalida")
    void guardarSalidaDirecta_conErrorNegocio_retornaFormularioConRechazoGlobal() throws Exception {
        when(loteServicio.listarDisponiblesConMedicamento()).thenReturn(List.of());
        when(prioridadServicio.identificarLotesPrioritarios(any())).thenReturn(Set.of());

        when(movimientoServicio.registrarSalida(eq(5), eq("almacen1"), any()))
                .thenThrow(new IllegalArgumentException("No hay existencias suficientes para registrar la salida"));

        mvc.perform(post("/salidas/nueva")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf())
                        .param("idLote", "5")
                        .param("cantidad", "40")
                        .param("motivo", "Salida excedente"))
                .andExpect(status().isOk())
                .andExpect(view().name("salidas/formulario-directo"))
                .andExpect(model().hasErrors());

        verify(movimientoServicio).registrarSalida(eq(5), eq("almacen1"), any());
    }

    // -------------------------------------------------------------------------
    // 7. POST /salidas/nueva exitoso: redirección correcta
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 7: POST /salidas/nueva válido redirige a /movimientos/{id}")
    void guardarSalidaDirecta_valida_redirigeADetalleMovimiento() throws Exception {
        when(movimientoServicio.registrarSalida(eq(5), eq("almacen1"), any()))
                .thenReturn(101);

        mvc.perform(post("/salidas/nueva")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf())
                        .param("idLote", "5")
                        .param("cantidad", "4")
                        .param("motivo", "Salida por rotura"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/movimientos/101"));

        verify(movimientoServicio).registrarSalida(eq(5), eq("almacen1"), any());
    }

    // -------------------------------------------------------------------------
    // 8. GET /lotes/{idLote}/salida existente: 200 y modelo correcto
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 8: GET /lotes/{idLote}/salida con lote existente retorna 200 y modelo completo")
    void nuevoPorLote_conLoteExistente_retornaFormularioLote() throws Exception {
        var lote = new Lote(5, 1, "LOT-100", LocalDate.now().plusMonths(6), 25);
        var medicamento = new Medicamento(1, "MED-01", "Amoxicilina", "500mg", "Caja", "CAJA", BigDecimal.valueOf(15.0), 5, true);
        var loteVista = new LoteDisponibleVista(5, 1, "MED-01", "Amoxicilina", "LOT-100", LocalDate.now().plusMonths(6), 25, "CAJA");

        when(loteServicio.buscarPorId(5)).thenReturn(Optional.of(lote));
        when(medicamentoServicio.buscarPorId(1)).thenReturn(Optional.of(medicamento));
        when(loteServicio.listarDisponiblesPorMedicamento(1)).thenReturn(List.of(loteVista));
        when(prioridadServicio.identificarLotesPrioritarios(any())).thenReturn(Set.of(5));

        mvc.perform(get("/lotes/5/salida").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isOk())
                .andExpect(view().name("salidas/formulario"))
                .andExpect(model().attributeExists("lote", "medicamento", "lotePrioritario", "formulario"));

        verify(loteServicio).buscarPorId(5);
        verify(medicamentoServicio).buscarPorId(1);
    }

    @Test
    @DisplayName("CA-2.3: GET /lotes/{idLote}/salida?motivo=Baja%20por%20vencimiento precarga motivo en el formulario")
    void nuevoPorLote_conMotivoParametro_precargaMotivoEnFormulario() throws Exception {
        var lote = new Lote(5, 1, "LOT-100", LocalDate.now().minusDays(1), 10);
        var medicamento = new Medicamento(1, "MED-01", "Amoxicilina", "500mg", "Caja", "CAJA", BigDecimal.valueOf(15.0), 5, true);

        when(loteServicio.buscarPorId(5)).thenReturn(Optional.of(lote));
        when(medicamentoServicio.buscarPorId(1)).thenReturn(Optional.of(medicamento));
        when(loteServicio.listarDisponiblesPorMedicamento(1)).thenReturn(List.of());
        when(prioridadServicio.identificarLotesPrioritarios(any())).thenReturn(Set.of());

        mvc.perform(get("/lotes/5/salida")
                        .param("motivo", "Baja por vencimiento")
                        .with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isOk())
                .andExpect(view().name("salidas/formulario"))
                .andExpect(model().attribute("formulario",
                        new com.farmacia.dto.SalidaInventarioFormulario(null, "Baja por vencimiento")));
    }

    @Test
    @DisplayName("CA-2.4: GET /lotes/{idLote}/salida con rol VENDEDOR retorna 403 Forbidden")
    void nuevoPorLote_conRolVendedor_retorna403Forbidden() throws Exception {
        mvc.perform(get("/lotes/5/salida").with(user("vendedor1").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA-2.4: GET /lotes/{idLote}/salida con rol COMPRAS retorna 403 Forbidden")
    void nuevoPorLote_conRolCompras_retorna403Forbidden() throws Exception {
        mvc.perform(get("/lotes/5/salida").with(user("compras1").roles("COMPRAS")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA-2.4: POST /lotes/{idLote}/salida con rol VENDEDOR retorna 403 Forbidden")
    void guardarPorLote_conRolVendedor_retorna403Forbidden() throws Exception {
        mvc.perform(post("/lotes/5/salida")
                        .with(user("vendedor1").roles("VENDEDOR"))
                        .with(csrf())
                        .param("cantidad", "1")
                        .param("motivo", "Intento no autorizado"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA-2.4: POST /lotes/{idLote}/salida con rol COMPRAS retorna 403 Forbidden")
    void guardarPorLote_conRolCompras_retorna403Forbidden() throws Exception {
        mvc.perform(post("/lotes/5/salida")
                        .with(user("compras1").roles("COMPRAS"))
                        .with(csrf())
                        .param("cantidad", "1")
                        .param("motivo", "Intento no autorizado"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // 9. GET /lotes/{idLote}/salida lote inexistente: 404
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 9: GET /lotes/{idLote}/salida con lote inexistente retorna 404 Not Found")
    void nuevoPorLote_conLoteInexistente_retorna404NotFound() throws Exception {
        when(loteServicio.buscarPorId(999)).thenReturn(Optional.empty());

        mvc.perform(get("/lotes/999/salida").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isNotFound());

        verify(loteServicio).buscarPorId(999);
        verify(medicamentoServicio, never()).buscarPorId(any());
    }

    // -------------------------------------------------------------------------
    // 10. POST /lotes/{idLote}/salida sin CSRF: 403 Forbidden
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 10: POST /lotes/{idLote}/salida sin CSRF es rechazado con 403 Forbidden")
    void guardarPorLote_sinCsrf_retorna403Forbidden() throws Exception {
        mvc.perform(post("/lotes/5/salida")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .param("cantidad", "2")
                        .param("motivo", "Ajuste de merma"))
                .andExpect(status().isForbidden());

        verify(movimientoServicio, never()).registrarSalida(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 11. POST /lotes/{idLote}/salida con validación fallida: 200 y errores
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 11: POST /lotes/{idLote}/salida con validación fallida recarga formulario con errores")
    void guardarPorLote_conErroresValidacion_retornaFormularioConErrores() throws Exception {
        var lote = new Lote(5, 1, "LOT-100", LocalDate.now().plusMonths(6), 25);
        var medicamento = new Medicamento(1, "MED-01", "Amoxicilina", "500mg", "Caja", "CAJA", BigDecimal.valueOf(15.0), 5, true);

        when(loteServicio.buscarPorId(5)).thenReturn(Optional.of(lote));
        when(medicamentoServicio.buscarPorId(1)).thenReturn(Optional.of(medicamento));
        when(loteServicio.listarDisponiblesPorMedicamento(1)).thenReturn(List.of());
        when(prioridadServicio.identificarLotesPrioritarios(any())).thenReturn(Set.of());

        mvc.perform(post("/lotes/5/salida")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf())
                        .param("cantidad", "0")
                        .param("motivo", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("salidas/formulario"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("formulario", "cantidad", "motivo"));

        verify(movimientoServicio, never()).registrarSalida(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 12. POST /lotes/{idLote}/salida con excepción de negocio: formulario y error global
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 12: POST /lotes/{idLote}/salida con stock insuficiente recarga formulario con error salida.invalida")
    void guardarPorLote_conErrorNegocio_retornaFormularioConRechazoGlobal() throws Exception {
        var lote = new Lote(5, 1, "LOT-100", LocalDate.now().plusMonths(6), 25);
        var medicamento = new Medicamento(1, "MED-01", "Amoxicilina", "500mg", "Caja", "CAJA", BigDecimal.valueOf(15.0), 5, true);

        when(loteServicio.buscarPorId(5)).thenReturn(Optional.of(lote));
        when(medicamentoServicio.buscarPorId(1)).thenReturn(Optional.of(medicamento));
        when(loteServicio.listarDisponiblesPorMedicamento(1)).thenReturn(List.of());
        when(prioridadServicio.identificarLotesPrioritarios(any())).thenReturn(Set.of());

        when(movimientoServicio.registrarSalida(eq(5), eq("almacen1"), any()))
                .thenThrow(new IllegalArgumentException("No hay existencias suficientes para registrar la salida"));

        mvc.perform(post("/lotes/5/salida")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf())
                        .param("cantidad", "30")
                        .param("motivo", "Salida mayor al stock"))
                .andExpect(status().isOk())
                .andExpect(view().name("salidas/formulario"))
                .andExpect(model().hasErrors());

        verify(movimientoServicio).registrarSalida(eq(5), eq("almacen1"), any());
    }

    // -------------------------------------------------------------------------
    // 13. POST /lotes/{idLote}/salida exitoso: redirección correcta
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 13: POST /lotes/{idLote}/salida válido redirige a /movimientos/{id}")
    void guardarPorLote_valido_redirigeADetalleMovimiento() throws Exception {
        var lote = new Lote(5, 1, "LOT-100", LocalDate.now().plusMonths(6), 25);
        var medicamento = new Medicamento(1, "MED-01", "Amoxicilina", "500mg", "Caja", "CAJA", BigDecimal.valueOf(15.0), 5, true);

        when(loteServicio.buscarPorId(5)).thenReturn(Optional.of(lote));
        when(medicamentoServicio.buscarPorId(1)).thenReturn(Optional.of(medicamento));
        when(loteServicio.listarDisponiblesPorMedicamento(1)).thenReturn(List.of());
        when(prioridadServicio.identificarLotesPrioritarios(any())).thenReturn(Set.of());

        when(movimientoServicio.registrarSalida(eq(5), eq("almacen1"), any()))
                .thenReturn(105);

        mvc.perform(post("/lotes/5/salida")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf())
                        .param("cantidad", "5")
                        .param("motivo", "Merma justificada"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/movimientos/105"));

        verify(movimientoServicio).registrarSalida(eq(5), eq("almacen1"), any());
    }
}
