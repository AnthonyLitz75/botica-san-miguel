package com.farmacia.controller;

import com.farmacia.dto.MedicamentoFormulario;
import com.farmacia.model.Medicamento;
import com.farmacia.service.MedicamentoServicio;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/medicamentos")
public class MedicamentoApiControlador {

    private final MedicamentoServicio servicio;

    public MedicamentoApiControlador(MedicamentoServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<Medicamento> listar(
            @RequestParam(name = "activo", defaultValue = "true") boolean activo) {
        return servicio.buscarPorEstado("", activo);
    }

    @GetMapping("/{id}")
    public Medicamento detalle(@PathVariable Integer id) {
        return buscarO404(id);
    }

    @PostMapping
    public ResponseEntity<Medicamento> guardar(
            @Valid @RequestBody MedicamentoFormulario formulario) {
        Integer id = servicio.guardar(formulario);
        return ResponseEntity.created(URI.create("/api/medicamentos/" + id))
                .body(buscarO404(id));
    }

    @PutMapping("/{id}")
    public Medicamento actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody MedicamentoFormulario formulario) {
        if (!servicio.actualizar(id, formulario)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado");
        }
        return buscarO404(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        if (!servicio.desactivar(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado");
        }
        return ResponseEntity.noContent().build();
    }

    private Medicamento buscarO404(Integer id) {
        return servicio.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Medicamento no encontrado"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, String>> codigoDuplicado() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Ya existe un medicamento con este codigo"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> datosInvalidos(
            MethodArgumentNotValidException excepcion) {
        Map<String, String> campos = new LinkedHashMap<>();
        excepcion.getBindingResult().getFieldErrors()
                .forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Datos invalidos", "campos", campos));
    }
}
