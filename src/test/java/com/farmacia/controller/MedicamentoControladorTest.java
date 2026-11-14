package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.dto.MedicamentoFormulario;
import com.farmacia.model.Medicamento;
import com.farmacia.service.MedicamentoServicio;
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

import java.math.BigDecimal;
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

@WebMvcTest(MedicamentoControlador.class)
@Import(SeguridadConfiguracion.class)
class MedicamentoControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MedicamentoServicio servicio;

    @MockitoBean
    private com.farmacia.service.CategoriaServicio categoriaServicio;

    @MockitoBean
    private com.farmacia.service.AuditoriaServicio auditoriaServicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    private Medicamento crearMedicamentoPrueba(Integer id, String codigo, String nombre) {
        return new Medicamento(
                id,
                codigo,
                nombre,
                "500mg",
                "Caja x 20 tabletas",
                "CAJA",
                new BigDecimal("12.50"),
                20,
                true);
    }

    // ==========================================
    // GET /medicamentos (Listado)
    // ==========================================

    @Test
    @DisplayName("GET /medicamentos sin autenticacion redirige al formulario de login")
    void listar_sinAutenticacion_redirigeALogin() throws Exception {
        mvc.perform(get("/medicamentos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ADMINISTRADOR", "ALMACENERO", "COMPRAS", "VENDEDOR" })
    @DisplayName("GET /medicamentos con roles autorizados retorna vista de listado")
    void listar_conRolesAutorizados_retornaListado(String rol) throws Exception {
        Medicamento med = crearMedicamentoPrueba(1, "MED-001", "Paracetamol 500mg");
        when(servicio.buscarPorEstado("", true)).thenReturn(List.of(med));

        mvc.perform(get("/medicamentos").with(user("usuario").roles(rol)))
                .andExpect(status().isOk())
                .andExpect(view().name("medicamentos/listado"))
                .andExpect(model().attribute("medicamentos", List.of(med)))
                .andExpect(model().attribute("buscar", ""))
                .andExpect(model().attribute("activo", true));
    }

    @Test
    @DisplayName("GET /medicamentos propaga parametros de busqueda y estado al servicio")
    void listar_conParametros_propagaFiltrosAlServicio() throws Exception {
        when(servicio.buscarPorEstado("amoxi", false)).thenReturn(List.of());

        mvc.perform(get("/medicamentos")
                .with(user("admin").roles("ADMINISTRADOR"))
                .param("buscar", "amoxi")
                .param("activo", "false"))
                .andExpect(status().isOk())
                .andExpect(view().name("medicamentos/listado"))
                .andExpect(model().attribute("buscar", "amoxi"))
                .andExpect(model().attribute("activo", false));

        verify(servicio).buscarPorEstado("amoxi", false);
    }

    @Test
    @DisplayName("GET /medicamentos muestra la columna Stock actual y el badge correspondiente")
    void listar_muestraColumnaStockActual() throws Exception {
        Medicamento med = new Medicamento(1, "MED-001", "Paracetamol 500mg", "500mg", "Caja", "CAJA",
                new BigDecimal("12.50"), 10, true, 25);
        when(servicio.buscarPorEstado("", true)).thenReturn(List.of(med));

        mvc.perform(get("/medicamentos").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Stock actual")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("stock-disponible")));
    }

    // ==========================================
    // GET /medicamentos/nuevo
    // ==========================================

    @ParameterizedTest
    @ValueSource(strings = { "ALMACENERO", "COMPRAS", "VENDEDOR" })
    @DisplayName("GET /medicamentos/nuevo deniega acceso (403) a roles no administradores")
    void nuevo_conRolesNoAutorizados_retorna403(String rol) throws Exception {
        mvc.perform(get("/medicamentos/nuevo").with(user("usuario").roles(rol)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /medicamentos/nuevo permite acceso a ADMINISTRADOR y retorna formulario")
    void nuevo_conAdministrador_retornaFormulario() throws Exception {
        mvc.perform(get("/medicamentos/nuevo").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("medicamentos/formulario"))
                .andExpect(model().attributeExists("formulario"));
    }

    // ==========================================
    // POST /medicamentos (Guardar nuevo)
    // ==========================================

    @Test
    @DisplayName("POST /medicamentos sin CSRF es rechazado con 403 Forbidden")
    void guardar_sinCsrf_retorna403() throws Exception {
        mvc.perform(post("/medicamentos")
                .with(user("admin").roles("ADMINISTRADOR"))
                .param("codigo", "MED-001")
                .param("nombre", "Paracetamol 500mg")
                .param("concentracion", "500mg")
                .param("presentacion", "Caja x 20 tabletas")
                .param("unidadControl", "CAJA")
                .param("precioVenta", "10.00")
                .param("stockMinimo", "5"))
                .andExpect(status().isForbidden());

        verify(servicio, never()).guardar(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ALMACENERO", "COMPRAS", "VENDEDOR" })
    @DisplayName("POST /medicamentos deniega acceso (403) a roles distintos de ADMINISTRADOR")
    void guardar_conRolesNoAutorizados_retorna403(String rol) throws Exception {
        mvc.perform(post("/medicamentos")
                .with(user("usuario").roles(rol))
                .with(csrf())
                .param("codigo", "MED-001")
                .param("nombre", "Paracetamol 500mg")
                .param("concentracion", "500mg")
                .param("presentacion", "Caja x 20 tabletas")
                .param("unidadControl", "CAJA")
                .param("precioVenta", "10.00")
                .param("stockMinimo", "5"))
                .andExpect(status().isForbidden());

        verify(servicio, never()).guardar(any());
    }

    @Test
    @DisplayName("POST /medicamentos con datos invalidos retorna formulario con errores de validacion")
    void guardar_conErroresValidacion_retornaFormularioConErrores() throws Exception {
        mvc.perform(post("/medicamentos")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf())
                .param("codigo", "")
                .param("nombre", "")
                .param("presentacion", "")
                .param("unidadControl", "INVALIDA")
                .param("precioVenta", "-5.00")
                .param("stockMinimo", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("medicamentos/formulario"))
                .andExpect(model().attributeHasFieldErrors("formulario", "codigo", "nombre", "presentacion", "unidadControl", "precioVenta", "stockMinimo"));

        verify(servicio, never()).guardar(any());
    }

    @Test
    @DisplayName("POST /medicamentos con codigo duplicado rechaza el campo codigo y retorna formulario")
    void guardar_conCodigoDuplicado_rechazaCampoCodigo() throws Exception {
        org.mockito.Mockito.doThrow(new DuplicateKeyException("Codigo duplicado"))
                .when(servicio).guardar(any(MedicamentoFormulario.class));

        mvc.perform(post("/medicamentos")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf())
                .param("codigo", "MED-DUP")
                .param("nombre", "Paracetamol Duplicado")
                .param("concentracion", "500mg")
                .param("presentacion", "Caja x 20 tabletas")
                .param("unidadControl", "CAJA")
                .param("precioVenta", "12.00")
                .param("stockMinimo", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("medicamentos/formulario"))
                .andExpect(model().attributeHasFieldErrorCode("formulario", "codigo", "codigo.duplicado"));
    }

    @Test
    @DisplayName("POST /medicamentos con datos validos persiste y redirige a listado")
    void guardar_datosValidos_guardaYRedirige() throws Exception {
        mvc.perform(post("/medicamentos")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf())
                .param("codigo", "MED-010")
                .param("nombre", "Ibuprofeno 400mg")
                .param("concentracion", "400mg")
                .param("presentacion", "Caja x 30 tabletas")
                .param("unidadControl", "CAJA")
                .param("precioVenta", "15.50")
                .param("stockMinimo", "20"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/medicamentos"));

        verify(servicio).guardar(any(MedicamentoFormulario.class));
    }

    // ==========================================
    // GET /medicamentos/{id}/editar
    // ==========================================

    @Test
    @DisplayName("GET /medicamentos/{id}/editar con id existente retorna formulario poblado")
    void editar_conIdExistente_retornaFormulario() throws Exception {
        Medicamento med = crearMedicamentoPrueba(1, "MED-001", "Paracetamol 500mg");
        when(servicio.buscarPorId(1)).thenReturn(Optional.of(med));

        mvc.perform(get("/medicamentos/1/editar").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("medicamentos/formulario"))
                .andExpect(model().attributeExists("formulario"))
                .andExpect(model().attribute("idMedicamento", 1));
    }

    @Test
    @DisplayName("GET /medicamentos/{id}/editar con id inexistente retorna 404 Not Found")
    void editar_conIdInexistente_retorna404() throws Exception {
        when(servicio.buscarPorId(999)).thenReturn(Optional.empty());

        mvc.perform(get("/medicamentos/999/editar").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /medicamentos/{id}/editar con rol no administrador retorna 403 Forbidden")
    void editar_conRolNoAutorizado_retorna403() throws Exception {
        mvc.perform(get("/medicamentos/1/editar").with(user("compras").roles("COMPRAS")))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // POST /medicamentos/{id}/editar
    // ==========================================

    @Test
    @DisplayName("POST /medicamentos/{id}/editar con id inexistente antes de validar retorna 404")
    void actualizar_conIdInexistente_retorna404() throws Exception {
        when(servicio.buscarPorId(999)).thenReturn(Optional.empty());

        mvc.perform(post("/medicamentos/999/editar")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf())
                .param("codigo", "MED-001")
                .param("nombre", "Paracetamol")
                .param("concentracion", "500mg")
                .param("presentacion", "Caja x 20 tabletas")
                .param("unidadControl", "CAJA")
                .param("precioVenta", "10.00")
                .param("stockMinimo", "5"))
                .andExpect(status().isNotFound());

        verify(servicio, never()).actualizar(any(), any());
    }

    @Test
    @DisplayName("POST /medicamentos/{id}/editar con errores de validacion retorna formulario con idMedicamento")
    void actualizar_conErroresValidacion_retornaFormulario() throws Exception {
        Medicamento med = crearMedicamentoPrueba(1, "MED-001", "Paracetamol 500mg");
        when(servicio.buscarPorId(1)).thenReturn(Optional.of(med));

        mvc.perform(post("/medicamentos/1/editar")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf())
                .param("codigo", "")
                .param("nombre", "")
                .param("presentacion", "")
                .param("unidadControl", "BOTELLA")
                .param("precioVenta", "-1.00")
                .param("stockMinimo", "-5"))
                .andExpect(status().isOk())
                .andExpect(view().name("medicamentos/formulario"))
                .andExpect(model().attribute("idMedicamento", 1))
                .andExpect(model().attributeHasFieldErrors("formulario", "codigo", "nombre", "presentacion", "unidadControl", "precioVenta", "stockMinimo"));

        verify(servicio, never()).actualizar(any(), any());
    }

    @Test
    @DisplayName("POST /medicamentos/{id}/editar cuando servicio retorna false lanza 404 Not Found")
    void actualizar_cuandoServicioRetornaFalse_lanza404() throws Exception {
        Medicamento med = crearMedicamentoPrueba(1, "MED-001", "Paracetamol 500mg");
        when(servicio.buscarPorId(1)).thenReturn(Optional.of(med));
        when(servicio.actualizar(eq(1), any(MedicamentoFormulario.class))).thenReturn(false);

        mvc.perform(post("/medicamentos/1/editar")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf())
                .param("codigo", "MED-001")
                .param("nombre", "Paracetamol Actualizado")
                .param("concentracion", "500mg")
                .param("presentacion", "Caja x 20 tabletas")
                .param("unidadControl", "CAJA")
                .param("precioVenta", "15.00")
                .param("stockMinimo", "10"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /medicamentos/{id}/editar con codigo duplicado rechaza el campo codigo")
    void actualizar_conCodigoDuplicado_rechazaCampoCodigo() throws Exception {
        Medicamento med = crearMedicamentoPrueba(1, "MED-001", "Paracetamol 500mg");
        when(servicio.buscarPorId(1)).thenReturn(Optional.of(med));
        when(servicio.actualizar(eq(1), any(MedicamentoFormulario.class)))
                .thenThrow(new DuplicateKeyException("Codigo duplicado"));

        mvc.perform(post("/medicamentos/1/editar")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf())
                .param("codigo", "MED-DUP")
                .param("nombre", "Paracetamol Duplicado")
                .param("concentracion", "500mg")
                .param("presentacion", "Caja x 20 tabletas")
                .param("unidadControl", "CAJA")
                .param("precioVenta", "15.00")
                .param("stockMinimo", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("medicamentos/formulario"))
                .andExpect(model().attribute("idMedicamento", 1))
                .andExpect(model().attributeHasFieldErrorCode("formulario", "codigo", "codigo.duplicado"));
    }

    @Test
    @DisplayName("POST /medicamentos/{id}/editar con datos validos actualiza y redirige")
    void actualizar_datosValidos_actualizaYRedirige() throws Exception {
        Medicamento med = crearMedicamentoPrueba(1, "MED-001", "Paracetamol 500mg");
        when(servicio.buscarPorId(1)).thenReturn(Optional.of(med));
        when(servicio.actualizar(eq(1), any(MedicamentoFormulario.class))).thenReturn(true);

        mvc.perform(post("/medicamentos/1/editar")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf())
                .param("codigo", "MED-001")
                .param("nombre", "Paracetamol Forte 1g")
                .param("concentracion", "1g")
                .param("presentacion", "Caja x 20 tabletas")
                .param("unidadControl", "CAJA")
                .param("precioVenta", "18.00")
                .param("stockMinimo", "15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/medicamentos"));

        verify(servicio).actualizar(eq(1), any(MedicamentoFormulario.class));
    }

    // ==========================================
    // POST /medicamentos/{id}/desactivar y activar
    // ==========================================

    @Test
    @DisplayName("POST /medicamentos/{id}/desactivar exitoso redirige a listado")
    void desactivar_exitoso_redirigeAListado() throws Exception {
        when(servicio.desactivar(1)).thenReturn(true);

        mvc.perform(post("/medicamentos/1/desactivar")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/medicamentos"));

        verify(servicio).desactivar(1);
    }

    @Test
    @DisplayName("POST /medicamentos/{id}/desactivar inexistente lanza 404 Not Found")
    void desactivar_inexistente_retorna404() throws Exception {
        when(servicio.desactivar(999)).thenReturn(false);

        mvc.perform(post("/medicamentos/999/desactivar")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /medicamentos/{id}/activar exitoso redirige a listado")
    void activar_exitoso_redirigeAListado() throws Exception {
        when(servicio.activar(1)).thenReturn(true);

        mvc.perform(post("/medicamentos/1/activar")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/medicamentos"));

        verify(servicio).activar(1);
    }

    @Test
    @DisplayName("POST /medicamentos/{id}/activar inexistente lanza 404 Not Found")
    void activar_inexistente_retorna404() throws Exception {
        when(servicio.activar(999)).thenReturn(false);

        mvc.perform(post("/medicamentos/999/activar")
                .with(user("admin").roles("ADMINISTRADOR"))
                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CA-3.2: GET /medicamentos?categoria=ID filtra incluyendo todos los descendientes en el árbol")
    void listar_conFiltroCategoria_incluyeDescendientes() throws Exception {
        com.farmacia.estructuras.ArbolCategorias arbol = new com.farmacia.estructuras.ArbolCategorias();
        arbol.insertar(1, "Analgésicos", null);
        arbol.insertar(2, "Antiinflamatorios", 1);
        arbol.insertar(3, "AINEs", 2);

        when(categoriaServicio.arbol()).thenReturn(arbol);
        when(categoriaServicio.listarActivas()).thenReturn(List.of());
        when(categoriaServicio.buscarPorId(1)).thenReturn(Optional.of(new com.farmacia.model.Categoria(1, "Analgésicos", null, true)));

        Medicamento med1 = crearMedicamentoPrueba(1, "MED-001", "Paracetamol");
        when(servicio.buscarActivosPorCategorias("", List.of(1, 2, 3))).thenReturn(List.of(med1));

        mvc.perform(get("/medicamentos")
                        .param("categoria", "1")
                        .with(user("vendedor").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("medicamentos/listado"))
                .andExpect(model().attributeExists("categoriaSeleccionada", "categoriaId", "medicamentos"));

        verify(servicio).buscarActivosPorCategorias("", List.of(1, 2, 3));
    }
}
