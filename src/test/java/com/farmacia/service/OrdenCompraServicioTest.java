package com.farmacia.service;

import com.farmacia.dto.DetalleOrdenCompraFormulario;
import com.farmacia.dto.OrdenCompraFormulario;
import com.farmacia.model.*;
import com.farmacia.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Pruebas Unitarias de OrdenCompraServicio")
class OrdenCompraServicioTest {
    /** Auditoría simulada (spec 014). */
    private final AuditoriaServicio auditoria = org.mockito.Mockito.mock(AuditoriaServicio.class);

    private OrdenCompraRepositorio ordenRepositorio;
    private DetalleOrdenCompraRepositorio detalleRepositorio;
    private UsuarioRepositorio usuarioRepositorio;
    private ProveedorRepositorio proveedorRepositorio;
    private MedicamentoRepositorio medicamentoRepositorio;
    private LoteRepositorio loteRepositorio;
    private MovimientoInventarioRepositorio movimientoRepositorio;

    private OrdenCompraServicio servicio;

    @BeforeEach
    void setUp() {
        ordenRepositorio = mock(OrdenCompraRepositorio.class);
        detalleRepositorio = mock(DetalleOrdenCompraRepositorio.class);
        usuarioRepositorio = mock(UsuarioRepositorio.class);
        proveedorRepositorio = mock(ProveedorRepositorio.class);
        medicamentoRepositorio = mock(MedicamentoRepositorio.class);
        loteRepositorio = mock(LoteRepositorio.class);
        movimientoRepositorio = mock(MovimientoInventarioRepositorio.class);

        servicio = new OrdenCompraServicio(
                ordenRepositorio,
                detalleRepositorio,
                usuarioRepositorio,
                proveedorRepositorio,
                medicamentoRepositorio,
                loteRepositorio,
                movimientoRepositorio,
                java.time.Clock.systemDefaultZone(),
                auditoria
        );
    }

    // =========================================================================
    // BLOQUE 1: ConsultasLectura (11 pruebas)
    // =========================================================================
    @Nested
    @DisplayName("Bloque 1: Consultas de Lectura")
    class ConsultasLectura {

        @Test
        @DisplayName("listar: retorna lista completa de órdenes cuando existen registros")
        void listar_conOrdenes_retornaListaCompleta() {
            var orden1 = new OrdenCompra(1, "OC-001", 10, 20, LocalDateTime.now(), "PENDIENTE", new BigDecimal("100.00"), "Obs 1");
            var orden2 = new OrdenCompra(2, "OC-002", 10, 21, LocalDateTime.now(), "RECIBIDA", new BigDecimal("250.00"), null);
            when(ordenRepositorio.listar()).thenReturn(List.of(orden1, orden2));

            List<OrdenCompra> resultado = servicio.listar();

            assertNotNull(resultado);
            assertEquals(2, resultado.size());
            assertEquals(orden1, resultado.get(0));
            assertEquals(orden2, resultado.get(1));
            verify(ordenRepositorio, times(1)).listar();
        }

        @Test
        @DisplayName("listar: retorna lista vacía cuando no existen órdenes")
        void listar_sinOrdenes_retornaListaVacia() {
            when(ordenRepositorio.listar()).thenReturn(List.of());

            List<OrdenCompra> resultado = servicio.listar();

            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(ordenRepositorio, times(1)).listar();
        }

        @Test
        @DisplayName("buscarPorId: retorna Optional con la orden cuando el ID existe")
        void buscarPorId_existente_retornaOptionalConOrden() {
            var orden = new OrdenCompra(5, "OC-005", 10, 20, LocalDateTime.now(), "PENDIENTE", new BigDecimal("50.00"), null);
            when(ordenRepositorio.buscarPorId(5)).thenReturn(Optional.of(orden));

            Optional<OrdenCompra> resultado = servicio.buscarPorId(5);

            assertTrue(resultado.isPresent());
            assertEquals(orden, resultado.get());
            verify(ordenRepositorio, times(1)).buscarPorId(5);
        }

