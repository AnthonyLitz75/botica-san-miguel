package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.dto.ProveedorFormulario;
import com.farmacia.model.Proveedor;
import com.farmacia.service.ProveedorServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

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

@WebMvcTest(ProveedorControlador.class)
@Import(SeguridadConfiguracion.class)
class ProveedorControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProveedorServicio servicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    private Proveedor crearProveedorPrueba(Integer id, String ruc, String razonSocial) {
        return new Proveedor(
                id,
                ruc,
                razonSocial,
                "014567890",
                "contacto@proveedor.pe",
                "Av. Los Sauces 123, Lima",
                true);
    }

    // ==========================================
    // GET /proveedores (Listado y Asimetría RBAC)
    // ==========================================

    @Test
    @DisplayName("GET /proveedores sin autenticacion redirige a /login")
    void listar_sinAutenticacion_redirigeALogin() throws Exception {
        mvc.perform(get("/proveedores"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
    }

    @Test
    @DisplayName("GET /proveedores deniega acceso (403) a VENDEDOR")
    void listar_conVendedor_retorna403() throws Exception {
        mvc.perform(get("/proveedores").with(user("vendedor").roles("VENDEDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /proveedores con ALMACENERO permite acceso pero con puedeGestionarProveedores=false")
    void listar_conAlmacenero_permiteAccesoSinPermisoGestion() throws Exception {
        Proveedor prov = crearProveedorPrueba(1, "20100000001", "Drogueria Central SAC");
        when(servicio.buscarPorEstado("", true)).thenReturn(List.of(prov));

        mvc.perform(get("/proveedores").with(user("almacen").roles("ALMACENERO")))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores/listado"))
                .andExpect(model().attribute("proveedores", List.of(prov)))
                .andExpect(model().attribute("buscar", ""))
                .andExpect(model().attribute("activo", true))
                .andExpect(model().attribute("puedeGestionarProveedores", false));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ADMINISTRADOR", "COMPRAS" })
    @DisplayName("GET /proveedores con ADMINISTRADOR o COMPRAS permite acceso con puedeGestionarProveedores=true")
    void listar_conAdminOCompras_permiteAccesoConPermisoGestion(String rol) throws Exception {
        Proveedor prov = crearProveedorPrueba(1, "20100000001", "Drogueria Central SAC");
        when(servicio.buscarPorEstado("", true)).thenReturn(List.of(prov));

        mvc.perform(get("/proveedores").with(user("usuario").roles(rol)))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores/listado"))
                .andExpect(model().attribute("proveedores", List.of(prov)))
                .andExpect(model().attribute("puedeGestionarProveedores", true));
    }

    @Test
    @DisplayName("GET /proveedores propaga filtros de busqueda y estado al servicio")
    void listar_conParametros_propagaFiltrosAlServicio() throws Exception {
        when(servicio.buscarPorEstado("drogueria", false)).thenReturn(List.of());

        mvc.perform(get("/proveedores")
                .with(user("compras").roles("COMPRAS"))
                .param("buscar", "drogueria")
                .param("activo", "false"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores/listado"))
                .andExpect(model().attribute("buscar", "drogueria"))
                .andExpect(model().attribute("activo", false));

        verify(servicio).buscarPorEstado("drogueria", false);
    }

    // ==========================================
    // GET /proveedores/nuevo
    // ==========================================

    @ParameterizedTest
    @ValueSource(strings = { "ALMACENERO", "VENDEDOR" })
    @DisplayName("GET /proveedores/nuevo deniega acceso (403) a roles ALMACENERO y VENDEDOR")
    void nuevo_conRolesNoAutorizados_retorna403(String rol) throws Exception {
        mvc.perform(get("/proveedores/nuevo").with(user("usuario").roles(rol)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ADMINISTRADOR", "COMPRAS" })
    @DisplayName("GET /proveedores/nuevo permite acceso a ADMINISTRADOR y COMPRAS")
    void nuevo_conRolesAutorizados_retornaFormulario(String rol) throws Exception {
        mvc.perform(get("/proveedores/nuevo").with(user("usuario").roles(rol)))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores/formulario"))
                .andExpect(model().attributeExists("formulario"));
    }

    // ==========================================
    // POST /proveedores (Guardar nuevo)
    // ==========================================

    @Test
    @DisplayName("POST /proveedores sin CSRF es rechazado con 403 Forbidden")
    void guardar_sinCsrf_retorna403() throws Exception {
        mvc.perform(post("/proveedores")
                .with(user("compras").roles("COMPRAS"))
                .param("ruc", "20123456789")
                .param("razonSocial", "Drogueria Central SAC")
                .param("telefono", "014567890")
                .param("correo", "contacto@central.pe")
                .param("direccion", "Av. Principal 123"))
                .andExpect(status().isForbidden());

        verify(servicio, never()).guardar(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ALMACENERO", "VENDEDOR" })
    @DisplayName("POST /proveedores deniega acceso (403) a ALMACENERO y VENDEDOR")
    void guardar_conRolesNoAutorizados_retorna403(String rol) throws Exception {
        mvc.perform(post("/proveedores")
                .with(user("usuario").roles(rol))
                .with(csrf())
                .param("ruc", "20123456789")
                .param("razonSocial", "Drogueria Central SAC")
                .param("telefono", "014567890")
                .param("correo", "contacto@central.pe")
                .param("direccion", "Av. Principal 123"))
                .andExpect(status().isForbidden());

        verify(servicio, never()).guardar(any());
    }

    @Test
    @DisplayName("POST /proveedores con errores de validacion retorna formulario con errores")
    void guardar_conErroresValidacion_retornaFormulario() throws Exception {
        mvc.perform(post("/proveedores")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf())
                .param("ruc", "123") // RUC debe tener 11 dígitos
                .param("razonSocial", "")
                .param("correo", "correo-invalido"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores/formulario"))
                .andExpect(model().attributeHasFieldErrors("formulario", "ruc", "razonSocial", "correo"));

        verify(servicio, never()).guardar(any());
    }

    @Test
    @DisplayName("POST /proveedores con RUC duplicado rechaza el campo ruc y retorna formulario")
    void guardar_conRucDuplicado_rechazaCampoRuc() throws Exception {
        org.mockito.Mockito.doThrow(new DuplicateKeyException("RUC duplicado"))
                .when(servicio).guardar(any(ProveedorFormulario.class));

        mvc.perform(post("/proveedores")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf())
                .param("ruc", "20123456789")
                .param("razonSocial", "Drogueria Duplicada SAC")
                .param("telefono", "014567890")
                .param("correo", "contacto@duplicada.pe")
                .param("direccion", "Av. Principal 123"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores/formulario"))
                .andExpect(model().attributeHasFieldErrorCode("formulario", "ruc", "ruc.duplicado"));
    }

    @Test
    @DisplayName("POST /proveedores con datos validos persiste y redirige a listado")
    void guardar_datosValidos_guardaYRedirige() throws Exception {
        mvc.perform(post("/proveedores")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf())
                .param("ruc", "20123456789")
                .param("razonSocial", "Laboratorios Unidos SAC")
                .param("telefono", "987654321")
                .param("correo", "ventas@unidos.com")
                .param("direccion", "Av. Grau 456, Lima"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/proveedores"));

        verify(servicio).guardar(any(ProveedorFormulario.class));
    }

    // ==========================================
    // GET /proveedores/{id}/editar
    // ==========================================

    @Test
    @DisplayName("GET /proveedores/{id}/editar con id existente retorna formulario con idProveedor")
    void editar_conIdExistente_retornaFormulario() throws Exception {
        Proveedor prov = crearProveedorPrueba(1, "20100000001", "Drogueria Central SAC");
        when(servicio.buscarPorId(1)).thenReturn(Optional.of(prov));

        mvc.perform(get("/proveedores/1/editar").with(user("compras").roles("COMPRAS")))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores/formulario"))
                .andExpect(model().attributeExists("formulario"))
                .andExpect(model().attribute("idProveedor", 1));
    }

    @Test
    @DisplayName("GET /proveedores/{id}/editar con id inexistente retorna 404 Not Found")
    void editar_conIdInexistente_retorna404() throws Exception {
        when(servicio.buscarPorId(999)).thenReturn(Optional.empty());

        mvc.perform(get("/proveedores/999/editar").with(user("compras").roles("COMPRAS")))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ALMACENERO", "VENDEDOR" })
    @DisplayName("GET /proveedores/{id}/editar deniega acceso (403) a ALMACENERO y VENDEDOR")
    void editar_conRolesNoAutorizados_retorna403(String rol) throws Exception {
        mvc.perform(get("/proveedores/1/editar").with(user("usuario").roles(rol)))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // POST /proveedores/{id}/editar
    // ==========================================

    @Test
    @DisplayName("POST /proveedores/{id}/editar con id inexistente antes de validar retorna 404")
    void actualizar_conIdInexistente_retorna404() throws Exception {
        when(servicio.buscarPorId(999)).thenReturn(Optional.empty());

        mvc.perform(post("/proveedores/999/editar")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf())
                .param("ruc", "20123456789")
                .param("razonSocial", "Proveedor Test")
                .param("correo", "test@prov.pe"))
                .andExpect(status().isNotFound());

        verify(servicio, never()).actualizar(any(), any());
    }

    @Test
    @DisplayName("POST /proveedores/{id}/editar con errores de validacion retorna formulario con idProveedor")
    void actualizar_conErroresValidacion_retornaFormulario() throws Exception {
        Proveedor prov = crearProveedorPrueba(1, "20100000001", "Drogueria Central SAC");
        when(servicio.buscarPorId(1)).thenReturn(Optional.of(prov));

        mvc.perform(post("/proveedores/1/editar")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf())
                .param("ruc", "123")
                .param("razonSocial", "")
                .param("correo", "correo-malo"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores/formulario"))
                .andExpect(model().attribute("idProveedor", 1))
                .andExpect(model().attributeHasFieldErrors("formulario", "ruc", "razonSocial", "correo"));

        verify(servicio, never()).actualizar(any(), any());
    }

    @Test
    @DisplayName("POST /proveedores/{id}/editar cuando servicio retorna false lanza 404 Not Found")
    void actualizar_cuandoServicioRetornaFalse_lanza404() throws Exception {
        Proveedor prov = crearProveedorPrueba(1, "20100000001", "Drogueria Central SAC");
        when(servicio.buscarPorId(1)).thenReturn(Optional.of(prov));
        when(servicio.actualizar(eq(1), any(ProveedorFormulario.class))).thenReturn(false);

        mvc.perform(post("/proveedores/1/editar")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf())
                .param("ruc", "20123456789")
                .param("razonSocial", "Drogueria Central Modificada")
                .param("telefono", "014567890")
                .param("correo", "contacto@central.pe")
                .param("direccion", "Av. Principal 123"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /proveedores/{id}/editar con RUC duplicado rechaza el campo ruc")
    void actualizar_conRucDuplicado_rechazaCampoRuc() throws Exception {
        Proveedor prov = crearProveedorPrueba(1, "20100000001", "Drogueria Central SAC");
        when(servicio.buscarPorId(1)).thenReturn(Optional.of(prov));
        when(servicio.actualizar(eq(1), any(ProveedorFormulario.class)))
                .thenThrow(new DuplicateKeyException("RUC duplicado"));

        mvc.perform(post("/proveedores/1/editar")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf())
                .param("ruc", "20999999999")
                .param("razonSocial", "Drogueria Central Modificada")
                .param("telefono", "014567890")
                .param("correo", "contacto@central.pe")
                .param("direccion", "Av. Principal 123"))
                .andExpect(status().isOk())
                .andExpect(view().name("proveedores/formulario"))
                .andExpect(model().attribute("idProveedor", 1))
                .andExpect(model().attributeHasFieldErrorCode("formulario", "ruc", "ruc.duplicado"));
    }

    @Test
    @DisplayName("POST /proveedores/{id}/editar con datos validos actualiza y redirige")
    void actualizar_datosValidos_actualizaYRedirige() throws Exception {
        Proveedor prov = crearProveedorPrueba(1, "20100000001", "Drogueria Central SAC");
        when(servicio.buscarPorId(1)).thenReturn(Optional.of(prov));
        when(servicio.actualizar(eq(1), any(ProveedorFormulario.class))).thenReturn(true);

        mvc.perform(post("/proveedores/1/editar")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf())
                .param("ruc", "20100000001")
                .param("razonSocial", "Drogueria Central SAC Actualizada")
                .param("telefono", "014567899")
                .param("correo", "info@central.pe")
                .param("direccion", "Av. Principal 456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/proveedores"));

        verify(servicio).actualizar(eq(1), any(ProveedorFormulario.class));
    }

    // ==========================================
    // POST /proveedores/{id}/desactivar y activar
    // ==========================================

    @Test
    @DisplayName("POST /proveedores/{id}/desactivar con roles no autorizados retorna 403 Forbidden")
    void desactivar_conRolNoAutorizado_retorna403() throws Exception {
        mvc.perform(post("/proveedores/1/desactivar")
                .with(user("almacen").roles("ALMACENERO"))
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /proveedores/{id}/desactivar exitoso redirige a listado")
    void desactivar_exitoso_redirigeAListado() throws Exception {
        when(servicio.desactivar(1)).thenReturn(true);

        mvc.perform(post("/proveedores/1/desactivar")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/proveedores"));

        verify(servicio).desactivar(1);
    }

    @Test
    @DisplayName("POST /proveedores/{id}/desactivar inexistente lanza 404 Not Found")
    void desactivar_inexistente_retorna404() throws Exception {
        when(servicio.desactivar(999)).thenReturn(false);

        mvc.perform(post("/proveedores/999/desactivar")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /proveedores/{id}/activar exitoso redirige a listado")
    void activar_exitoso_redirigeAListado() throws Exception {
        when(servicio.activar(1)).thenReturn(true);

        mvc.perform(post("/proveedores/1/activar")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/proveedores"));

        verify(servicio).activar(1);
    }

    @Test
    @DisplayName("POST /proveedores/{id}/activar inexistente lanza 404 Not Found")
    void activar_inexistente_retorna404() throws Exception {
        when(servicio.activar(999)).thenReturn(false);

        mvc.perform(post("/proveedores/999/activar")
                .with(user("compras").roles("COMPRAS"))
                .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
