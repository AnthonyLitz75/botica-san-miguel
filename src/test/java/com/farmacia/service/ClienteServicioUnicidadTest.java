package com.farmacia.service;

import com.farmacia.dto.ClienteFormulario;
import com.farmacia.exception.ValidacionContactoException;
import com.farmacia.model.Cliente;
import com.farmacia.repository.ClienteRepositorio;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServicioUnicidadTest {

    @Mock
    private ClienteRepositorio repositorio;

    @Mock
    private AuditoriaServicio auditoria;

    private ClienteServicio servicio;

    @BeforeEach
    void setUp() {
        servicio = new ClienteServicio(repositorio, auditoria);
    }

    @Test
    @DisplayName("a) Crear con correo ya existente (distinta capitalizacion y espacios) retorna error de correo")
    void crearConCorreoDuplicadoLanzaErrorPorCampo() {
        when(repositorio.existeDocumento("DNI", "12345678", null)).thenReturn(false);
        when(repositorio.buscarTitularPorCorreo("juan@gmail.com", null))
                .thenReturn(Optional.of("Juan Pérez Quispe"));

        ClienteFormulario form = new ClienteFormulario(
                "DNI", "12345678", "Nuevo Cliente", "999888777", "  Juan@Gmail.com ");

        assertThatThrownBy(() -> servicio.guardar(form))
                .isInstanceOf(ValidacionContactoException.class)
                .satisfies(ex -> {
                    ValidacionContactoException e = (ValidacionContactoException) ex;
                    assertThat(e.tieneError("correo")).isTrue();
                    assertThat(e.getError("correo")).isEqualTo("Este correo ya está registrado a nombre de Juan Pérez Quispe.");
                    assertThat(e.tieneError("telefono")).isFalse();
                });
    }

    @Test
    @DisplayName("b) Crear con telefono ya existente (con prefijo +51 y espacios) retorna error de telefono")
    void crearConTelefonoDuplicadoLanzaErrorPorCampo() {
        when(repositorio.existeDocumento("DNI", "12345678", null)).thenReturn(false);
        when(repositorio.buscarTitularPorTelefono("958145341", null))
                .thenReturn(Optional.of("Makaay Anthony Tanta Ramos"));

        ClienteFormulario form = new ClienteFormulario(
                "DNI", "12345678", "Nuevo Cliente", "+51 958 145 341", "nuevo@correo.com");

        assertThatThrownBy(() -> servicio.guardar(form))
                .isInstanceOf(ValidacionContactoException.class)
                .satisfies(ex -> {
                    ValidacionContactoException e = (ValidacionContactoException) ex;
                    assertThat(e.tieneError("telefono")).isTrue();
                    assertThat(e.getError("telefono")).isEqualTo("Este teléfono ya está registrado a nombre de Makaay Anthony Tanta Ramos.");
                    assertThat(e.tieneError("correo")).isFalse();
                });
    }

    @Test
    @DisplayName("c) Crear con ambos datos duplicados retorna los dos errores a la vez")
    void crearConAmbosDuplicadosRetornaDosErroresALaVez() {
        when(repositorio.existeDocumento("DNI", "12345678", null)).thenReturn(false);
        when(repositorio.buscarTitularPorCorreo("juan@gmail.com", null))
                .thenReturn(Optional.of("Juan Pérez Quispe"));
        when(repositorio.buscarTitularPorTelefono("958145341", null))
                .thenReturn(Optional.of("Makaay Anthony Tanta Ramos"));

        ClienteFormulario form = new ClienteFormulario(
                "DNI", "12345678", "Nuevo Cliente", "958-145-341", "juan@gmail.com");

        assertThatThrownBy(() -> servicio.guardar(form))
                .isInstanceOf(ValidacionContactoException.class)
                .satisfies(ex -> {
                    ValidacionContactoException e = (ValidacionContactoException) ex;
                    assertThat(e.getErroresPorCampo()).hasSize(2);
                    assertThat(e.getError("correo")).isEqualTo("Este correo ya está registrado a nombre de Juan Pérez Quispe.");
                    assertThat(e.getError("telefono")).isEqualTo("Este teléfono ya está registrado a nombre de Makaay Anthony Tanta Ramos.");
                });
    }

    @Test
    @DisplayName("d) Editar un registro sin cambiar su correo/telefono propio es exitoso (excluye propio id)")
    void editarRegistroSinCambiarContactoPropioEsExitoso() {
        Cliente clienteExistente = new Cliente(
                5, "DNI", "12345678", "Cliente Existente", "958145341", "cliente@correo.com");
        when(repositorio.buscarPorId(5)).thenReturn(Optional.of(clienteExistente));
        when(repositorio.existeDocumento("DNI", "12345678", 5)).thenReturn(false);
        when(repositorio.buscarTitularPorCorreo("cliente@correo.com", 5)).thenReturn(Optional.empty());
        when(repositorio.buscarTitularPorTelefono("958145341", 5)).thenReturn(Optional.empty());
        when(repositorio.actualizar(eq(5), any())).thenReturn(1);

        ClienteFormulario form = new ClienteFormulario(
                "DNI", "12345678", "Cliente Existente Modificado", " 958145341 ", " CLIENTE@correo.com ");

        boolean resultado = servicio.actualizar(5, form);
        assertThat(resultado).isTrue();
    }

    @Test
    @DisplayName("e) Editar para tomar el valor de OTRO registro retorna error")
    void editarParaTomarValorDeOtroRegistroLanzaError() {
        Cliente clienteExistente = new Cliente(
                5, "DNI", "12345678", "Cliente 5", "999111222", "cliente5@correo.com");
        when(repositorio.buscarPorId(5)).thenReturn(Optional.of(clienteExistente));
        when(repositorio.existeDocumento("DNI", "12345678", 5)).thenReturn(false);
        when(repositorio.buscarTitularPorTelefono("958145341", 5))
                .thenReturn(Optional.of("Makaay Anthony Tanta Ramos"));

        ClienteFormulario form = new ClienteFormulario(
                "DNI", "12345678", "Cliente 5", "+51 958 145 341", "cliente5@correo.com");

        assertThatThrownBy(() -> servicio.actualizar(5, form))
                .isInstanceOf(ValidacionContactoException.class)
                .satisfies(ex -> {
                    ValidacionContactoException e = (ValidacionContactoException) ex;
                    assertThat(e.tieneError("telefono")).isTrue();
                    assertThat(e.getError("telefono")).isEqualTo("Este teléfono ya está registrado a nombre de Makaay Anthony Tanta Ramos.");
                });
    }

    @Test
    @DisplayName("f) Varios registros con correo/telefono vacio o nulo son permitidos")
    void registrosConContactoVacioSonPermitidos() {
        when(repositorio.existeDocumento("DNI", "12345678", null)).thenReturn(false);
        when(repositorio.guardar(any())).thenReturn(10);

        ClienteFormulario formVacio = new ClienteFormulario(
                "DNI", "12345678", "Cliente Sin Contacto", "   ", "");

        Integer id = servicio.guardar(formVacio);
        assertThat(id).isEqualTo(10);

        ArgumentCaptor<ClienteFormulario> captor = ArgumentCaptor.forClass(ClienteFormulario.class);
        verify(repositorio).guardar(captor.capture());
        assertThat(captor.getValue().telefono()).isNull();
        assertThat(captor.getValue().correo()).isNull();
    }

    @Test
    @DisplayName("g) El valor se guarda estrictamente normalizado en el repositorio")
    void guardarGuardaValoresNormalizados() {
        when(repositorio.existeDocumento("DNI", "12345678", null)).thenReturn(false);
        when(repositorio.buscarTitularPorCorreo("juan@gmail.com", null)).thenReturn(Optional.empty());
        when(repositorio.buscarTitularPorTelefono("958145341", null)).thenReturn(Optional.empty());
        when(repositorio.guardar(any())).thenReturn(11);

        ClienteFormulario form = new ClienteFormulario(
                "DNI", "12345678", "Juan Pérez", " +51 958 145 341 ", " Juan@Gmail.Com ");

        servicio.guardar(form);

        ArgumentCaptor<ClienteFormulario> captor = ArgumentCaptor.forClass(ClienteFormulario.class);
        verify(repositorio).guardar(captor.capture());
        assertThat(captor.getValue().telefono()).isEqualTo("958145341");
        assertThat(captor.getValue().correo()).isEqualTo("juan@gmail.com");
    }

    @Test
    @DisplayName("Traduccion de excepcion de indice unico a error por campo ante concurrencia")
    void traducirExcepcionIndiceUnique() {
        when(repositorio.existeDocumento("DNI", "12345678", null)).thenReturn(false);
        when(repositorio.buscarTitularPorCorreo("juan@gmail.com", null))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("Cliente Concurrente"));
        when(repositorio.buscarTitularPorTelefono("958145341", null)).thenReturn(Optional.empty());
        when(repositorio.guardar(any())).thenThrow(new DuplicateKeyException(
                "ERROR: duplicate key value violates unique constraint \"uq_cliente_correo\""));

        ClienteFormulario form = new ClienteFormulario(
                "DNI", "12345678", "Juan Pérez", "958145341", "juan@gmail.com");

        assertThatThrownBy(() -> servicio.guardar(form))
                .isInstanceOf(ValidacionContactoException.class)
                .satisfies(ex -> {
                    ValidacionContactoException e = (ValidacionContactoException) ex;
                    assertThat(e.tieneError("correo")).isTrue();
                    assertThat(e.getError("correo")).isEqualTo("Este correo ya está registrado a nombre de Cliente Concurrente.");
                });
    }
}
