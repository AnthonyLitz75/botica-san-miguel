package com.farmacia.service;

import com.farmacia.dto.ProveedorFormulario;
import com.farmacia.exception.ValidacionContactoException;
import com.farmacia.model.Proveedor;
import com.farmacia.repository.ProveedorRepositorio;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProveedorServicioUnicidadTest {

    @Mock
    private ProveedorRepositorio repositorio;

    @Mock
    private AuditoriaServicio auditoria;

    private ProveedorServicio servicio;

    @BeforeEach
    void setUp() {
        servicio = new ProveedorServicio(repositorio, auditoria);
    }

    @Test
    @DisplayName("a) Crear proveedor con correo ya existente retorna error de correo")
    void crearProveedorConCorreoDuplicadoRetornaError() {
        when(repositorio.buscarTitularPorCorreo("ventas@medifarma.pe", null))
                .thenReturn(Optional.of("Laboratorios Medifarma S.A."));

        ProveedorFormulario form = new ProveedorFormulario(
                "20100070970", "Nuevo Proveedor", "014258900", "  Ventas@Medifarma.PE ", "Av. Principal 123");

        assertThatThrownBy(() -> servicio.guardar(form))
                .isInstanceOf(ValidacionContactoException.class)
                .satisfies(ex -> {
                    ValidacionContactoException e = (ValidacionContactoException) ex;
                    assertThat(e.tieneError("correo")).isTrue();
                    assertThat(e.getError("correo")).isEqualTo("Este correo ya está registrado a nombre de Laboratorios Medifarma S.A..");
                    assertThat(e.tieneError("telefono")).isFalse();
                });
    }

    @Test
    @DisplayName("b) Crear proveedor con telefono ya existente retorna error de telefono")
    void crearProveedorConTelefonoDuplicadoRetornaError() {
        when(repositorio.buscarTitularPorTelefono("999000111", null))
                .thenReturn(Optional.of("Distribuidora de prueba"));

        ProveedorFormulario form = new ProveedorFormulario(
                "20100070970", "Nuevo Proveedor", "+51 999 000 111", "nuevo@correo.pe", "Av. Principal 123");

        assertThatThrownBy(() -> servicio.guardar(form))
                .isInstanceOf(ValidacionContactoException.class)
                .satisfies(ex -> {
                    ValidacionContactoException e = (ValidacionContactoException) ex;
                    assertThat(e.tieneError("telefono")).isTrue();
                    assertThat(e.getError("telefono")).isEqualTo("Este teléfono ya está registrado a nombre de Distribuidora de prueba.");
                    assertThat(e.tieneError("correo")).isFalse();
                });
    }

    @Test
    @DisplayName("c) Crear proveedor con ambos duplicados retorna dos errores a la vez")
    void crearProveedorConAmbosDuplicadosRetornaDosErrores() {
        when(repositorio.buscarTitularPorCorreo("ventas@medifarma.pe", null))
                .thenReturn(Optional.of("Laboratorios Medifarma S.A."));
        when(repositorio.buscarTitularPorTelefono("999000111", null))
                .thenReturn(Optional.of("Distribuidora de prueba"));

        ProveedorFormulario form = new ProveedorFormulario(
                "20100070970", "Nuevo Proveedor", "999-000-111", "ventas@medifarma.pe", "Av. Principal 123");

        assertThatThrownBy(() -> servicio.guardar(form))
                .isInstanceOf(ValidacionContactoException.class)
                .satisfies(ex -> {
                    ValidacionContactoException e = (ValidacionContactoException) ex;
                    assertThat(e.getErroresPorCampo()).hasSize(2);
                    assertThat(e.getError("correo")).isEqualTo("Este correo ya está registrado a nombre de Laboratorios Medifarma S.A..");
                    assertThat(e.getError("telefono")).isEqualTo("Este teléfono ya está registrado a nombre de Distribuidora de prueba.");
                });
    }

    @Test
    @DisplayName("d) Editar proveedor sin cambiar su propio contacto es exitoso")
    void editarProveedorSinCambiarContactoPropioEsExitoso() {
        Proveedor provExistente = new Proveedor(
                3, "20100070970", "Medifarma S.A.", "014258900", "ventas@medifarma.pe", "Av. Lima 100", true);
        when(repositorio.buscarPorId(3)).thenReturn(Optional.of(provExistente));
        when(repositorio.buscarTitularPorCorreo("ventas@medifarma.pe", 3)).thenReturn(Optional.empty());
        when(repositorio.buscarTitularPorTelefono("014258900", 3)).thenReturn(Optional.empty());
        when(repositorio.actualizar(eq(3), any())).thenReturn(1);

        ProveedorFormulario form = new ProveedorFormulario(
                "20100070970", "Medifarma S.A. Actualizada", " (01) 425-8900 ", " VENTAS@medifarma.pe ", "Av. Lima 100");

        boolean actualizado = servicio.actualizar(3, form);
        assertThat(actualizado).isTrue();
    }

    @Test
    @DisplayName("e) Editar proveedor para tomar contacto de OTRO registro lanza error")
    void editarProveedorConContactoDeOtroLanzaError() {
        when(repositorio.buscarTitularPorCorreo("compras2@example.com", 3))
                .thenReturn(Optional.of("Distribuidora Demos Dos"));

        ProveedorFormulario form = new ProveedorFormulario(
                "20100070970", "Medifarma S.A.", "014258900", "compras2@example.com", "Av. Lima 100");

        assertThatThrownBy(() -> servicio.actualizar(3, form))
                .isInstanceOf(ValidacionContactoException.class)
                .satisfies(ex -> {
                    ValidacionContactoException e = (ValidacionContactoException) ex;
                    assertThat(e.tieneError("correo")).isTrue();
                    assertThat(e.getError("correo")).isEqualTo("Este correo ya está registrado a nombre de Distribuidora Demos Dos.");
                });
    }

    @Test
    @DisplayName("f) Varios proveedores con contacto vacio o nulo son permitidos")
    void proveedoresConContactoVacioSonPermitidos() {
        ProveedorFormulario formVacio = new ProveedorFormulario(
                "20100070970", "Proveedor Sin Contacto", "   ", "", "Direccion");

        servicio.guardar(formVacio);

        ArgumentCaptor<ProveedorFormulario> captor = ArgumentCaptor.forClass(ProveedorFormulario.class);
        verify(repositorio).guardar(captor.capture());
        assertThat(captor.getValue().telefono()).isNull();
        assertThat(captor.getValue().correo()).isNull();
    }

    @Test
    @DisplayName("g) El valor se guarda estrictamente normalizado en el repositorio")
    void guardarGuardaValoresNormalizados() {
        when(repositorio.buscarTitularPorCorreo("ventas@medifarma.pe", null)).thenReturn(Optional.empty());
        when(repositorio.buscarTitularPorTelefono("999000111", null)).thenReturn(Optional.empty());

        ProveedorFormulario form = new ProveedorFormulario(
                "20100070970", "Laboratorios Medifarma", " +51 999 000 111 ", " Ventas@Medifarma.Pe ", "Direccion");

        servicio.guardar(form);

        ArgumentCaptor<ProveedorFormulario> captor = ArgumentCaptor.forClass(ProveedorFormulario.class);
        verify(repositorio).guardar(captor.capture());
        assertThat(captor.getValue().telefono()).isEqualTo("999000111");
        assertThat(captor.getValue().correo()).isEqualTo("ventas@medifarma.pe");
    }

    @Test
    @DisplayName("Traduccion de excepcion de indice unico a error por campo ante concurrencia")
    void traducirExcepcionIndiceUnique() {
        when(repositorio.buscarTitularPorCorreo("ventas@medifarma.pe", null)).thenReturn(Optional.empty());
        when(repositorio.buscarTitularPorTelefono("999000111", null))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("Proveedor Concurrente"));
        org.mockito.Mockito.doThrow(new DuplicateKeyException(
                "ERROR: duplicate key value violates unique constraint \"uq_proveedor_telefono\""))
                .when(repositorio).guardar(any());

        ProveedorFormulario form = new ProveedorFormulario(
                "20100070970", "Medifarma", "999000111", "ventas@medifarma.pe", "Direccion");

        assertThatThrownBy(() -> servicio.guardar(form))
                .isInstanceOf(ValidacionContactoException.class)
                .satisfies(ex -> {
                    ValidacionContactoException e = (ValidacionContactoException) ex;
                    assertThat(e.tieneError("telefono")).isTrue();
                    assertThat(e.getError("telefono")).isEqualTo("Este teléfono ya está registrado a nombre de Proveedor Concurrente.");
                });
    }
}
