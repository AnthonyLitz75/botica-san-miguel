package com.farmacia.seguridad;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.controller.NavegacionCompartida;
import com.farmacia.service.AlertaInventarioServicio;
import com.farmacia.service.ClienteServicio;
import com.farmacia.service.LotePrioridadServicio;
import com.farmacia.service.LoteServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.MovimientoInventarioServicio;
import com.farmacia.service.OrdenCompraServicio;
import com.farmacia.service.PreparacionVentaServicio;
import com.farmacia.service.ProveedorServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import com.farmacia.service.UsuarioServicio;
import com.farmacia.service.VentaServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Spec 013 (CA-1.2, CA-1.3): matriz de rutas web × roles según la sección Roles del README.
 * Fija el comportamiento de permisos: cualquier cambio en {@code SeguridadConfiguracion} que abra o cierre
 * una ruta sin querer hace fallar esta prueba. Las specs siguientes añaden aquí sus rutas.
 * <p>
 * Solo se verifica la decisión de seguridad: los servicios son simulados, así que una ruta permitida puede
 * terminar en error de la vista; lo que importa es que no responda 403 ni redirija al login.
 */
@WebMvcTest
@Import({SeguridadConfiguracion.class, NavegacionCompartida.class})
class MatrizRutasTest {

    private static final String A = "ADMINISTRADOR";
    private static final String V = "VENDEDOR";
    private static final String L = "ALMACENERO";
    private static final String C = "COMPRAS";
    private static final List<String> ROLES = List.of(A, V, L, C);

    @Autowired private MockMvc mvc;

    @MockitoBean private AlertaInventarioServicio alertaInventarioServicio;
    @MockitoBean private ClienteServicio clienteServicio;
    @MockitoBean private LotePrioridadServicio lotePrioridadServicio;
    @MockitoBean private LoteServicio loteServicio;
    @MockitoBean private MedicamentoServicio medicamentoServicio;
    @MockitoBean private MovimientoInventarioServicio movimientoInventarioServicio;
    @MockitoBean private OrdenCompraServicio ordenCompraServicio;
    @MockitoBean private PreparacionVentaServicio preparacionVentaServicio;
    @MockitoBean private ProveedorServicio proveedorServicio;
    @MockitoBean private VentaServicio ventaServicio;
    @MockitoBean private UsuarioDetallesServicio usuarioDetallesServicio;
    @MockitoBean private UsuarioServicio usuarioServicio;
    @MockitoBean private com.farmacia.service.AuditoriaServicio auditoriaServicio;

    /** Ruta, método y roles que la pueden usar (matriz del README). */
    static Stream<Arguments> rutas() {
        return Stream.of(
                // Medicamentos y lotes
                ruta("GET", "/medicamentos", A, V, L, C),
                ruta("GET", "/medicamentos/1/lotes", A, V, L, C),
                ruta("GET", "/medicamentos/nuevo", A),
                ruta("POST", "/medicamentos", A),
                ruta("GET", "/medicamentos/1/editar", A),
                ruta("POST", "/medicamentos/1/editar", A),
                ruta("POST", "/medicamentos/1/desactivar", A),
                ruta("POST", "/medicamentos/1/activar", A),
                // Proveedores
                ruta("GET", "/proveedores", A, L, C),
                ruta("GET", "/proveedores/nuevo", A, C),
                ruta("POST", "/proveedores", A, C),
                ruta("GET", "/proveedores/1/editar", A, C),
                ruta("POST", "/proveedores/1/editar", A, C),
                ruta("POST", "/proveedores/1/desactivar", A, C),
                ruta("POST", "/proveedores/1/activar", A, C),
                // Movimientos y salidas
                ruta("GET", "/movimientos", A, L),
                ruta("GET", "/movimientos/1", A, L),
                ruta("GET", "/salidas/nueva", A, L),
                ruta("POST", "/salidas/nueva", A, L),
                ruta("GET", "/lotes/1/salida", A, L),
                ruta("POST", "/lotes/1/salida", A, L),
                ruta("GET", "/inventario/existencias-iniciales", A, L),
                ruta("POST", "/inventario/existencias-iniciales", A, L),
                // Alertas y clientes
                ruta("GET", "/alertas", A, V, L, C),
                ruta("GET", "/clientes", A, V),
                ruta("GET", "/clientes/nuevo", A, V),
                ruta("POST", "/clientes", A, V),
                ruta("GET", "/clientes/1/editar", A, V),
                ruta("POST", "/clientes/1/editar", A, V),
                // Órdenes de compra
                ruta("GET", "/ordenes-compra", A, L, C),
                ruta("GET", "/ordenes-compra/1", A, L, C),
                ruta("GET", "/ordenes-compra/nuevo", A, C),
                ruta("POST", "/ordenes-compra", A, C),
                ruta("GET", "/ordenes-compra/1/recepcion", A, L),
                ruta("POST", "/ordenes-compra/1/recepcion", A, L),
                ruta("POST", "/ordenes-compra/1/cancelar", A, C),
                // Ventas
                ruta("GET", "/ventas/nueva", A, V),
                ruta("GET", "/ventas/preparacion", A, V),
                ruta("POST", "/ventas/preparacion", A, V),
                ruta("POST", "/ventas/preparacion/deshacer", A, V),
                ruta("DELETE", "/ventas/preparacion/1", A, V),
                ruta("PUT", "/ventas/preparacion/1/descuento", A, V),
                ruta("POST", "/ventas/confirmar", A, V),
                ruta("GET", "/ventas/1", A, V),
                // Historial de ventas (spec 005)
                ruta("GET", "/ventas", A),
                ruta("GET", "/ventas/mias", V),
                // Auditoría (spec 014)
                ruta("GET", "/auditoria", A),
                ruta("GET", "/auditoria.csv", A),
                // Usuarios (spec 007)
                ruta("GET", "/usuarios", A),
                ruta("GET", "/usuarios/nuevo", A),
                ruta("POST", "/usuarios", A),
                ruta("GET", "/usuarios/1/editar", A),
                ruta("POST", "/usuarios/1/editar", A),
                ruta("POST", "/usuarios/1/clave", A),
                ruta("POST", "/usuarios/1/desactivar", A),
                ruta("POST", "/usuarios/1/activar", A));
    }

