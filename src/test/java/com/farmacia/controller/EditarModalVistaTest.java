package com.farmacia.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.dto.ClienteFormulario;
import com.farmacia.dto.MedicamentoFormulario;
import com.farmacia.dto.ProveedorFormulario;
import com.farmacia.dto.UsuarioFormulario;
import com.farmacia.estructuras.ArbolCategorias;
import com.farmacia.model.Categoria;
import com.farmacia.model.ClienteResumenVista;
import com.farmacia.model.Medicamento;
import com.farmacia.model.Proveedor;
import com.farmacia.model.UsuarioListadoVista;
import com.farmacia.service.AuditoriaServicio;
import com.farmacia.service.CategoriaServicio;
import com.farmacia.service.ClienteServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.ProveedorServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import com.farmacia.service.UsuarioServicio;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
        MedicamentoControlador.class,
        ClienteWebControlador.class,
        ProveedorControlador.class,
        UsuarioControlador.class,
        CategoriaControlador.class
})
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class EditarModalVistaTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MedicamentoServicio medicamentoServicio;

    @MockitoBean
    private ClienteServicio clienteServicio;

    @MockitoBean
    private ProveedorServicio proveedorServicio;

    @MockitoBean
    private UsuarioServicio usuarioServicio;

    @MockitoBean
    private CategoriaServicio categoriaServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarioDetallesServicio;

    @MockitoBean
    private AuditoriaServicio auditoriaServicio;

    @Test
    @DisplayName("Medicamentos: listado renderiza atributos de modal de edición en el botón lápiz")
    void medicamentos_renderizaAtributosEdicionModal() throws Exception {
        Medicamento med = new Medicamento(
                1, "MED-001", "Paracetamol", "500 mg", "Caja x 100",
                "CAJA", new BigDecimal("15.50"), 10, true, 50, 1);
        when(medicamentoServicio.buscarPorEstado(anyString(), anyBoolean())).thenReturn(List.of(med));
        when(categoriaServicio.listarActivas()).thenReturn(List.of());

        mvc.perform(get("/medicamentos").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modal-open=\"modal-nuevo-medicamento\"")))
                .andExpect(content().string(containsString("data-modo=\"editar\"")))
                .andExpect(content().string(containsString("data-action=\"/medicamentos/1/editar\"")))
                .andExpect(content().string(containsString("data-titulo=\"Editar Paracetamol\"")))
                .andExpect(content().string(containsString("data-codigo=\"MED-001\"")));
    }

    @Test
    @DisplayName("Medicamentos: POST /medicamentos/1/editar responde JSON 400 ante errores con AJAX")
    void medicamentos_actualizarAjax_erroresValidacion() throws Exception {
        Medicamento med = new Medicamento(
                1, "MED-001", "Paracetamol", "500 mg", "Caja x 100",
                "CAJA", new BigDecimal("15.50"), 10, true, 50, 1);
        when(medicamentoServicio.buscarPorId(1)).thenReturn(Optional.of(med));

        mvc.perform(post("/medicamentos/1/editar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("codigo", "")
                        .param("nombre", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").exists());
    }

    @Test
    @DisplayName("Medicamentos: POST /medicamentos/1/editar responde JSON 200 ante éxito con AJAX")
    void medicamentos_actualizarAjax_exito() throws Exception {
        Medicamento med = new Medicamento(
                1, "MED-001", "Paracetamol", "500 mg", "Caja x 100",
                "CAJA", new BigDecimal("15.50"), 10, true, 50, 1);
        when(medicamentoServicio.buscarPorId(1)).thenReturn(Optional.of(med));
        when(medicamentoServicio.actualizar(eq(1), any(MedicamentoFormulario.class))).thenReturn(true);

        mvc.perform(post("/medicamentos/1/editar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("codigo", "MED-001")
                        .param("nombre", "Paracetamol Forte")
                        .param("concentracion", "1 g")
                        .param("presentacion", "Caja x 50")
                        .param("unidadControl", "CAJA")
                        .param("precioVenta", "20.00")
                        .param("stockMinimo", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.url").value("/medicamentos"));
    }

    @Test
    @DisplayName("Clientes: listado renderiza atributos de modal de edición en el botón lápiz")
    void clientes_renderizaAtributosEdicionModal() throws Exception {
        ClienteResumenVista cli = new ClienteResumenVista(
                10, "DNI", "12345678", "Juan Pérez", "987654321", "juan@test.com", 2);
        when(clienteServicio.listar(any())).thenReturn(List.of(cli));

        mvc.perform(get("/clientes").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modal-open=\"modal-nuevo-cliente\"")))
                .andExpect(content().string(containsString("data-modo=\"editar\"")))
                .andExpect(content().string(containsString("data-action=\"/clientes/10/editar\"")))
                .andExpect(content().string(containsString("data-titulo=\"Editar Juan Pérez\"")))
                .andExpect(content().string(containsString("data-numero-documento=\"12345678\"")));
    }

    @Test
    @DisplayName("Clientes: POST /clientes/10/editar responde JSON 400 ante errores con AJAX")
    void clientes_actualizarAjax_erroresValidacion() throws Exception {
        mvc.perform(post("/clientes/10/editar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("tipoDocumento", "DNI")
                        .param("numeroDocumento", "")
                        .param("nombreCompleto", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").exists());
    }

    @Test
    @DisplayName("Clientes: POST /clientes/10/editar responde JSON 200 ante éxito con AJAX")
    void clientes_actualizarAjax_exito() throws Exception {
        when(clienteServicio.actualizar(eq(10), any(ClienteFormulario.class))).thenReturn(true);

        mvc.perform(post("/clientes/10/editar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("tipoDocumento", "DNI")
                        .param("numeroDocumento", "12345678")
                        .param("nombreCompleto", "Juan Pérez Editado")
                        .param("telefono", "987654321")
                        .param("correo", "juan.editado@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.url").value("/clientes"));
    }

    @Test
    @DisplayName("Proveedores: listado renderiza atributos de modal de edición en el botón lápiz")
    void proveedores_renderizaAtributosEdicionModal() throws Exception {
        Proveedor prov = new Proveedor(
                5, "20100070970", "Laboratorios Rowe", "014567890", "rowe@farmacia.pe", "Av. Lima 123", true);
        when(proveedorServicio.buscarPorEstado(anyString(), anyBoolean())).thenReturn(List.of(prov));

        mvc.perform(get("/proveedores").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modal-open=\"modal-nuevo-proveedor\"")))
                .andExpect(content().string(containsString("data-modo=\"editar\"")))
                .andExpect(content().string(containsString("data-action=\"/proveedores/5/editar\"")))
                .andExpect(content().string(containsString("data-titulo=\"Editar Laboratorios Rowe\"")))
                .andExpect(content().string(containsString("data-ruc=\"20100070970\"")));
    }

    @Test
    @DisplayName("Proveedores: POST /proveedores/5/editar responde JSON 400 ante errores con AJAX")
    void proveedores_actualizarAjax_erroresValidacion() throws Exception {
        when(proveedorServicio.buscarPorId(5)).thenReturn(Optional.of(
                new Proveedor(5, "20100070970", "Rowe", null, null, null, true)));

        mvc.perform(post("/proveedores/5/editar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("ruc", "")
                        .param("razonSocial", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").exists());
    }

    @Test
    @DisplayName("Proveedores: POST /proveedores/5/editar responde JSON 200 ante éxito con AJAX")
    void proveedores_actualizarAjax_exito() throws Exception {
        when(proveedorServicio.buscarPorId(5)).thenReturn(Optional.of(
                new Proveedor(5, "20100070970", "Rowe", null, null, null, true)));
        when(proveedorServicio.actualizar(eq(5), any(ProveedorFormulario.class))).thenReturn(true);

        mvc.perform(post("/proveedores/5/editar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("ruc", "20100070970")
                        .param("razonSocial", "Laboratorios Rowe S.A.")
                        .param("telefono", "014567890")
                        .param("correo", "ventas@rowe.pe")
                        .param("direccion", "Av. Lima 123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.url").value("/proveedores"));
    }

    @Test
    @DisplayName("Usuarios: listado renderiza atributos de modal de edición en el botón lápiz con 'Editar a <nombre>'")
    void usuarios_renderizaAtributosEdicionModal() throws Exception {
        UsuarioListadoVista vista = new UsuarioListadoVista(
                2, "Carlos Ramos", "cramos", "VENDEDOR", true, LocalDateTime.now());
        when(usuarioServicio.listarParaListado(anyString(), anyInt())).thenReturn(
                new UsuarioServicio.UsuariosPaginados(List.of(vista), 1, 1, 1));

        mvc.perform(get("/usuarios").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modal-open=\"modal-nuevo-usuario\"")))
                .andExpect(content().string(containsString("data-modo=\"editar\"")))
                .andExpect(content().string(containsString("data-action=\"/usuarios/2/editar\"")))
                .andExpect(content().string(containsString("data-titulo=\"Editar a Carlos Ramos\"")))
                .andExpect(content().string(containsString("data-usuario=\"cramos\"")))
                .andExpect(content().string(containsString("id=\"modal-contenedor-clave\"")));
    }

    @Test
    @DisplayName("Usuarios: POST /usuarios/2/editar responde JSON 200 ante éxito con AJAX")
    void usuarios_actualizarAjax_exito() throws Exception {
        mvc.perform(post("/usuarios/2/editar")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("nombre", "Carlos Ramos Actualizado")
                        .param("nombreUsuario", "cramos")
                        .param("rol", "VENDEDOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.url").value("/usuarios"));
    }

    @Test
    @DisplayName("Categorías: listado renderiza '+' de subcategoría y lápiz en modal con textos sin jerga")
    void categorias_renderizaSubcategoriaYEdicionModalConTextosSinJerga() throws Exception {
        ArbolCategorias arbol = new ArbolCategorias();
        arbol.insertar(1, "Analgésicos", null);
        arbol.insertar(2, "Opiáceos", 1);

        Categoria c1 = new Categoria(1, "Analgésicos", null, true);
        Categoria c2 = new Categoria(2, "Opiáceos", 1, true);

        when(categoriaServicio.arbol()).thenReturn(arbol);
        when(categoriaServicio.listarActivas()).thenReturn(List.of(c1, c2));

        mvc.perform(get("/categorias").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modal-open=\"modal-nueva-categoria\"")))
                .andExpect(content().string(containsString("data-modo=\"subcategoria\"")))
                .andExpect(content().string(containsString("data-padre-id=\"1\"")))
                .andExpect(content().string(containsString("data-titulo=\"Nueva subcategoría de Analgésicos\"")))
                .andExpect(content().string(containsString("data-modo=\"editar\"")))
                .andExpect(content().string(containsString("data-titulo=\"Editar Analgésicos\"")))
                // Textos sin jerga
                .andExpect(content().string(containsString("Ponle un nombre y, si quieres, elige a qué categoría pertenece.")))
                .andExpect(content().string(containsString("Categoría principal (opcional)")))
                .andExpect(content().string(containsString("— Ninguna (será una categoría principal) —")))
                .andExpect(content().string(containsString("Déjalo vacío si es un grupo principal.")));
    }

    @Test
    @DisplayName("Categorías: POST /categorias/nueva responde JSON 200 con url de resaltar ante éxito con AJAX")
    void categorias_crearAjax_exitoConResaltar() throws Exception {
        when(categoriaServicio.crear(eq("Antipiréticos"), any())).thenReturn(7);

        mvc.perform(post("/categorias/nueva")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("nombre", "Antipiréticos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.url").value("/categorias?resaltar=7"))
                .andExpect(jsonPath("$.id").value(7));
    }
}
