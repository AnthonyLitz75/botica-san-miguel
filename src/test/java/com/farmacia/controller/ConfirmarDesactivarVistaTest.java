package com.farmacia.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.estructuras.ArbolCategorias;
import com.farmacia.model.Categoria;
import com.farmacia.model.Medicamento;
import com.farmacia.model.Proveedor;
import com.farmacia.model.UsuarioListadoVista;
import com.farmacia.service.AuditoriaServicio;
import com.farmacia.service.CategoriaServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.ProveedorServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import com.farmacia.service.UsuarioServicio;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
        MedicamentoControlador.class,
        CategoriaControlador.class,
        ProveedorControlador.class,
        UsuarioControlador.class
})
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class ConfirmarDesactivarVistaTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MedicamentoServicio medicamentoServicio;

    @MockitoBean
    private CategoriaServicio categoriaServicio;

    @MockitoBean
    private ProveedorServicio proveedorServicio;

    @MockitoBean
    private UsuarioServicio usuarioServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarioDetallesServicio;

    @MockitoBean
    private AuditoriaServicio auditoriaServicio;

    @Test
    @DisplayName("Medicamentos: renderiza modal #modal-confirmar y atributos data-confirmar-* sin confirm() nativo")
    void medicamentos_renderizaModalYAtributosConfirmarDesactivar() throws Exception {
        Medicamento med = new Medicamento(
                10,
                "MED-010",
                "Amoxicilina 500mg",
                "500mg",
                "Caja x 30",
                "CAJA",
                new BigDecimal("15.50"),
                10,
                true);

        when(medicamentoServicio.buscarPorEstado(any(), anyBoolean())).thenReturn(List.of(med));
        when(categoriaServicio.listarActivas()).thenReturn(List.of());

        mvc.perform(get("/medicamentos").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"modal-confirmar\"")))
                .andExpect(content().string(containsString("data-confirmar-titulo=\"¿Desactivar Amoxicilina 500mg?\"")))
                .andExpect(content().string(containsString("data-confirmar-mensaje=\"Dejará de aparecer en Caja y en nuevas órdenes de compra. Se conserva su historial y sus lotes.\"")))
                .andExpect(content().string(containsString("data-confirmar-boton=\"Desactivar\"")))
                .andExpect(content().string(not(containsString("onsubmit=\"return confirm"))))
                .andExpect(content().string(not(containsString("¿Desactivar este medicamento?"))));
    }

    @Test
    @DisplayName("Categorías: renderiza modal #modal-confirmar y atributos data-confirmar-* sin confirm() nativo")
    void categorias_renderizaModalYAtributosConfirmarDesactivar() throws Exception {
        Categoria cat = new Categoria(5, "Antibióticos", null, true);
        ArbolCategorias arbol = new ArbolCategorias();
        arbol.insertar(5, "Antibióticos", null);

        when(categoriaServicio.arbol()).thenReturn(arbol);
        when(categoriaServicio.listarActivas()).thenReturn(List.of(cat));
        when(categoriaServicio.stockPorRama(anyInt())).thenReturn(0);
        when(categoriaServicio.alertasPorRama(anyInt())).thenReturn(0);

        mvc.perform(get("/categorias").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"modal-confirmar\"")))
                .andExpect(content().string(containsString("data-confirmar-titulo=\"¿Desactivar Antibióticos?\"")))
                .andExpect(content().string(containsString("data-confirmar-mensaje=\"Dejará de aparecer en la estructura y en nuevos medicamentos. Solo se puede desactivar si no tiene subcategorías ni medicamentos asociados.\"")))
                .andExpect(content().string(containsString("data-confirmar-boton=\"Desactivar\"")))
                .andExpect(content().string(not(containsString("onsubmit=\"return confirm"))))
                .andExpect(content().string(not(containsString("¿Está seguro de desactivar esta categoría?"))));
    }

    @Test
    @DisplayName("Proveedores: renderiza modal #modal-confirmar y atributos data-confirmar-* sin confirm() nativo")
    void proveedores_renderizaModalYAtributosConfirmarDesactivar() throws Exception {
        Proveedor prov = new Proveedor(
                3,
                "20123456789",
                "Distribuidora Lima S.A.C.",
                "contacto@lima.pe",
                "987654321",
                "Av. Central 123",
                true);

        when(proveedorServicio.buscarPorEstado(anyString(), anyBoolean())).thenReturn(List.of(prov));

        mvc.perform(get("/proveedores").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"modal-confirmar\"")))
                .andExpect(content().string(containsString("data-confirmar-titulo=\"¿Desactivar Distribuidora Lima S.A.C.?\"")))
                .andExpect(content().string(containsString("data-confirmar-mensaje=\"No podrá usarse en nuevas órdenes de compra. Se conserva su historial.\"")))
                .andExpect(content().string(containsString("data-confirmar-boton=\"Desactivar\"")))
                .andExpect(content().string(not(containsString("onsubmit=\"return confirm"))))
                .andExpect(content().string(not(containsString("¿Desactivar este proveedor?"))));
    }

    @Test
    @DisplayName("Usuarios: renderiza modal #modal-confirmar y atributos data-confirmar-* sin confirm() nativo")
    void usuarios_renderizaModalYAtributosConfirmarDesactivar() throws Exception {
        UsuarioListadoVista usuarioPrueba = new UsuarioListadoVista(
                2,
                "Carlos Gómez",
                "cgomez",
                "VENDEDOR",
                true,
                LocalDateTime.now());

        when(usuarioServicio.listarParaListado(eq("admin"), anyInt()))
                .thenReturn(new UsuarioServicio.UsuariosPaginados(List.of(usuarioPrueba), 1, 1, 1));
        when(usuarioServicio.listarParaListado("admin")).thenReturn(List.of(usuarioPrueba));

        mvc.perform(get("/usuarios").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"modal-confirmar\"")))
                .andExpect(content().string(containsString("data-confirmar-titulo=\"¿Desactivar a Carlos Gómez?\"")))
                .andExpect(content().string(containsString("data-confirmar-mensaje=\"No podrá iniciar sesión. Sus registros se conservan.\"")))
                .andExpect(content().string(containsString("data-confirmar-boton=\"Desactivar\"")))
                .andExpect(content().string(not(containsString("onsubmit=\"return confirm"))))
                .andExpect(content().string(not(containsString("¿Desactivar este usuario?"))));
    }
}