        @Test
        @DisplayName("buscarPorId: retorna Optional.empty cuando el ID no existe")
        void buscarPorId_inexistente_retornaOptionalEmpty() {
            when(ordenRepositorio.buscarPorId(999)).thenReturn(Optional.empty());

            Optional<OrdenCompra> resultado = servicio.buscarPorId(999);

            assertTrue(resultado.isEmpty());
            verify(ordenRepositorio, times(1)).buscarPorId(999);
        }

        @Test
        @DisplayName("buscarPorId: propaga ID nulo al repositorio y retorna Optional.empty")
        void buscarPorId_idNulo_retornaOptionalEmpty() {
            when(ordenRepositorio.buscarPorId(null)).thenReturn(Optional.empty());

            Optional<OrdenCompra> resultado = servicio.buscarPorId(null);

            assertTrue(resultado.isEmpty());
            verify(ordenRepositorio, times(1)).buscarPorId(null);
        }

        @Test
        @DisplayName("listarDetalles: retorna lista de detalles asociados a la orden")
        void listarDetalles_conDetalles_retornaLista() {
            var det1 = new DetalleOrdenCompra(1, 10, 101, 5, new BigDecimal("10.00"), new BigDecimal("50.00"));
            var det2 = new DetalleOrdenCompra(2, 10, 102, 2, new BigDecimal("25.00"), new BigDecimal("50.00"));
            when(detalleRepositorio.listarPorOrden(10)).thenReturn(List.of(det1, det2));

            List<DetalleOrdenCompra> resultado = servicio.listarDetalles(10);

            assertNotNull(resultado);
            assertEquals(2, resultado.size());
            assertEquals(det1, resultado.get(0));
            assertEquals(det2, resultado.get(1));
            verify(detalleRepositorio, times(1)).listarPorOrden(10);
        }

        @Test
        @DisplayName("listarDetalles: retorna lista vacía cuando la orden no tiene detalles")
        void listarDetalles_sinDetalles_retornaVacia() {
            when(detalleRepositorio.listarPorOrden(20)).thenReturn(List.of());

            List<DetalleOrdenCompra> resultado = servicio.listarDetalles(20);

            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(detalleRepositorio, times(1)).listarPorOrden(20);
        }

        @Test
        @DisplayName("listarLineasRecepcion: retorna lista de líneas de recepción con saldos calculados")
        void listarLineasRecepcion_conLineas_retornaLista() {
            var linea1 = new LineaRecepcionVista(1, 101, "MED-01", "Amoxicilina", 50, 20, 30);
            var linea2 = new LineaRecepcionVista(2, 102, "MED-02", "Ibuprofeno", 20, 20, 0);
            when(detalleRepositorio.listarLineasRecepcion(15)).thenReturn(List.of(linea1, linea2));

            List<LineaRecepcionVista> resultado = servicio.listarLineasRecepcion(15);

            assertNotNull(resultado);
            assertEquals(2, resultado.size());
            assertEquals(linea1, resultado.get(0));
            assertEquals(linea2, resultado.get(1));
            verify(detalleRepositorio, times(1)).listarLineasRecepcion(15);
        }

        @Test
        @DisplayName("listarLineasRecepcion: retorna lista vacía cuando no existen líneas")
        void listarLineasRecepcion_sinLineas_retornaVacia() {
            when(detalleRepositorio.listarLineasRecepcion(99)).thenReturn(List.of());

            List<LineaRecepcionVista> resultado = servicio.listarLineasRecepcion(99);

            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(detalleRepositorio, times(1)).listarLineasRecepcion(99);
        }

        @Test
        @DisplayName("listarDetallesVista: retorna lista proyectada de detalles para vista")
        void listarDetallesVista_conRegistros_retornaLista() {
            var vista1 = new DetalleOrdenCompraVista(1, 101, "MED-01", "Paracetamol", "Caja", 10, 0, 10, new BigDecimal("5.00"), new BigDecimal("50.00"));
            var vista2 = new DetalleOrdenCompraVista(2, 102, "MED-02", "Omeprazol", "Frasco", 5, 5, 0, new BigDecimal("12.00"), new BigDecimal("60.00"));
            when(detalleRepositorio.listarVistaPorOrden(30)).thenReturn(List.of(vista1, vista2));

            List<DetalleOrdenCompraVista> resultado = servicio.listarDetallesVista(30);

            assertNotNull(resultado);
            assertEquals(2, resultado.size());
            assertEquals(vista1, resultado.get(0));
            assertEquals(vista2, resultado.get(1));
            verify(detalleRepositorio, times(1)).listarVistaPorOrden(30);
        }

