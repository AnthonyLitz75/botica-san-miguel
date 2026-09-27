package com.farmacia.controller;

import com.farmacia.service.MovimientoInventarioServicio;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.farmacia.model.MovimientoInventario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/movimientos")
public class MovimientoInventarioControlador {

    private final MovimientoInventarioServicio servicio;

    public MovimientoInventarioControlador(
            MovimientoInventarioServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("movimientos", servicio.listar());
        return "movimientos/listado";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable("id") Integer id, Model model) {
        MovimientoInventario movimiento = servicio.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Movimiento no encontrado"));

        model.addAttribute("movimiento", movimiento);
        model.addAttribute("detalles", servicio.listarDetalles(id));

        return "movimientos/detalle";
    }
}