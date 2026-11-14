package com.farmacia.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.exception.RecursoNoEncontradoException;
import com.farmacia.exception.StockInsuficienteException;

class GlobalRestExceptionHandlerTest {

    private final GlobalRestExceptionHandler handler = new GlobalRestExceptionHandler();

    @Test
    @DisplayName("ReglaNegocioException genera ProblemDetail con estado 400 y propiedades de compatibilidad")
    void manejaReglaNegocio() {
        var ex = new StockInsuficienteException("Stock agotado para el producto");

        ResponseEntity<ProblemDetail> respuesta = handler.manejarReglaNegocio(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getDetail()).isEqualTo("Stock agotado para el producto");
        assertThat(respuesta.getBody().getProperties()).containsEntry("mensaje", "Stock agotado para el producto");
        assertThat(respuesta.getBody().getProperties()).containsEntry("error", "Stock agotado para el producto");
    }

    @Test
    @DisplayName("IllegalArgumentException genera ProblemDetail con estado 400")
    void manejaArgumentoInvalido() {
        var ex = new IllegalArgumentException("Parámetro inválido");

        ResponseEntity<ProblemDetail> respuesta = handler.manejarArgumentoInvalido(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getDetail()).isEqualTo("Parámetro inválido");
        assertThat(respuesta.getBody().getProperties()).containsEntry("mensaje", "Parámetro inválido");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException mapea campos y primer error en ProblemDetail")
    void manejaValidacion() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError errorCodigo = new FieldError("form", "codigo", "El código es obligatorio");
        FieldError errorPrecio = new FieldError("form", "precio", "El precio debe ser positivo");

        when(bindingResult.getFieldErrors()).thenReturn(List.of(errorCodigo, errorPrecio));
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ProblemDetail> respuesta = handler.manejarValidacion(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getDetail()).isEqualTo("El código es obligatorio");
        assertThat(respuesta.getBody().getProperties()).containsKey("campos");
        assertThat(respuesta.getBody().getProperties()).containsEntry("error", "Datos inválidos");
    }

    @Test
    @DisplayName("DuplicateKeyException genera ProblemDetail con estado 409 Conflict")
    void manejaClaveDuplicada() {
        var ex = new DuplicateKeyException("llave duplicada en postgres");

        ResponseEntity<ProblemDetail> respuesta = handler.manejarClaveDuplicada(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getProperties()).containsKey("error");
    }

    @Test
    @DisplayName("RecursoNoEncontradoException genera ProblemDetail con estado 404")
    void manejaNoEncontrado() {
        var ex = new RecursoNoEncontradoException("Medicamento no hallado");

        ResponseEntity<ProblemDetail> respuesta = handler.manejarNoEncontrado(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getDetail()).isEqualTo("Medicamento no hallado");
    }

    @Test
    @DisplayName("ResponseStatusException preserva el código HTTP y mensaje de motivo")
    void manejaResponseStatus() {
        var ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Elemento ausente");

        ResponseEntity<ProblemDetail> respuesta = handler.manejarResponseStatus(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getDetail()).isEqualTo("Elemento ausente");
    }
}