        @Test
        @DisplayName("listarDetallesVista: retorna lista vacía cuando no existen registros")
        void listarDetallesVista_sinRegistros_retornaVacia() {
            when(detalleRepositorio.listarVistaPorOrden(40)).thenReturn(List.of());

            List<DetalleOrdenCompraVista> resultado = servicio.listarDetallesVista(40);

            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(detalleRepositorio, times(1)).listarVistaPorOrden(40);
        }
    }

    // =========================================================================
    // BLOQUE 2: ValidacionUsuarioYProveedor (7 pruebas)
    // =========================================================================
    @Nested
    @DisplayName("Bloque 2: Validación de Usuario y Proveedor")
    class ValidacionUsuarioYProveedor {

        @Test
        @DisplayName("guardar: lanza AccessDeniedException cuando el usuario no existe")
        void guardar_usuarioNoExiste_lanzaAccessDeniedException() {
            when(usuarioRepositorio.buscarPorNombreUsuario("desconocido")).thenReturn(Optional.empty());
            var form = new OrdenCompraFormulario(1, "Obs", List.of(new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("10.00"))));

            var ex = assertThrows(AccessDeniedException.class,
                    () -> servicio.guardar("desconocido", form));

            assertEquals("Usuario no autorizado", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, proveedorRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza AccessDeniedException cuando el usuario está inactivo")
        void guardar_usuarioInactivo_lanzaAccessDeniedException() {
            var usuarioInactivo = new Usuario(1, "Inactivo", "user.inactivo", "hash", "ADMINISTRADOR", false);
            when(usuarioRepositorio.buscarPorNombreUsuario("user.inactivo")).thenReturn(Optional.of(usuarioInactivo));
            var form = new OrdenCompraFormulario(1, "Obs", List.of(new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("10.00"))));

            var ex = assertThrows(AccessDeniedException.class,
                    () -> servicio.guardar("user.inactivo", form));

            assertEquals("Usuario no autorizado", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, proveedorRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza AccessDeniedException cuando el rol no es ADMINISTRADOR ni COMPRAS")
        void guardar_usuarioRolNoAutorizado_lanzaAccessDeniedException() {
            var usuarioAlmacen = new Usuario(2, "Almacenero", "user.almacen", "hash", "ALMACENERO", true);
            when(usuarioRepositorio.buscarPorNombreUsuario("user.almacen")).thenReturn(Optional.of(usuarioAlmacen));
            var form = new OrdenCompraFormulario(1, "Obs", List.of(new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("10.00"))));

            var ex = assertThrows(AccessDeniedException.class,
                    () -> servicio.guardar("user.almacen", form));

            assertEquals("Usuario no autorizado", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, proveedorRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando el idProveedor es nulo")
        void guardar_proveedorIdNulo_lanzaIllegalArgumentException() {
            var usuarioAdmin = new Usuario(1, "Admin", "admin", "hash", "ADMINISTRADOR", true);
            when(usuarioRepositorio.buscarPorNombreUsuario("admin")).thenReturn(Optional.of(usuarioAdmin));
            var form = new OrdenCompraFormulario(null, "Obs", List.of(new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("10.00"))));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", form));

            assertEquals("Selecciona un proveedor", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, proveedorRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando el idProveedor es menor o igual a cero")
        void guardar_proveedorIdMenorOIgualACero_lanzaIllegalArgumentException() {
            var usuarioAdmin = new Usuario(1, "Admin", "admin", "hash", "ADMINISTRADOR", true);
            when(usuarioRepositorio.buscarPorNombreUsuario("admin")).thenReturn(Optional.of(usuarioAdmin));
            var form = new OrdenCompraFormulario(0, "Obs", List.of(new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("10.00"))));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", form));

            assertEquals("Selecciona un proveedor", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, proveedorRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando el proveedor no existe en BD")
        void guardar_proveedorNoExiste_lanzaIllegalArgumentException() {
            var usuarioCompras = new Usuario(3, "Compras", "compras", "hash", "COMPRAS", true);
            when(usuarioRepositorio.buscarPorNombreUsuario("compras")).thenReturn(Optional.of(usuarioCompras));
            when(proveedorRepositorio.buscarPorId(99)).thenReturn(Optional.empty());
            var form = new OrdenCompraFormulario(99, "Obs", List.of(new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("10.00"))));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("compras", form));

            assertEquals("Proveedor no encontrado", ex.getMessage());
            verify(proveedorRepositorio, times(1)).buscarPorId(99);
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando el proveedor está inactivo")
        void guardar_proveedorInactivo_lanzaIllegalArgumentException() {
            var usuarioCompras = new Usuario(3, "Compras", "compras", "hash", "COMPRAS", true);
            var proveedorInactivo = new Proveedor(7, "20123456789", "Distribuidora Inactiva", "123456", "prov@test.com", "Av. 1", false);
            when(usuarioRepositorio.buscarPorNombreUsuario("compras")).thenReturn(Optional.of(usuarioCompras));
            when(proveedorRepositorio.buscarPorId(7)).thenReturn(Optional.of(proveedorInactivo));
            var form = new OrdenCompraFormulario(7, "Obs", List.of(new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("10.00"))));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("compras", form));

            assertEquals("El proveedor seleccionado esta inactivo", ex.getMessage());
            verify(proveedorRepositorio, times(1)).buscarPorId(7);
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, medicamentoRepositorio);
        }
    }