    @ParameterizedTest(name = "{0} {1} → permitido a {2}")
    @MethodSource("rutas")
    @DisplayName("Cada rol recibe exactamente los permisos de la matriz")
    void cadaRolRecibeLosPermisosDeLaMatriz(String metodo, String ruta, Set<String> permitidos) throws Exception {
        for (String rol : ROLES) {
            var resultado = ejecutar(peticion(metodo, ruta).with(user("u-" + rol).roles(rol)));
            if (permitidos.contains(rol)) {
                assertTrue(resultado.paso(), rol + " debería poder usar " + metodo + " " + ruta
                        + " pero recibió " + resultado.estado() + " " + resultado.destino());
            } else {
                assertEquals(403, resultado.estado(), rol + " no debería poder usar " + metodo + " " + ruta);
            }
        }
    }

    @ParameterizedTest(name = "{0} {1} sin sesión")
    @MethodSource("rutas")
    @DisplayName("CA-1.3: sin sesión, toda ruta protegida redirige al login")
    void sinSesionRedirigeAlLogin(String metodo, String ruta, Set<String> permitidos) throws Exception {
        var resultado = ejecutar(peticion(metodo, ruta));
        assertEquals(302, resultado.estado(), metodo + " " + ruta);
        assertTrue(resultado.destino() != null && resultado.destino().endsWith("/login"),
                metodo + " " + ruta + " redirige a " + resultado.destino());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("CA-1.3: /login y los recursos estáticos son públicos")
    void loginYEstaticosSonPublicos() throws Exception {
        assertEquals(200, ejecutar(peticion("GET", "/login")).estado());
        assertNotEquals(302, ejecutar(peticion("GET", "/css/estilos.css")).estado());
        assertNotEquals(403, ejecutar(peticion("GET", "/css/estilos.css")).estado());
    }

    private static Arguments ruta(String metodo, String ruta, String... permitidos) {
        return Arguments.of(metodo, ruta, Set.of(permitidos));
    }

    private static MockHttpServletRequestBuilder peticion(String metodo, String ruta) {
        var builder = request(HttpMethod.valueOf(metodo), ruta);
        if (!metodo.equals("GET")) {
            builder = builder.with(csrf()).contentType("application/json").content("{}");
        }
        return builder;
    }

    /**
     * Ejecuta la petición. Si el controlador lanza una excepción (servicios simulados), la petición ya pasó la
     * seguridad: se considera permitida.
     */
    private Resultado ejecutar(MockHttpServletRequestBuilder peticion) {
        try {
            MvcResult r = mvc.perform(peticion).andReturn();
            return new Resultado(r.getResponse().getStatus(), r.getResponse().getRedirectedUrl());
        } catch (Exception excepcionDelControlador) {
            return new Resultado(-1, null);
        }
    }

    private record Resultado(int estado, String destino) {
        boolean paso() {
            boolean alLogin = destino != null && destino.endsWith("/login");
            return estado != 403 && estado != 401 && !alLogin;
        }
    }
}
