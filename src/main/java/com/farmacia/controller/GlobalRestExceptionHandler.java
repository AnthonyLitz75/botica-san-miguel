package com.farmacia.controller;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.exception.RecursoNoEncontradoException;
import com.farmacia.exception.ReglaNegocioException;

/**
 * Controlador global centralizado para el manejo estandarizado de excepciones en endpoints REST,
 * implementando el estándar RFC 9457 (Problem Details for HTTP APIs).
 * <p>
 * Mantiene compatibilidad hacia atrás mediante propiedades extendidas ("mensaje", "error", "campos")
 * para clientes web JavaScript existentes y contratos de pruebas.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalRestExceptionHandler {

    private static final URI TIPO_ERROR_VALIDACION = URI.create("urn:problem-type:validacion");
    private static final URI TIPO_ERROR_NEGOCIO = URI.create("urn:problem-type:regla-negocio");
    private static final URI TIPO_ERROR_DUPLICADO = URI.create("urn:problem-type:conflicto-duplicado");
    private static final URI TIPO_ERROR_NO_ENCONTRADO = URI.create("urn:problem-type:recurso-no-encontrado");

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ProblemDetail> manejarReglaNegocio(ReglaNegocioException excepcion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, excepcion.getMessage());
        problema.setTitle("Conflicto de Regla de Negocio");
        problema.setType(TIPO_ERROR_NEGOCIO);
        problema.setProperty("mensaje", excepcion.getMessage());
        problema.setProperty("error", excepcion.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problema);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> manejarArgumentoInvalido(IllegalArgumentException excepcion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, excepcion.getMessage());
        problema.setTitle("Argumento de Solicitud Inválido");
        problema.setType(TIPO_ERROR_NEGOCIO);
        problema.setProperty("mensaje", excepcion.getMessage());
        problema.setProperty("error", excepcion.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problema);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> manejarValidacion(MethodArgumentNotValidException excepcion) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : excepcion.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        String primerMensaje = excepcion.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Datos de entrada inválidos");

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, primerMensaje);
        problema.setTitle("Error de Validación de Formulario");
        problema.setType(TIPO_ERROR_VALIDACION);
        problema.setProperty("mensaje", primerMensaje);
        problema.setProperty("error", "Datos inválidos");
        problema.setProperty("campos", campos);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problema);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> manejarClaveDuplicada(DuplicateKeyException excepcion) {
        String mensaje = "Ya existe un registro con estos datos únicos";
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, mensaje);
        problema.setTitle("Conflicto por Registro Duplicado");
        problema.setType(TIPO_ERROR_DUPLICADO);
        problema.setProperty("mensaje", mensaje);
        problema.setProperty("error", mensaje);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> manejarNoEncontrado(RecursoNoEncontradoException excepcion) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, excepcion.getMessage());
        problema.setTitle("Recurso No Encontrado");
        problema.setType(TIPO_ERROR_NO_ENCONTRADO);
        problema.setProperty("mensaje", excepcion.getMessage());
        problema.setProperty("error", excepcion.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problema);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> manejarResponseStatus(ResponseStatusException excepcion) {
        String detalle = excepcion.getReason() != null ? excepcion.getReason() : excepcion.getMessage();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(excepcion.getStatusCode(), detalle);
        problema.setTitle("Error en Operación REST");
        problema.setProperty("mensaje", detalle);
        problema.setProperty("error", detalle);
        return ResponseEntity.status(excepcion.getStatusCode()).body(problema);
    }
}