    // =========================================================================
    // BLOQUE 3: ValidacionCabeceraYObservaciones (3 pruebas)
    // =========================================================================
    @Nested
    @DisplayName("Bloque 3: Validación de Cabecera y Observaciones")
    class ValidacionCabeceraYObservaciones {

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando el formulario es nulo")
        void guardar_formularioNulo_lanzaIllegalArgumentException() {
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", null));

            assertEquals("Los datos de la orden son obligatorios", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, usuarioRepositorio, proveedorRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando las observaciones superan 500 caracteres")
        void guardar_observacionesExceden500Caracteres_lanzaIllegalArgumentException() {
            String obsLarga = "A".repeat(501);
            var form = new OrdenCompraFormulario(1, obsLarga, List.of(new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("10.00"))));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", form));

            assertEquals("Las observaciones admiten hasta 500 caracteres", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, usuarioRepositorio, proveedorRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: convierte observaciones en blanco o nulas a null sanitizado al persistir")
        void guardar_observacionesEnBlancoONulas_persisteNullSanitizado() {
            var usuario = new Usuario(1, "Admin", "admin", "hash", "ADMINISTRADOR", true);
            var proveedor = new Proveedor(5, "20123456789", "Prov Activo", "123", "a@a.com", "Dir", true);
            var medicamento = new Medicamento(10, "MED-10", "Ibuprofeno", "400mg", "Caja", "Unidad", new BigDecimal("5.00"), 10, true);

            when(usuarioRepositorio.buscarPorNombreUsuario("admin")).thenReturn(Optional.of(usuario));
            when(proveedorRepositorio.buscarPorId(5)).thenReturn(Optional.of(proveedor));
            when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(medicamento));
            when(ordenRepositorio.guardar(anyString(), anyInt(), anyInt(), any(), any())).thenReturn(77);

            var formConEspacios = new OrdenCompraFormulario(5, "   \t\n   ", List.of(new DetalleOrdenCompraFormulario(10, 2, new BigDecimal("10.00"))));

            Integer idGenerado = servicio.guardar("admin", formConEspacios);

            assertEquals(77, idGenerado);
            ArgumentCaptor<String> captorObs = ArgumentCaptor.forClass(String.class);
            verify(ordenRepositorio).guardar(anyString(), eq(1), eq(5), eq(new BigDecimal("20.00")), captorObs.capture());
            assertNull(captorObs.getValue());
        }
    }

    // =========================================================================
    // BLOQUE 4: ValidacionDetallesYMedicamentos (6 pruebas)
    // =========================================================================
    @Nested
    @DisplayName("Bloque 4: Validación de Detalles y Medicamentos")
    class ValidacionDetallesYMedicamentos {

        @BeforeEach
        void prepararContextoValido() {
            var usuario = new Usuario(1, "Admin", "admin", "hash", "ADMINISTRADOR", true);
            var proveedor = new Proveedor(5, "20123456789", "Prov Activo", "123", "a@a.com", "Dir", true);
            when(usuarioRepositorio.buscarPorNombreUsuario("admin")).thenReturn(Optional.of(usuario));
            when(proveedorRepositorio.buscarPorId(5)).thenReturn(Optional.of(proveedor));
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando la lista de detalles es nula o vacía")
        void guardar_detallesNulosOVacios_lanzaIllegalArgumentException() {
            var formVacio = new OrdenCompraFormulario(5, "Obs", List.of());
            var formNulo = new OrdenCompraFormulario(5, "Obs", null);

            var exVacio = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", formVacio));
            var exNulo = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", formNulo));

            assertEquals("Agrega al menos un medicamento", exVacio.getMessage());
            assertEquals("Agrega al menos un medicamento", exNulo.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando un elemento de la lista es nulo (sin NPE)")
        void guardar_detalleElementoNulo_lanzaIllegalArgumentException() {
            List<DetalleOrdenCompraFormulario> detallesConNulo = Collections.singletonList(null);
            var form = new OrdenCompraFormulario(5, "Obs", detallesConNulo);

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", form));

            assertEquals("Selecciona un medicamento valido", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando idMedicamento es nulo o <= 0")
        void guardar_idMedicamentoNuloOMenorIgualACero_lanzaIllegalArgumentException() {
            var detNulo = new DetalleOrdenCompraFormulario(null, 5, new BigDecimal("10.00"));
            var detCero = new DetalleOrdenCompraFormulario(0, 5, new BigDecimal("10.00"));
            var detNegativo = new DetalleOrdenCompraFormulario(-2, 5, new BigDecimal("10.00"));

            var exNulo = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", new OrdenCompraFormulario(5, "Obs", List.of(detNulo))));
            var exCero = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", new OrdenCompraFormulario(5, "Obs", List.of(detCero))));
            var exNeg = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", new OrdenCompraFormulario(5, "Obs", List.of(detNegativo))));

            assertEquals("Selecciona un medicamento valido", exNulo.getMessage());
            assertEquals("Selecciona un medicamento valido", exCero.getMessage());
            assertEquals("Selecciona un medicamento valido", exNeg.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio, medicamentoRepositorio);
        }

        @Test
        @DisplayName("guardar: detecta medicamento repetido y lanza excepción sin consultar BD para el duplicado")
        void guardar_medicamentoRepetido_lanzaIllegalArgumentExceptionSinConsultarRepo() {
            var det1 = new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("10.00"));
            var det2 = new DetalleOrdenCompraFormulario(10, 2, new BigDecimal("10.00"));
            var form = new OrdenCompraFormulario(5, "Obs", List.of(det1, det2));

            var med1 = new Medicamento(10, "MED-10", "Med", "100mg", "Caja", "Unidad", new BigDecimal("10.00"), 5, true);
            when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(med1));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", form));

            assertEquals("No puedes repetir un medicamento en la orden", ex.getMessage());
            // Verificamos que medicamentoRepositorio no fue consultado 2 veces (solo a lo sumo 1 vez para el primer ítem, nunca para el repetido)
            verify(medicamentoRepositorio, atMostOnce()).buscarPorId(10);
            verifyNoInteractions(ordenRepositorio, detalleRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando el medicamento no existe")
        void guardar_medicamentoNoExiste_lanzaIllegalArgumentException() {
            var det = new DetalleOrdenCompraFormulario(999, 5, new BigDecimal("10.00"));
            var form = new OrdenCompraFormulario(5, "Obs", List.of(det));
            when(medicamentoRepositorio.buscarPorId(999)).thenReturn(Optional.empty());

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", form));

            assertEquals("Medicamento no encontrado", ex.getMessage());
            verify(medicamentoRepositorio, times(1)).buscarPorId(999);
            verifyNoInteractions(ordenRepositorio, detalleRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando el medicamento está inactivo")
        void guardar_medicamentoInactivo_lanzaIllegalArgumentException() {
            var det = new DetalleOrdenCompraFormulario(12, 5, new BigDecimal("10.00"));
            var form = new OrdenCompraFormulario(5, "Obs", List.of(det));
            var medInactivo = new Medicamento(12, "MED-12", "Inactivo", "50mg", "Caja", "Unidad", new BigDecimal("10.00"), 5, false);
            when(medicamentoRepositorio.buscarPorId(12)).thenReturn(Optional.of(medInactivo));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", form));

            assertEquals("La orden contiene un medicamento inactivo", ex.getMessage());
            verify(medicamentoRepositorio, times(1)).buscarPorId(12);
            verifyNoInteractions(ordenRepositorio, detalleRepositorio);
        }
    }

    // =========================================================================
    // BLOQUE 5: ValidacionCantidadesYLimitesMonetarios (5 pruebas)
    // =========================================================================
    @Nested
    @DisplayName("Bloque 5: Validación de Cantidades y Límites Monetarios")
    class ValidacionCantidadesYLimitesMonetarios {

        @BeforeEach
        void prepararContextoValido() {
            var usuario = new Usuario(1, "Admin", "admin", "hash", "ADMINISTRADOR", true);
            var proveedor = new Proveedor(5, "20123456789", "Prov Activo", "123", "a@a.com", "Dir", true);
            var medicamento = new Medicamento(10, "MED-10", "Med", "100mg", "Caja", "Unidad", new BigDecimal("10.00"), 5, true);

            when(usuarioRepositorio.buscarPorNombreUsuario("admin")).thenReturn(Optional.of(usuario));
            when(proveedorRepositorio.buscarPorId(5)).thenReturn(Optional.of(proveedor));
            when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(medicamento));
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando la cantidadSolicitada es nula o <= 0")
        void guardar_cantidadSolicitadaNulaOMenorIgualACero_lanzaIllegalArgumentException() {
            var detNulo = new DetalleOrdenCompraFormulario(10, null, new BigDecimal("10.00"));
            var detCero = new DetalleOrdenCompraFormulario(10, 0, new BigDecimal("10.00"));
            var detNeg = new DetalleOrdenCompraFormulario(10, -5, new BigDecimal("10.00"));

            var exNulo = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", new OrdenCompraFormulario(5, "Obs", List.of(detNulo))));
            var exCero = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", new OrdenCompraFormulario(5, "Obs", List.of(detCero))));
            var exNeg = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", new OrdenCompraFormulario(5, "Obs", List.of(detNeg))));

            assertEquals("La cantidad debe ser mayor que cero", exNulo.getMessage());
            assertEquals("La cantidad debe ser mayor que cero", exCero.getMessage());
            assertEquals("La cantidad debe ser mayor que cero", exNeg.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando el costoUnitario es nulo o negativo")
        void guardar_costoUnitarioNuloONegativo_lanzaIllegalArgumentException() {
            var detNulo = new DetalleOrdenCompraFormulario(10, 5, null);
            var detNeg = new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("-0.01"));

            var exNulo = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", new OrdenCompraFormulario(5, "Obs", List.of(detNulo))));
            var exNeg = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", new OrdenCompraFormulario(5, "Obs", List.of(detNeg))));

            assertEquals("El costo unitario no es valido", exNulo.getMessage());
            assertEquals("El costo unitario no es valido", exNeg.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio);
        }

        @Test
        @DisplayName("guardar: rechaza costo con más de 2 decimales significativos (ej. 10.555)")
        void guardar_costoUnitarioMasDeDosDecimalesSignificativos_lanzaIllegalArgumentException() {
            var detTresDecimales = new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("10.555"));
            var form = new OrdenCompraFormulario(5, "Obs", List.of(detTresDecimales));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", form));

            assertEquals("El costo unitario no es valido", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando el costoUnitario supera el límite máximo")
        void guardar_costoUnitarioExcedeLimiteMaximo_lanzaIllegalArgumentException() {
            // Límite permitido: 9999999999.99. Costo que lo excede: 10000000000.00
            var detExcedido = new DetalleOrdenCompraFormulario(10, 1, new BigDecimal("10000000000.00"));
            var form = new OrdenCompraFormulario(5, "Obs", List.of(detExcedido));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", form));

            assertEquals("El costo unitario no es valido", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio);
        }

        @Test
        @DisplayName("guardar: lanza IllegalArgumentException cuando el total acumulado supera el límite permitido")
        void guardar_totalAcumuladoExcedeLimiteMaximo_lanzaIllegalArgumentException() {
            // Costo individual menor al límite: 6000000000.00, pero multiplicado por 2 da 12000000000.00 > límite
            var detTotalExcedido = new DetalleOrdenCompraFormulario(10, 2, new BigDecimal("6000000000.00"));
            var form = new OrdenCompraFormulario(5, "Obs", List.of(detTotalExcedido));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.guardar("admin", form));

            assertEquals("El total supera el importe permitido", ex.getMessage());
            verifyNoInteractions(ordenRepositorio, detalleRepositorio);
        }
    }

    // =========================================================================
    // BLOQUE 6: PersistenciaYEncadenamiento (3 pruebas exhaustivas)
    // =========================================================================
    @Nested
    @DisplayName("Bloque 6: Persistencia y Encadenamiento")
    class PersistenciaYEncadenamiento {

        @Test
        @DisplayName("guardar: persiste cabecera y detalle en orden estricto, sanitiza observaciones y encadena ID")
        void guardar_ordenConUnDetalle_persisteCabeceraYDetalleConInOrder() {
            var usuario = new Usuario(3, "Usuario Compras", "compras", "hash", "COMPRAS", true);
            var proveedor = new Proveedor(7, "20123456789", "Distribuidora Lima", "123456", "lima@prov.com", "Av. Principal", true);
            var medicamento = new Medicamento(12, "MED-12", "Amoxicilina 500mg", "500mg", "Caja", "Unidad", new BigDecimal("15.50"), 20, true);

            when(usuarioRepositorio.buscarPorNombreUsuario("compras")).thenReturn(Optional.of(usuario));
            when(proveedorRepositorio.buscarPorId(7)).thenReturn(Optional.of(proveedor));
            when(medicamentoRepositorio.buscarPorId(12)).thenReturn(Optional.of(medicamento));
            when(ordenRepositorio.guardar(anyString(), eq(3), eq(7), eq(new BigDecimal("155.00")), eq("Entrega urgente en almacen principal"))).thenReturn(88);

            var detalle = new DetalleOrdenCompraFormulario(12, 10, new BigDecimal("15.50"));
            var form = new OrdenCompraFormulario(7, "   Entrega urgente en almacen principal   ", List.of(detalle));

            Integer idOrdenGenerado = servicio.guardar("compras", form);

            assertEquals(88, idOrdenGenerado);

            InOrder inOrder = inOrder(ordenRepositorio, detalleRepositorio);
            inOrder.verify(ordenRepositorio).guardar(
                    matches("^OC-[a-f0-9]{27}$"),
                    eq(3),
                    eq(7),
                    eq(new BigDecimal("155.00")),
                    eq("Entrega urgente en almacen principal")
            );
            inOrder.verify(detalleRepositorio).guardar(
                    eq(88),
                    eq(12),
                    eq(10),
                    eq(new BigDecimal("15.50"))
            );
            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("guardar: acepta costo 0.00 y ceros decimales finales (12.5000), normalizando el total a escala 2")
        void guardar_ordenConMultiplesDetallesYCerosDecimales_calculaTotalYPersisteEnSecuencia() {
            var usuario = new Usuario(1, "Admin", "admin", "hash", "ADMINISTRADOR", true);
            var proveedor = new Proveedor(4, "20987654321", "Farmacéutica del Sur", "654321", "sur@prov.com", "Jr. Comercio", true);
            var med1 = new Medicamento(10, "MED-10", "Paracetamol", "500mg", "Caja", "Unidad", new BigDecimal("2.00"), 10, true);
            var med2 = new Medicamento(20, "MED-20", "Alcohol medicinal", "70%", "Frasco", "Unidad", new BigDecimal("12.50"), 15, true);

            when(usuarioRepositorio.buscarPorNombreUsuario("admin")).thenReturn(Optional.of(usuario));
            when(proveedorRepositorio.buscarPorId(4)).thenReturn(Optional.of(proveedor));
            when(medicamentoRepositorio.buscarPorId(10)).thenReturn(Optional.of(med1));
            when(medicamentoRepositorio.buscarPorId(20)).thenReturn(Optional.of(med2));

            // Detalle 1: costo 0.00 * 5 = 0.00 (muestra médica/bonificación aceptada)
            // Detalle 2: costo 12.5000 * 2 = 25.00 (ceros finales aceptados)
            // Total: 25.00 con escala 2
            when(ordenRepositorio.guardar(anyString(), eq(1), eq(4), eq(new BigDecimal("25.00")), isNull())).thenReturn(105);

            var det1 = new DetalleOrdenCompraFormulario(10, 5, new BigDecimal("0.00"));
            var det2 = new DetalleOrdenCompraFormulario(20, 2, new BigDecimal("12.5000"));
            var form = new OrdenCompraFormulario(4, null, List.of(det1, det2));

            Integer idGenerado = servicio.guardar("admin", form);

            assertEquals(105, idGenerado);

            InOrder inOrder = inOrder(ordenRepositorio, detalleRepositorio);
            inOrder.verify(ordenRepositorio).guardar(
                    matches("^OC-[a-f0-9]{27}$"),
                    eq(1),
                    eq(4),
                    eq(new BigDecimal("25.00")),
                    isNull()
            );
            inOrder.verify(detalleRepositorio).guardar(
                    eq(105),
                    eq(10),
                    eq(5),
                    eq(new BigDecimal("0.00"))
            );
            inOrder.verify(detalleRepositorio).guardar(
                    eq(105),
                    eq(20),
                    eq(2),
                    eq(new BigDecimal("12.5000"))
            );
            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("guardar: persiste exitosamente cuando el total es exactamente igual al límite 9999999999.99")
        void guardar_totalExactamenteIgualAlLimite_persisteExitosamente() {
            var usuario = new Usuario(1, "Admin", "admin", "hash", "ADMINISTRADOR", true);
            var proveedor = new Proveedor(5, "20123456789", "Proveedor Central", "123", "c@c.com", "Dir", true);
            var med = new Medicamento(30, "MED-30", "Equipo Médico Especializado", "Unidad", "Caja", "Unidad", new BigDecimal("9999999999.99"), 1, true);

            when(usuarioRepositorio.buscarPorNombreUsuario("admin")).thenReturn(Optional.of(usuario));
            when(proveedorRepositorio.buscarPorId(5)).thenReturn(Optional.of(proveedor));
            when(medicamentoRepositorio.buscarPorId(30)).thenReturn(Optional.of(med));

            BigDecimal totalLimiteExacto = new BigDecimal("9999999999.99");
            when(ordenRepositorio.guardar(anyString(), eq(1), eq(5), eq(totalLimiteExacto), eq("Orden al límite"))).thenReturn(999);

            var detalle = new DetalleOrdenCompraFormulario(30, 1, totalLimiteExacto);
            var form = new OrdenCompraFormulario(5, "Orden al límite", List.of(detalle));

            Integer idGenerado = servicio.guardar("admin", form);

            assertEquals(999, idGenerado);

            InOrder inOrder = inOrder(ordenRepositorio, detalleRepositorio);
            inOrder.verify(ordenRepositorio).guardar(
                    matches("^OC-[a-f0-9]{27}$"),
                    eq(1),
                    eq(5),
                    eq(totalLimiteExacto),
                    eq("Orden al límite")
            );
            inOrder.verify(detalleRepositorio).guardar(
                    eq(999),
                    eq(30),
                    eq(1),
                    eq(totalLimiteExacto)
            );
            inOrder.verifyNoMoreInteractions();
        }
    }
}
