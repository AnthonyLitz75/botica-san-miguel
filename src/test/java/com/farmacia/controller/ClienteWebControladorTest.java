package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.dto.ClienteFormulario;
import com.farmacia.model.Cliente;
import com.farmacia.service.ClienteServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ClienteWebControlador.class)
@Import(SeguridadConfiguracion.class)
class ClienteWebControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ClienteServicio servicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    // =========================================================================
    // SECCIÓN A: AUTENTICACIÓN Y AUTORIZACIÓN
    // =========================================================================

    @Test
    @DisplayName("Escenario 1: Solicitud anónima a /clientes redirige a /login (302)")
    void listar_sinAutenticacion_redirigeALogin() throws Exception {
        mvc.perform(get("/clientes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));

        verify(servicio, never()).listar(any());
    }

    @Test
    @DisplayName("Escenario 2: Usuario con rol COMPRAS recibe 403 Forbidden en /clientes")
    void deniegaElModuloACompras() throws Exception {
        mvc.perform(get("/clientes").with(user("compras1").roles("COMPRAS")))
                .andExpect(status().isForbidden());

        verify(servicio, never()).listar(any());
    }

    @Test
    @DisplayName("Escenario 3: Usuario con rol ALMACENERO recibe 403 Forbidden en /clientes")
    void deniegaElModuloAAlmacenero() throws Exception {
        mvc.perform(get("/clientes").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isForbidden());

        verify(servicio, never()).listar(any());
    }

    // =========================================================================
    // SECCIÓN B: CONSULTAS Y FORMULARIOS (GET)
    // =========================================================================

    @Test
    @DisplayName("Escenario 4: Listado de clientes por criterio con VENDEDOR y enlace al formulario")
    void listaClientesPorCriterioYEnlazaAlFormulario() throws Exception {
        when(servicio.listar("Ana")).thenReturn(List.of(
                new Cliente(8, "DNI", "12345678", "Ana Torres", "987 654 321", "ana@example.com")));

        mvc.perform(get("/clientes")
                        .param("buscar", " Ana ")
                        .with(user("vendedor1").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("clientes/listado"))
                .andExpect(model().attributeExists("clientes", "buscar"))
                .andExpect(content().string(containsString("Ana Torres")))
                .andExpect(content().string(containsString("DNI")))
                .andExpect(content().string(containsString("Nuevo cliente")));

        verify(servicio).listar("Ana");
    }

    @Test
    @DisplayName("Escenario 5: Formulario de creación nuevo accesible por VENDEDOR con DTO inicializado")
    void nuevo_conVendedor_renderizaFormularioVacio() throws Exception {
        mvc.perform(get("/clientes/nuevo").with(user("vendedor1").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("clientes/formulario"))
                .andExpect(model().attributeExists("formulario"))
                .andExpect(model().attribute("idCliente", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("Escenario 6: Formulario de edición con cliente existente carga datos correctamente")
    void editaClienteConSusDatosActuales() throws Exception {
        when(servicio.buscarPorId(8)).thenReturn(Optional.of(
                new Cliente(8, "RUC", "20123456789", "Botica Cliente S.A.C.", "987111222", "contacto@example.com")));

        mvc.perform(get("/clientes/8/editar").with(user("admin1").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("clientes/formulario"))
                .andExpect(model().attribute("idCliente", 8))
                .andExpect(content().string(containsString("Editar cliente")))
                .andExpect(content().string(containsString("value=\"20123456789\"")))
                .andExpect(content().string(containsString("Botica Cliente S.A.C.")));

        verify(servicio).buscarPorId(8);
    }

    @Test
    @DisplayName("Escenario 7: Edición de cliente inexistente retorna 404 Not Found")
    void editar_conClienteInexistente_retorna404NotFound() throws Exception {
        when(servicio.buscarPorId(999)).thenReturn(Optional.empty());

        mvc.perform(get("/clientes/999/editar").with(user("admin1").roles("ADMINISTRADOR")))
                .andExpect(status().isNotFound());

        verify(servicio).buscarPorId(999);
    }

    // =========================================================================
    // SECCIÓN C: OPERACIONES POST (ALTA Y EDICIÓN), CSRF Y FLASH ATTRIBUTES
    // =========================================================================

    @Test
    @DisplayName("Escenario 8: POST /clientes sin CSRF es rechazado con 403 Forbidden")
    void guardar_sinCsrf_retorna403Forbidden() throws Exception {
        mvc.perform(post("/clientes")
                        .with(user("vendedor1").roles("VENDEDOR"))
                        .param("tipoDocumento", "DNI")
                        .param("numeroDocumento", "12345678")
                        .param("nombreCompleto", "Carlos López"))
                .andExpect(status().isForbidden());

        verify(servicio, never()).guardar(any());
    }

    @Test
    @DisplayName("Escenario 9: POST /clientes con validación fallida recarga formulario con errores")
    void guardar_conErroresValidacion_retornaFormularioConErrores() throws Exception {
        mvc.perform(post("/clientes")
                        .with(user("vendedor1").roles("VENDEDOR"))
                        .with(csrf())
                        .param("tipoDocumento", "")
                        .param("numeroDocumento", "")
                        .param("nombreCompleto", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("clientes/formulario"))
                .andExpect(model().hasErrors())
                .andExpect(model().attribute("idCliente", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attributeHasFieldErrors("formulario", "tipoDocumento", "numeroDocumento", "nombreCompleto"));

        verify(servicio, never()).guardar(any());
    }

    @Test
    @DisplayName("Escenario 10: POST /clientes con documento duplicado o regla inválida recarga con error cliente.invalido")
    void guardar_conDocumentoDuplicado_retornaFormularioConError() throws Exception {
        when(servicio.guardar(any()))
                .thenThrow(new DuplicateKeyException("Ya existe un cliente con este documento"));

        mvc.perform(post("/clientes")
                        .with(user("vendedor1").roles("VENDEDOR"))
                        .with(csrf())
                        .param("tipoDocumento", "DNI")
                        .param("numeroDocumento", "12345678")
                        .param("nombreCompleto", "Carlos López")
                        .param("telefono", "999888777")
                        .param("correo", "carlos@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("clientes/formulario"))
                .andExpect(model().hasErrors())
                .andExpect(model().attribute("idCliente", org.hamcrest.Matchers.nullValue()));

        verify(servicio).guardar(any());
    }

    @Test
    @DisplayName("Escenario 11: POST /clientes válido guarda, redirige a /clientes y emite FlashAttribute")
    void guardar_valido_redirigeYEmiteFlashAttribute() throws Exception {
        when(servicio.guardar(any())).thenReturn(10);

        mvc.perform(post("/clientes")
                        .with(user("vendedor1").roles("VENDEDOR"))
                        .with(csrf())
                        .param("tipoDocumento", "DNI")
                        .param("numeroDocumento", "87654321")
                        .param("nombreCompleto", "María Gonzales")
                        .param("telefono", "987654321")
                        .param("correo", "maria@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clientes"))
                .andExpect(flash().attribute("mensaje", "Cliente registrado correctamente."));

        verify(servicio).guardar(any(ClienteFormulario.class));
    }

    @Test
    @DisplayName("Escenario 12: POST /clientes/{id}/editar sin CSRF es rechazado con 403 Forbidden")
    void actualizar_sinCsrf_retorna403Forbidden() throws Exception {
        mvc.perform(post("/clientes/8/editar")
                        .with(user("admin1").roles("ADMINISTRADOR"))
                        .param("tipoDocumento", "RUC")
                        .param("numeroDocumento", "20123456789")
                        .param("nombreCompleto", "Botica Actualizada S.A.C."))
                .andExpect(status().isForbidden());

        verify(servicio, never()).actualizar(any(), any());
    }

    @Test
    @DisplayName("Escenario 13: POST /clientes/{id}/editar con validación fallida recarga formulario con errores e idCliente")
    void actualizar_conErroresValidacion_retornaFormularioConErrores() throws Exception {
        mvc.perform(post("/clientes/8/editar")
                        .with(user("admin1").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .param("tipoDocumento", "INVALIDO")
                        .param("numeroDocumento", "123")
                        .param("nombreCompleto", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("clientes/formulario"))
                .andExpect(model().hasErrors())
                .andExpect(model().attribute("idCliente", 8));

        verify(servicio, never()).actualizar(any(), any());
    }

    @Test
    @DisplayName("Escenario 14: POST /clientes/{id}/editar con cliente inexistente retorna 404 Not Found")
    void actualizar_conClienteInexistente_retorna404NotFound() throws Exception {
        when(servicio.actualizar(eq(999), any())).thenReturn(false);

        mvc.perform(post("/clientes/999/editar")
                        .with(user("admin1").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .param("tipoDocumento", "DNI")
                        .param("numeroDocumento", "11223344")
                        .param("nombreCompleto", "Cliente Fantasma")
                        .param("telefono", "")
                        .param("correo", ""))
                .andExpect(status().isNotFound());

        verify(servicio).actualizar(eq(999), any());
    }

    @Test
    @DisplayName("Escenario 15: POST /clientes/{id}/editar con conflicto de documento recarga formulario con error")
    void actualizar_conDocumentoDuplicado_retornaFormularioConError() throws Exception {
        when(servicio.actualizar(eq(8), any()))
                .thenThrow(new IllegalArgumentException("El número de documento ya pertenece a otro cliente"));

        mvc.perform(post("/clientes/8/editar")
                        .with(user("admin1").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .param("tipoDocumento", "DNI")
                        .param("numeroDocumento", "12345678")
                        .param("nombreCompleto", "Nombre Modificado"))
                .andExpect(status().isOk())
                .andExpect(view().name("clientes/formulario"))
                .andExpect(model().hasErrors())
                .andExpect(model().attribute("idCliente", 8));

        verify(servicio).actualizar(eq(8), any());
    }

    @Test
    @DisplayName("Escenario 16: POST /clientes/{id}/editar válido actualiza, redirige y emite FlashAttribute")
    void actualizar_valido_redirigeYEmiteFlashAttribute() throws Exception {
        when(servicio.actualizar(eq(8), any())).thenReturn(true);

        mvc.perform(post("/clientes/8/editar")
                        .with(user("admin1").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .param("tipoDocumento", "RUC")
                        .param("numeroDocumento", "20123456789")
                        .param("nombreCompleto", "Botica Cliente Renovada S.A.C.")
                        .param("telefono", "987654321")
                        .param("correo", "contacto@boticacliente.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clientes"))
                .andExpect(flash().attribute("mensaje", "Cambios del cliente guardados correctamente."));

        verify(servicio).actualizar(eq(8), any(ClienteFormulario.class));
    }
}
