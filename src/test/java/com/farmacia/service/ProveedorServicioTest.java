package com.farmacia.service;

import com.farmacia.dto.ProveedorFormulario;
import com.farmacia.model.Proveedor;
import com.farmacia.repository.ProveedorRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProveedorServicioTest {

    @Mock
    private ProveedorRepositorio repositorio;

    private ProveedorServicio servicio;

    @BeforeEach
    void setUp() {
        servicio = new ProveedorServicio(repositorio);
    }

    private Proveedor crearProveedor(Integer id, String ruc, String razonSocial, boolean activo) {
        return new Proveedor(
                id,
                ruc,
                razonSocial,
                "014567890",
                "contacto@proveedor.pe",
                "Av. Principal 123",
                activo);
    }

    // =========================================================================
    // 1. Pruebas de listarActivos()
    // =========================================================================
    @Nested
    @DisplayName("Pruebas de listarActivos")
    class ListarActivos {

        @Test
        @DisplayName("Delega en repositorio.listarActivos y retorna la lista obtenida")
        void listarActivos_conProveedores_retornaListaDeRepositorio() {
            // Arrange
            var prov1 = crearProveedor(1, "20100000001", "Drogueria Central SAC", true);
            var prov2 = crearProveedor(2, "20100000002", "Distribuidora Lima SA", true);
            when(repositorio.listarActivos()).thenReturn(List.of(prov1, prov2));

            // Act
            List<Proveedor> resultado = servicio.listarActivos();

            // Assert
            assertNotNull(resultado);
            assertEquals(2, resultado.size());
            assertEquals(prov1, resultado.get(0));
            assertEquals(prov2, resultado.get(1));
            verify(repositorio).listarActivos();
        }

        @Test
        @DisplayName("Retorna lista vacía cuando el repositorio no tiene proveedores activos")
        void listarActivos_sinProveedores_retornaListaVacia() {
            // Arrange
            when(repositorio.listarActivos()).thenReturn(List.of());

            // Act
            List<Proveedor> resultado = servicio.listarActivos();

            // Assert
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(repositorio).listarActivos();
        }
    }

    // =========================================================================
    // 2. Pruebas de guardar(ProveedorFormulario) — Sanitización de datos
    // =========================================================================
    @Nested
    @DisplayName("Pruebas de guardar y sanitización")
    class GuardarYSanitizacion {

        @Test
        @DisplayName("Recorta espacios exteriores en RUC y razón social antes de delegar a guardar")
        void guardar_conEspaciosExterioresEnObligatorios_recortaEspacios() {
            // Arrange
            var formulario = new ProveedorFormulario(
                    "  20123456789  ",
                    "   Drogueria San Martin SAC   ",
                    "014567890",
                    "info@sanmartin.pe",
                    "Av. Grau 456");
            var captor = ArgumentCaptor.forClass(ProveedorFormulario.class);

            // Act
            servicio.guardar(formulario);

            // Assert
            verify(repositorio).guardar(captor.capture());
            ProveedorFormulario sanitizado = captor.getValue();
            assertEquals("20123456789", sanitizado.ruc());
            assertEquals("Drogueria San Martin SAC", sanitizado.razonSocial());
            assertEquals("014567890", sanitizado.telefono());
            assertEquals("info@sanmartin.pe", sanitizado.correo());
            assertEquals("Av. Grau 456", sanitizado.direccion());
        }

        @Test
        @DisplayName("Recorta espacios exteriores en campos opcionales poblados")
        void guardar_conEspaciosExterioresEnOpcionales_recortaEspacios() {
            // Arrange
            var formulario = new ProveedorFormulario(
                    "20123456789",
                    "Drogueria Central SAC",
                    "  987654321  ",
                    "  ventas@central.pe  ",
                    "  Jr. Los Pinos 789  ");
            var captor = ArgumentCaptor.forClass(ProveedorFormulario.class);

            // Act
            servicio.guardar(formulario);

            // Assert
            verify(repositorio).guardar(captor.capture());
            ProveedorFormulario sanitizado = captor.getValue();
            assertEquals("987654321", sanitizado.telefono());
            assertEquals("ventas@central.pe", sanitizado.correo());
            assertEquals("Jr. Los Pinos 789", sanitizado.direccion());
        }

        @Test
        @DisplayName("Convierte campos opcionales vacíos a null")
        void guardar_conCamposOpcionalesVacios_convierteANull() {
            // Arrange
            var formulario = new ProveedorFormulario(
                    "20123456789",
                    "Drogueria Central SAC",
                    "",
                    "",
                    "");
            var captor = ArgumentCaptor.forClass(ProveedorFormulario.class);

            // Act
            servicio.guardar(formulario);

            // Assert
            verify(repositorio).guardar(captor.capture());
            ProveedorFormulario sanitizado = captor.getValue();
            assertNull(sanitizado.telefono());
            assertNull(sanitizado.correo());
            assertNull(sanitizado.direccion());
        }

        @Test
        @DisplayName("Convierte campos opcionales compuestos solo por espacios a null")
        void guardar_conCamposOpcionalesSoloEspacios_convierteANull() {
            // Arrange
            var formulario = new ProveedorFormulario(
                    "20123456789",
                    "Drogueria Central SAC",
                    "    ",
                    " \t \n ",
                    "   ");
            var captor = ArgumentCaptor.forClass(ProveedorFormulario.class);

            // Act
            servicio.guardar(formulario);

            // Assert
            verify(repositorio).guardar(captor.capture());
            ProveedorFormulario sanitizado = captor.getValue();
            assertNull(sanitizado.telefono());
            assertNull(sanitizado.correo());
            assertNull(sanitizado.direccion());
        }

        @Test
        @DisplayName("Conserva valores null en campos opcionales originalmente nulos")
        void guardar_conCamposOpcionalesNulos_conservaNull() {
            // Arrange
            var formulario = new ProveedorFormulario(
                    "20123456789",
                    "Drogueria Central SAC",
                    null,
                    null,
                    null);
            var captor = ArgumentCaptor.forClass(ProveedorFormulario.class);

            // Act
            servicio.guardar(formulario);

            // Assert
            verify(repositorio).guardar(captor.capture());
            ProveedorFormulario sanitizado = captor.getValue();
            assertEquals("20123456789", sanitizado.ruc());
            assertEquals("Drogueria Central SAC", sanitizado.razonSocial());
            assertNull(sanitizado.telefono());
            assertNull(sanitizado.correo());
            assertNull(sanitizado.direccion());
        }
    }

    // =========================================================================
    // 3. Pruebas de buscarPorId(Integer)
    // =========================================================================
    @Nested
    @DisplayName("Pruebas de buscarPorId")
    class BuscarPorId {

        @Test
        @DisplayName("Retorna Optional con proveedor cuando el ID existe")
        void buscarPorId_conIdExistente_retornaOptionalConProveedor() {
            // Arrange
            var proveedor = crearProveedor(10, "20100000010", "Proveedor Alfa", true);
            when(repositorio.buscarPorId(10)).thenReturn(Optional.of(proveedor));

            // Act
            Optional<Proveedor> resultado = servicio.buscarPorId(10);

            // Assert
            assertTrue(resultado.isPresent());
            assertEquals(proveedor, resultado.get());
            verify(repositorio).buscarPorId(10);
        }

        @Test
        @DisplayName("Retorna Optional.empty cuando el ID no existe en el repositorio")
        void buscarPorId_conIdInexistente_retornaOptionalVacio() {
            // Arrange
            when(repositorio.buscarPorId(999)).thenReturn(Optional.empty());

            // Act
            Optional<Proveedor> resultado = servicio.buscarPorId(999);

            // Assert
            assertTrue(resultado.isEmpty());
            verify(repositorio).buscarPorId(999);
        }
    }

    // =========================================================================
    // 4. Pruebas de actualizar(Integer, ProveedorFormulario)
    // =========================================================================
    @Nested
    @DisplayName("Pruebas de actualizar")
    class Actualizar {

        @Test
        @DisplayName("Retorna true cuando el repositorio actualiza exactamente una fila")
        void actualizar_cuandoActualizaUnaFila_retornaTrue() {
            // Arrange
            var formulario = new ProveedorFormulario(
                    "20100000001",
                    "Drogueria Modificada SAC",
                    "014567899",
                    "nuevo@correo.pe",
                    "Nueva Direccion 123");
            when(repositorio.actualizar(eq(1), any(ProveedorFormulario.class))).thenReturn(1);

            // Act
            boolean resultado = servicio.actualizar(1, formulario);

            // Assert
            assertTrue(resultado);
            verify(repositorio).actualizar(eq(1), any(ProveedorFormulario.class));
        }

        @Test
        @DisplayName("Retorna false cuando el repositorio actualiza cero filas (proveedor inexistente)")
        void actualizar_cuandoActualizaCeroFilas_retornaFalse() {
            // Arrange
            var formulario = new ProveedorFormulario(
                    "20100000001",
                    "Drogueria Modificada SAC",
                    null,
                    null,
                    null);
            when(repositorio.actualizar(eq(999), any(ProveedorFormulario.class))).thenReturn(0);

            // Act
            boolean resultado = servicio.actualizar(999, formulario);

            // Assert
            assertFalse(resultado);
            verify(repositorio).actualizar(eq(999), any(ProveedorFormulario.class));
        }

        @Test
        @DisplayName("Sanitiza datos obligatorios y normaliza opcionales vacíos al actualizar")
        void actualizar_sanitizaDatosYNormalizaOpcionales() {
            // Arrange
            var formulario = new ProveedorFormulario(
                    "   20100000001   ",
                    "   Razon Social Modificada   ",
                    "   ",
                    "   correo@test.pe   ",
                    "");
            var captor = ArgumentCaptor.forClass(ProveedorFormulario.class);
            when(repositorio.actualizar(eq(5), any(ProveedorFormulario.class))).thenReturn(1);

            // Act
            boolean resultado = servicio.actualizar(5, formulario);

            // Assert
            assertTrue(resultado);
            verify(repositorio).actualizar(eq(5), captor.capture());
            ProveedorFormulario sanitizado = captor.getValue();
            assertEquals("20100000001", sanitizado.ruc());
            assertEquals("Razon Social Modificada", sanitizado.razonSocial());
            assertNull(sanitizado.telefono());
            assertEquals("correo@test.pe", sanitizado.correo());
            assertNull(sanitizado.direccion());
        }
    }

    // =========================================================================
    // 5. Pruebas de desactivar(Integer)
    // =========================================================================
    @Nested
    @DisplayName("Pruebas de desactivar")
    class Desactivar {

        @Test
        @DisplayName("Retorna true cuando el repositorio desactiva exitosamente una fila")
        void desactivar_cuandoModificaUnaFila_retornaTrue() {
            // Arrange
            when(repositorio.desactivar(1)).thenReturn(1);

            // Act
            boolean resultado = servicio.desactivar(1);

            // Assert
            assertTrue(resultado);
            verify(repositorio).desactivar(1);
        }

        @Test
        @DisplayName("Retorna false cuando el repositorio no encuentra la fila a desactivar")
        void desactivar_cuandoModificaCeroFilas_retornaFalse() {
            // Arrange
            when(repositorio.desactivar(999)).thenReturn(0);

            // Act
            boolean resultado = servicio.desactivar(999);

            // Assert
            assertFalse(resultado);
            verify(repositorio).desactivar(999);
        }
    }

    // =========================================================================
    // 6. Pruebas de activar(Integer)
    // =========================================================================
    @Nested
    @DisplayName("Pruebas de activar")
    class Activar {

        @Test
        @DisplayName("Retorna true cuando el repositorio activa exitosamente una fila")
        void activar_cuandoModificaUnaFila_retornaTrue() {
            // Arrange
            when(repositorio.activar(1)).thenReturn(1);

            // Act
            boolean resultado = servicio.activar(1);

            // Assert
            assertTrue(resultado);
            verify(repositorio).activar(1);
        }

        @Test
        @DisplayName("Retorna false cuando el repositorio no encuentra la fila a activar")
        void activar_cuandoModificaCeroFilas_retornaFalse() {
            // Arrange
            when(repositorio.activar(999)).thenReturn(0);

            // Act
            boolean resultado = servicio.activar(999);

            // Assert
            assertFalse(resultado);
            verify(repositorio).activar(999);
        }
    }

    // =========================================================================
    // 7. Pruebas de listarPorEstado(boolean)
    // =========================================================================
    @Nested
    @DisplayName("Pruebas de listarPorEstado")
    class ListarPorEstado {

        @Test
        @DisplayName("Consulta proveedores con activo = true")
        void listarPorEstado_conActivoTrue_retornaListaDeRepositorio() {
            // Arrange
            var provActivo = crearProveedor(1, "20100000001", "Drogueria Activa", true);
            when(repositorio.listarPorEstado(true)).thenReturn(List.of(provActivo));

            // Act
            List<Proveedor> resultado = servicio.listarPorEstado(true);

            // Assert
            assertEquals(1, resultado.size());
            assertEquals(provActivo, resultado.get(0));
            verify(repositorio).listarPorEstado(true);
        }

        @Test
        @DisplayName("Consulta proveedores con activo = false")
        void listarPorEstado_conActivoFalse_retornaListaDeRepositorio() {
            // Arrange
            var provInactivo = crearProveedor(2, "20100000002", "Drogueria Inactiva", false);
            when(repositorio.listarPorEstado(false)).thenReturn(List.of(provInactivo));

            // Act
            List<Proveedor> resultado = servicio.listarPorEstado(false);

            // Assert
            assertEquals(1, resultado.size());
            assertEquals(provInactivo, resultado.get(0));
            verify(repositorio).listarPorEstado(false);
        }

        @Test
        @DisplayName("Retorna lista vacía cuando no existen proveedores en el estado solicitado")
        void listarPorEstado_sinResultados_retornaListaVacia() {
            // Arrange
            when(repositorio.listarPorEstado(false)).thenReturn(List.of());

            // Act
            List<Proveedor> resultado = servicio.listarPorEstado(false);

            // Assert
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
            verify(repositorio).listarPorEstado(false);
        }
    }

    // =========================================================================
    // 8. Pruebas de buscarPorEstado(String, boolean)
    // =========================================================================
    @Nested
    @DisplayName("Pruebas de buscarPorEstado")
    class BuscarPorEstado {

        @Test
        @DisplayName("Normaliza texto con strip y propaga parámetros al repositorio")
        void buscarPorEstado_conTextoConEspacios_aplicaStripYPropaga() {
            // Arrange
            var prov = crearProveedor(1, "20100000001", "Drogueria Central SAC", true);
            when(repositorio.buscarPorEstado("drogueria", true)).thenReturn(List.of(prov));

            // Act
            List<Proveedor> resultado = servicio.buscarPorEstado("   drogueria   ", true);

            // Assert
            assertEquals(1, resultado.size());
            assertEquals(prov, resultado.get(0));
            verify(repositorio).buscarPorEstado("drogueria", true);
        }

        @Test
        @DisplayName("Normaliza texto nulo convirtiéndolo a cadena vacía")
        void buscarPorEstado_conTextoNulo_convierteACadenaVacia() {
            // Arrange
            var prov = crearProveedor(1, "20100000001", "Drogueria Central SAC", true);
            when(repositorio.buscarPorEstado("", true)).thenReturn(List.of(prov));

            // Act
            List<Proveedor> resultado = servicio.buscarPorEstado(null, true);

            // Assert
            assertEquals(1, resultado.size());
            verify(repositorio).buscarPorEstado("", true);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        @DisplayName("Normaliza cadenas nulas, vacías o de puros espacios a cadena vacía")
        void buscarPorEstado_conCadenasBlancasONulas_normalizaACadenaVacia(String textoBusqueda) {
            // Arrange
            when(repositorio.buscarPorEstado("", false)).thenReturn(List.of());

            // Act
            List<Proveedor> resultado = servicio.buscarPorEstado(textoBusqueda, false);

            // Assert
            assertNotNull(resultado);
            verify(repositorio).buscarPorEstado("", false);
        }

        @Test
        @DisplayName("Propaga correctamente el indicador activo = false con texto de búsqueda")
        void buscarPorEstado_conEstadoInactivo_propagaFiltrosCorrectos() {
            // Arrange
            var provInactivo = crearProveedor(3, "20999999999", "Farmaceutica Inactiva SAC", false);
            when(repositorio.buscarPorEstado("farmaceutica", false)).thenReturn(List.of(provInactivo));

            // Act
            List<Proveedor> resultado = servicio.buscarPorEstado("farmaceutica", false);

            // Assert
            assertEquals(1, resultado.size());
            assertEquals(provInactivo, resultado.get(0));
            verify(repositorio).buscarPorEstado("farmaceutica", false);
        }
    }
}
