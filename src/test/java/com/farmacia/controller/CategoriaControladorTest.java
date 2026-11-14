package com.farmacia.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.estructuras.ArbolCategorias;
import com.farmacia.model.Categoria;
import com.farmacia.service.CategoriaServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoriaControlador.class)
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class CategoriaControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CategoriaServicio categoriaServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarioDetallesServicio;

    @MockitoBean
    private com.farmacia.service.AuditoriaServicio auditoriaServicio;

    @Test
    @DisplayName("GET /categorias muestra el árbol con atributos del modelo")
    void getCategoriasMuestraArbol() throws Exception {
        ArbolCategorias arbol = new ArbolCategorias();
        arbol.insertar(1, "Analgésicos", null);
        when(categoriaServicio.arbol()).thenReturn(arbol);
        when(categoriaServicio.listarActivas()).thenReturn(List.of(new Categoria(1, "Analgésicos", null, true)));

        mvc.perform(get("/categorias").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("categorias/arbol"))
                .andExpect(model().attributeExists("arbol", "categorias", "totalCategorias", "raices", "stockPorRama", "alertasPorRama"));
    }

    @Test
    @DisplayName("GET /categorias/nueva muestra formulario de creación")
    void getNuevaCategoriaMuestraFormulario() throws Exception {
        when(categoriaServicio.listarActivas()).thenReturn(List.of());

        mvc.perform(get("/categorias/nueva").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("categorias/formulario"))
                .andExpect(model().attributeExists("formulario", "padresDisponibles", "esEdicion"));
    }

    @Test
    @DisplayName("POST /categorias/nueva con datos válidos registra y redirige")
    void postNuevaCategoriaValidaRedirige() throws Exception {
        when(categoriaServicio.crear(anyString(), any())).thenReturn(10);

        mvc.perform(post("/categorias/nueva")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .param("nombre", "Antibióticos")
                        .param("idPadre", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categorias"))
                .andExpect(flash().attributeExists("mensajeExito"));

        verify(categoriaServicio).crear(eq("Antibióticos"), any());
    }

    @Test
    @DisplayName("POST /categorias/nueva con nombre vacío retorna formulario con errores")
    void postNuevaCategoriaVaciaRetornaErrores() throws Exception {
        mvc.perform(post("/categorias/nueva")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .param("nombre", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("categorias/formulario"))
                .andExpect(model().hasErrors());

        verify(categoriaServicio, never()).crear(anyString(), any());
    }

    @Test
    @DisplayName("GET /categorias/{id}/editar carga datos y excluye descendientes")
    void getEditarCargaDatos() throws Exception {
        Categoria c1 = new Categoria(1, "Analgésicos", null, true);
        Categoria c2 = new Categoria(2, "AINEs", 1, true);

        ArbolCategorias arbol = new ArbolCategorias();
        arbol.insertar(1, "Analgésicos", null);
        arbol.insertar(2, "AINEs", 1);

        when(categoriaServicio.buscarPorId(1)).thenReturn(Optional.of(c1));
        when(categoriaServicio.arbol()).thenReturn(arbol);
        when(categoriaServicio.listarActivas()).thenReturn(List.of(c1, c2));

        mvc.perform(get("/categorias/1/editar").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("categorias/formulario"))
                .andExpect(model().attribute("categoriaId", 1))
                .andExpect(model().attribute("esEdicion", true));
    }

    @Test
    @DisplayName("POST /categorias/{id}/desactivar desactiva y redirige")
    void postDesactivarRedirige() throws Exception {
        mvc.perform(post("/categorias/1/desactivar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categorias"))
                .andExpect(flash().attributeExists("mensajeExito"));

        verify(categoriaServicio).desactivar(1);
    }
}
