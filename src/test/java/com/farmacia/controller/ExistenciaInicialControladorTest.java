package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.dto.ExistenciaInicialFormulario;
import com.farmacia.model.Medicamento;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ExistenciaInicialControlador.class)
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class ExistenciaInicialControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MovimientoInventarioServicio movimientoServicio;

    @MockitoBean
    private MedicamentoServicio medicamentoServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarioDetallesServicio;

    private Medicamento medicamentoActivo() {
        return new Medicamento(1, "MED-01", "Paracetamol 500mg", "Paracetamol", "500mg", "TABLETA",
                new BigDecimal("0.50"), 10, true, 0);
    }

    // -------------------------------------------------------------------------
    // Seguridad y Roles (CA-1.6)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Solicitud anónima a GET y POST redirige al login (302)")
    void sinSesion_redirigeALogin() throws Exception {
        mvc.perform(get("/inventario/existencias-iniciales"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));

        mvc.perform(post("/inventario/existencias-iniciales").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @DisplayName("Usuario con rol VENDEDOR recibe 403 Forbidden")
    void conRolVendedor_recibe403() throws Exception {
        mvc.perform(get("/inventario/existencias-iniciales").with(user("vendedor1").roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/inventario/existencias-iniciales").with(user("vendedor1").roles("VENDEDOR")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Usuario con rol COMPRAS recibe 403 Forbidden")
    void conRolCompras_recibe403() throws Exception {
        mvc.perform(get("/inventario/existencias-iniciales").with(user("compras1").roles("COMPRAS")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/inventario/existencias-iniciales").with(user("compras1").roles("COMPRAS")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Operaciones con ALMACENERO / ADMINISTRADOR
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /inventario/existencias-iniciales con ALMACENERO retorna 200 y vista formulario")
    void getFormulario_conAlmacenero_retorna200YVista() throws Exception {
        when(medicamentoServicio.listarActivos()).thenReturn(List.of(medicamentoActivo()));

        mvc.perform(get("/inventario/existencias-iniciales").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isOk())
                .andExpect(view().name("inventario/existencias-iniciales"))
                .andExpect(model().attributeExists("formulario"))
                .andExpect(model().attributeExists("medicamentos"));
    }

    @Test
    @DisplayName("POST /inventario/existencias-iniciales válido registra y redirige a /movimientos con flash")
    void registrar_conDatosValidos_redirigeAMovimientos() throws Exception {
        LocalDate futuro = LocalDate.now().plusMonths(6);
        when(movimientoServicio.registrarExistenciaInicial(eq("almacen1"), any(ExistenciaInicialFormulario.class)))
                .thenReturn(10);

        mvc.perform(post("/inventario/existencias-iniciales")
                        .param("idMedicamento", "1")
                        .param("numeroLote", "LOTE-2026-01")
                        .param("fechaVencimiento", futuro.toString())
                        .param("cantidad", "50")
                        .param("motivo", "Existencias iniciales")
                        .param("confirmarAcumulacion", "false")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/movimientos"))
                .andExpect(flash().attributeExists("exito"));

        verify(movimientoServicio).registrarExistenciaInicial(eq("almacen1"), any(ExistenciaInicialFormulario.class));
    }

    @Test
    @DisplayName("POST /inventario/existencias-iniciales con errores de validación no llama al servicio y recarga vista")
    void registrar_conErroresDeValidacion_recargaVista() throws Exception {
        when(medicamentoServicio.listarActivos()).thenReturn(List.of(medicamentoActivo()));

        mvc.perform(post("/inventario/existencias-iniciales")
                        .param("idMedicamento", "")
                        .param("numeroLote", "")
                        .param("cantidad", "0")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("inventario/existencias-iniciales"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeExists("medicamentos"));

        verify(movimientoServicio, never()).registrarExistenciaInicial(any(), any());
    }

    @Test
    @DisplayName("POST /inventario/existencias-iniciales con regla de negocio fallida captura error y recarga vista")
    void registrar_conErrorDeNegocio_muestraError() throws Exception {
        LocalDate futuro = LocalDate.now().plusMonths(6);
        when(medicamentoServicio.listarActivos()).thenReturn(List.of(medicamentoActivo()));
        when(movimientoServicio.registrarExistenciaInicial(eq("admin1"), any(ExistenciaInicialFormulario.class)))
                .thenThrow(new IllegalArgumentException("El lote ya existe con otra fecha de vencimiento"));

        mvc.perform(post("/inventario/existencias-iniciales")
                        .param("idMedicamento", "1")
                        .param("numeroLote", "LOTE-DUP")
                        .param("fechaVencimiento", futuro.toString())
                        .param("cantidad", "20")
                        .param("motivo", "Existencias iniciales")
                        .with(user("admin1").roles("ADMINISTRADOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("inventario/existencias-iniciales"))
                .andExpect(model().attribute("error", "El lote ya existe con otra fecha de vencimiento"))
                .andExpect(model().attributeExists("medicamentos"));
    }

    @Test
    @DisplayName("POST /inventario/existencias-iniciales vía AJAX válido retorna 200 con ok y url")
    void registrar_conAjaxValido_retornaJsonOkYUrl() throws Exception {
        LocalDate futuro = LocalDate.now().plusMonths(6);
        when(movimientoServicio.registrarExistenciaInicial(eq("almacen1"), any(ExistenciaInicialFormulario.class)))
                .thenReturn(15);

        mvc.perform(post("/inventario/existencias-iniciales")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Accept", "application/json")
                        .param("idMedicamento", "1")
                        .param("numeroLote", "LOTE-AJAX-01")
                        .param("fechaVencimiento", futuro.toString())
                        .param("cantidad", "30")
                        .param("motivo", "Existencias iniciales")
                        .param("confirmarAcumulacion", "false")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.url").value("/movimientos"))
                .andExpect(jsonPath("$.mensaje").value("Existencias iniciales registradas correctamente"));

        verify(movimientoServicio).registrarExistenciaInicial(eq("almacen1"), any(ExistenciaInicialFormulario.class));
    }

    @Test
    @DisplayName("POST /inventario/existencias-iniciales vía AJAX con errores retorna 400 con mapa de errores")
    void registrar_conAjaxInvalido_retorna400JsonErrores() throws Exception {
        mvc.perform(post("/inventario/existencias-iniciales")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Accept", "application/json")
                        .param("idMedicamento", "")
                        .param("numeroLote", "")
                        .param("cantidad", "0")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idMedicamento").exists())
                .andExpect(jsonPath("$.errores.numeroLote").exists())
                .andExpect(jsonPath("$.errores.cantidad").exists());

        verify(movimientoServicio, never()).registrarExistenciaInicial(any(), any());
    }

    @Test
    @DisplayName("POST /inventario/existencias-iniciales vía AJAX con error de negocio retorna 400")
    void registrar_conAjaxErrorNegocio_retorna400JsonError() throws Exception {
        LocalDate futuro = LocalDate.now().plusMonths(6);
        when(movimientoServicio.registrarExistenciaInicial(eq("admin1"), any(ExistenciaInicialFormulario.class)))
                .thenThrow(new IllegalArgumentException("Ese lote ya existe con la misma fecha de vencimiento. Marca «Sumar a un lote que ya existe» para añadir la cantidad."));

        mvc.perform(post("/inventario/existencias-iniciales")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Accept", "application/json")
                        .param("idMedicamento", "1")
                        .param("numeroLote", "LOTE-DUP")
                        .param("fechaVencimiento", futuro.toString())
                        .param("cantidad", "20")
                        .param("motivo", "Existencias iniciales")
                        .with(user("admin1").roles("ADMINISTRADOR"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.errores.numeroLote").exists());
    }

    @Test
    @DisplayName("GET /inventario/existencias-iniciales renderiza textos limpios y no contiene 'Volver a movimientos' ni aviso viejo")
    void getFormulario_renderizaTextosNuevosYSinVolverAMovimientos() throws Exception {
        when(medicamentoServicio.listarActivos()).thenReturn(List.of(medicamentoActivo()));

        mvc.perform(get("/inventario/existencias-iniciales").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INVENTARIO / TRAZABILIDAD")))
                .andExpect(content().string(containsString("Registrar existencias iniciales")))
                .andExpect(content().string(containsString("Úsalo para cargar el stock con el que empiezas, sin necesidad de una orden de compra.")))
                .andExpect(content().string(containsString("Sumar a un lote que ya existe")))
                .andExpect(content().string(not(containsString("Volver a movimientos"))))
                .andExpect(content().string(not(containsString("Carga inicial de inventario"))));
    }

    @Test
    @DisplayName("POST /inventario/existencias-iniciales vía AJAX mapea error de vencimiento a fechaVencimiento")
    void registrar_conAjaxErrorVencimiento_mapeaAFechaVencimiento() throws Exception {
        LocalDate futuro = LocalDate.now().plusMonths(6);
        when(movimientoServicio.registrarExistenciaInicial(eq("almacen1"), any(ExistenciaInicialFormulario.class)))
                .thenThrow(new IllegalArgumentException("La fecha de vencimiento debe ser posterior a hoy"));

        mvc.perform(post("/inventario/existencias-iniciales")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("idMedicamento", "1")
                        .param("numeroLote", "LOTE-VENC")
                        .param("fechaVencimiento", futuro.toString())
                        .param("cantidad", "10")
                        .param("motivo", "Existencias iniciales")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaVencimiento").value("La fecha de vencimiento debe ser posterior a hoy"));
    }

    @Test
    @DisplayName("POST /inventario/existencias-iniciales vía AJAX mapea error de cantidad a cantidad")
    void registrar_conAjaxErrorCantidad_mapeaACantidad() throws Exception {
        LocalDate futuro = LocalDate.now().plusMonths(6);
        when(movimientoServicio.registrarExistenciaInicial(eq("almacen1"), any(ExistenciaInicialFormulario.class)))
                .thenThrow(new IllegalArgumentException("La cantidad debe ser mayor que cero"));

        mvc.perform(post("/inventario/existencias-iniciales")
                        .header("Accept", "application/json")
                        .param("idMedicamento", "1")
                        .param("numeroLote", "LOTE-CANT")
                        .param("fechaVencimiento", futuro.toString())
                        .param("cantidad", "10")
                        .param("motivo", "Existencias iniciales")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cantidad").value("La cantidad debe ser mayor que cero"));
    }

    @Test
    @DisplayName("POST /inventario/existencias-iniciales vía AJAX mapea error de medicamento a idMedicamento")
    void registrar_conAjaxErrorMedicamento_mapeaAMedicamento() throws Exception {
        LocalDate futuro = LocalDate.now().plusMonths(6);
        when(movimientoServicio.registrarExistenciaInicial(eq("almacen1"), any(ExistenciaInicialFormulario.class)))
                .thenThrow(new IllegalArgumentException("El medicamento especificado no existe o está inactivo"));

        mvc.perform(post("/inventario/existencias-iniciales")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("idMedicamento", "99")
                        .param("numeroLote", "LOTE-MED")
                        .param("fechaVencimiento", futuro.toString())
                        .param("cantidad", "10")
                        .param("motivo", "Existencias iniciales")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idMedicamento").value("El medicamento especificado no existe o está inactivo"));
    }

    @Test
    @DisplayName("POST /inventario/existencias-iniciales vía AJAX mapea error no clasificado a motivo")
    void registrar_conAjaxErrorOtro_mapeaAMotivo() throws Exception {
        LocalDate futuro = LocalDate.now().plusMonths(6);
        when(movimientoServicio.registrarExistenciaInicial(eq("almacen1"), any(ExistenciaInicialFormulario.class)))
                .thenThrow(new IllegalArgumentException("Error no clasificado del sistema"));

        mvc.perform(post("/inventario/existencias-iniciales")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("idMedicamento", "1")
                        .param("numeroLote", "LOTE-OTRO")
                        .param("fechaVencimiento", futuro.toString())
                        .param("cantidad", "10")
                        .param("motivo", "Existencias iniciales")
                        .with(user("almacen1").roles("ALMACENERO"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo").value("Error no clasificado del sistema"));
    }

    @Test
    @DisplayName("GET /inventario/existencias-iniciales conserva formulario si ya está en el modelo (redirectAttributes)")
    void getFormulario_conFormularioExistente_noSobrescribe() throws Exception {
        when(medicamentoServicio.listarActivos()).thenReturn(List.of(medicamentoActivo()));
        ExistenciaInicialFormulario previo = new ExistenciaInicialFormulario(1, "LOTE-PREVIO", LocalDate.now().plusMonths(6), 15, "Motivo previo", true);

        mvc.perform(get("/inventario/existencias-iniciales")
                        .flashAttr("formulario", previo)
                        .with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isOk())
                .andExpect(view().name("inventario/existencias-iniciales"))
                .andExpect(model().attribute("formulario", previo));
    }
}
