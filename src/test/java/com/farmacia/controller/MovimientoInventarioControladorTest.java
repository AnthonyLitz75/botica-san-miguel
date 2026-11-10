package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.DetalleMovimientoInventario;
import com.farmacia.model.MovimientoInventario;
import com.farmacia.service.MovimientoInventarioServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MovimientoInventarioControlador.class)
@Import(SeguridadConfiguracion.class)
class MovimientoInventarioControladorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MovimientoInventarioServicio servicio;

    @MockitoBean
    private UsuarioDetallesServicio usuarios;

    // -------------------------------------------------------------------------
    // Escenario 1: GET /movimientos sin autenticación redirige al formulario de login
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 1: Solicitud anónima a /movimientos redirige a /login (302)")
    void listar_sinAutenticacion_redirigeALogin() throws Exception {
        mvc.perform(get("/movimientos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));

        verify(servicio, never()).listar();
    }

    // -------------------------------------------------------------------------
    // Escenario 2: GET /movimientos con rol VENDEDOR es denegado con 403 Forbidden
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 2: Usuario con rol VENDEDOR recibe 403 Forbidden en /movimientos")
    void listar_conRolVendedor_retorna403Forbidden() throws Exception {
        mvc.perform(get("/movimientos").with(user("vendedor1").roles("VENDEDOR")))
                .andExpect(status().isForbidden());

        verify(servicio, never()).listar();
    }

    // -------------------------------------------------------------------------
    // Escenario 3: GET /movimientos con rol COMPRAS es denegado con 403 Forbidden
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 3: Usuario con rol COMPRAS recibe 403 Forbidden en /movimientos")
    void listar_conRolCompras_retorna403Forbidden() throws Exception {
        mvc.perform(get("/movimientos").with(user("compras1").roles("COMPRAS")))
                .andExpect(status().isForbidden());

        verify(servicio, never()).listar();
    }

    // -------------------------------------------------------------------------
    // Escenario 4: GET /movimientos con rol ALMACENERO retorna vista y modelo
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 4: Usuario con rol ALMACENERO accede a /movimientos (200 OK y vista correcta)")
    void listar_conRolAlmacenero_retornaVistaListadoYModelo() throws Exception {
        var lista = List.of(
                new MovimientoInventario(1, 2, null, null, null, "SALIDA", LocalDateTime.now(), "Ajuste por merma"),
                new MovimientoInventario(2, 2, 1, null, 10, "ENTRADA", LocalDateTime.now(), "Recepción orden 10")
        );
        when(servicio.listar()).thenReturn(lista);

        mvc.perform(get("/movimientos").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isOk())
                .andExpect(view().name("movimientos/listado"))
                .andExpect(model().attributeExists("movimientos"))
                .andExpect(model().attribute("movimientos", hasSize(2)));

        verify(servicio).listar();
    }

    // -------------------------------------------------------------------------
    // Escenario 5: GET /movimientos con rol ADMINISTRADOR retorna vista y modelo
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 5: Usuario con rol ADMINISTRADOR accede a /movimientos (200 OK y vista correcta)")
    void listar_conRolAdministrador_retornaVistaListadoYModelo() throws Exception {
        when(servicio.listar()).thenReturn(List.of());

        mvc.perform(get("/movimientos").with(user("admin1").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("movimientos/listado"))
                .andExpect(model().attributeExists("movimientos"))
                .andExpect(model().attribute("movimientos", hasSize(0)));

        verify(servicio).listar();
    }

    // -------------------------------------------------------------------------
    // Escenario 6a: GET /movimientos/{id} con ALMACENERO cuando existe
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 6a: Consulta de movimiento existente por ALMACENERO retorna 200 y detalle")
    void detalle_conIdExistente_retornaVistaDetalle() throws Exception {
        var movimiento = new MovimientoInventario(10, 2, null, null, null, "SALIDA", LocalDateTime.now(), "Salida controlada");
        var detalles = List.of(
                new DetalleMovimientoInventario(3, "MED-01", "Paracetamol 500mg", "LOTE-101", "CAJA", 5)
        );

        when(servicio.buscarPorId(10)).thenReturn(Optional.of(movimiento));
        when(servicio.listarDetalles(10)).thenReturn(detalles);

        mvc.perform(get("/movimientos/10").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isOk())
                .andExpect(view().name("movimientos/detalle"))
                .andExpect(model().attribute("movimiento", movimiento))
                .andExpect(model().attribute("detalles", detalles));

        verify(servicio).buscarPorId(10);
        verify(servicio).listarDetalles(10);
    }

    // -------------------------------------------------------------------------
    // Escenario 6b: GET /movimientos/{id} con ALMACENERO cuando no existe retorna 404
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 6b: Consulta de movimiento inexistente retorna 404 Not Found")
    void detalle_conIdInexistente_retorna404NotFound() throws Exception {
        when(servicio.buscarPorId(999)).thenReturn(Optional.empty());

        mvc.perform(get("/movimientos/999").with(user("almacen1").roles("ALMACENERO")))
                .andExpect(status().isNotFound());

        verify(servicio).buscarPorId(999);
        verify(servicio, never()).listarDetalles(999);
    }

    // -------------------------------------------------------------------------
    // Escenario 7: POST /movimientos con ADMINISTRADOR y CSRF válido es bloqueado por denyAll
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Escenario 7: Intento de POST en /movimientos con ADMINISTRADOR y CSRF válido es rechazado con 403 (denyAll)")
    void postMovimientos_conAdministradorYCsrfValido_esRechazadoPorDenyAll() throws Exception {
        mvc.perform(post("/movimientos")
                        .with(user("admin1").roles("ADMINISTRADOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(servicio, never()).registrarSalida(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
