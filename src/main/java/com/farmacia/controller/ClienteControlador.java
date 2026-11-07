package com.farmacia.controller;

import com.farmacia.dto.ClienteFormulario;
import com.farmacia.model.Cliente;
import com.farmacia.service.ClienteServicio;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
@RequestMapping("/api/clientes")
public class ClienteControlador {
    private final ClienteServicio servicio;

    public ClienteControlador(ClienteServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<Cliente> listar(@RequestParam(name = "buscar", defaultValue = "") String buscar) {
        return servicio.listar(buscar);
    }

    @GetMapping("/{id}")
    public Cliente detalle(@PathVariable Integer id) {
        return servicio.buscarPorId(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cliente no encontrado"));
    }

    @PostMapping
    public ResponseEntity<Cliente> guardar(@Valid @RequestBody ClienteFormulario formulario) {
        Integer id = servicio.guardar(formulario);
        Cliente cliente = servicio.buscarPorId(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo recuperar el cliente registrado"));
        return ResponseEntity.created(URI.create("/api/clientes/" + id)).body(cliente);
    }

    @PutMapping("/{id}")
    public Cliente actualizar(@PathVariable Integer id, @Valid @RequestBody ClienteFormulario formulario) {
        if (!servicio.actualizar(id, formulario)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
        }
        return detalle(id);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, String>> documentoDuplicado() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Ya existe un cliente con este documento"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> documentoInvalido(IllegalArgumentException excepcion) {
        return ResponseEntity.badRequest().body(Map.of("error", excepcion.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> datosInvalidos(MethodArgumentNotValidException excepcion) {
        Map<String, String> campos = new LinkedHashMap<>();
        excepcion.getBindingResult().getFieldErrors()
                .forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("error", "Datos inválidos", "campos", campos));
    }
}
