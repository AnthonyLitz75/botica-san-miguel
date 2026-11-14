package com.farmacia.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.dto.UsuarioFormulario;
import com.farmacia.model.Usuario;
import com.farmacia.service.UsuarioDetallesServicio;
import com.farmacia.service.UsuarioServicio;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UsuarioControlador.class)
@Import(SeguridadConfiguracion.class)
class UsuarioControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UsuarioServicio servicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    // -------------------------------------------------------------------------
    // CA-4.1: Seguridad y RBAC
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Petición anónima a /usuarios redirige a /login (302)")
    void listar_anonimo_redirigeALogin() throws Exception {
        mvc.perform(get("/usuarios"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @DisplayName("CA-4.1: VENDEDOR, ALMACENERO y COMPRAS reciben 403 Forbidden en /usuarios")
    void listar_rolesNoAutorizados_retorna403() throws Exception {
        mvc.perform(get("/usuarios").with(user("vend").roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/usuarios").with(user("almacen").roles("ALMACENERO")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/usuarios").with(user("compra").roles("COMPRAS")))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // CA-4.2: Listado sin hashes ni claves en el HTML
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CA-4.2: GET /usuarios como ADMINISTRADOR renderiza 200 y NO expone ningún hash en el HTML")
    void listar_administrador_renderizaSinHashEnHtml() throws Exception {
        var u1 = new Usuario(1, "Administrador Uno", "admin1", "{bcrypt}$2a$10$HASH_SECRETO_INEXISTENTE", "ADMINISTRADOR", true);
        var u2 = new Usuario(2, "Vendedor Dos", "vendedor2", "{bcrypt}$2a$10$OTRO_HASH_CONFIDENCIAL", "VENDEDOR", true);

        when(servicio.listar("admin")).thenReturn(List.of(u1, u2));

        mvc.perform(get("/usuarios").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/listado"))
                .andExpect(model().attributeExists("usuarios"))
                .andExpect(content().string(containsString("Administrador Uno")))
                .andExpect(content().string(containsString("admin1")))
                .andExpect(content().string(containsString("Vendedor Dos")))
                // Regla estricta CA-4.2: el hash jamás debe aparecer en el HTML renderizado
                .andExpect(content().string(not(containsString("{bcrypt}"))))
                .andExpect(content().string(not(containsString("HASH_SECRETO"))))
                .andExpect(content().string(not(containsString("OTRO_HASH"))));
    }

    // -------------------------------------------------------------------------
    // Crear usuario
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /usuarios/nuevo como ADMINISTRADOR retorna formulario (200)")
    void nuevo_administrador_retornaFormulario() throws Exception {
        mvc.perform(get("/usuarios/nuevo").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/formulario"))
                .andExpect(model().attributeExists("formulario", "roles"));
    }

    @Test
    @DisplayName("POST /usuarios válido como ADMINISTRADOR crea usuario y redirige")
    void guardar_valido_redirigeAListado() throws Exception {
        when(servicio.crear(eq("admin"), any())).thenReturn(10);

        mvc.perform(post("/usuarios")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .param("nombre", "Nuevo Empleado")
                        .param("nombreUsuario", "nempleado")
                        .param("rol", "VENDEDOR")
                        .param("clave", "ClaveSegura2026!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios"));

        verify(servicio).crear(eq("admin"), any());
    }

    @Test
    @DisplayName("POST /usuarios con errores de validación recarga formulario con errores")
    void guardar_invalido_recargaFormulario() throws Exception {
        mvc.perform(post("/usuarios")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .param("nombre", "")
                        .param("nombreUsuario", "abc") // demasiado corto
                        .param("rol", "VENDEDOR")
                        .param("clave", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/formulario"))
                .andExpect(model().hasErrors());
    }

    // -------------------------------------------------------------------------
    // Editar usuario
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /usuarios/{id}/editar con usuario existente retorna formulario")
    void editar_existente_retornaFormulario() throws Exception {
        var u = new Usuario(5, "Carlos Lopez", "clopez", "h", "ALMACENERO", true);
        when(servicio.buscarPorId(5, "admin")).thenReturn(Optional.of(u));

        mvc.perform(get("/usuarios/5/editar").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/formulario"))
                .andExpect(model().attributeExists("formulario", "idUsuario", "roles"));
    }

    @Test
    @DisplayName("POST /usuarios/{id}/editar válido actualiza y redirige")
    void actualizar_valido_redirige() throws Exception {
        mvc.perform(post("/usuarios/5/editar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .param("nombre", "Carlos Modificado")
                        .param("nombreUsuario", "clopez")
                        .param("rol", "COMPRAS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios"));

        verify(servicio).actualizar(eq("admin"), eq(5), any(UsuarioFormulario.class));
    }

    // -------------------------------------------------------------------------
    // Restablecer clave, desactivar y activar
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /usuarios/{id}/clave válido llama a cambiarClave y redirige")
    void cambiarClave_valida_redirige() throws Exception {
        mvc.perform(post("/usuarios/5/clave")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .param("nuevaClave", "NuevaClaveSuper2026!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios"));

        verify(servicio).cambiarClave(eq("admin"), eq(5), any());
    }

    @Test
    @DisplayName("POST /usuarios/{id}/desactivar llama a desactivar y redirige")
    void desactivar_llamaServicioYRedirige() throws Exception {
        mvc.perform(post("/usuarios/5/desactivar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios"));

        verify(servicio).desactivar("admin", 5);
    }

    @Test
    @DisplayName("POST /usuarios/{id}/activar llama a activar y redirige")
    void activar_llamaServicioYRedirige() throws Exception {
        mvc.perform(post("/usuarios/5/activar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios"));

        verify(servicio).activar("admin", 5);
    }
}
