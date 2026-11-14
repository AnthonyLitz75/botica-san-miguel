package com.farmacia.controller;

import com.farmacia.dto.ExistenciaInicialFormulario;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.MovimientoInventarioServicio;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador web para registrar existencias iniciales sin orden de compra (spec 006, RF-001, US-1).
 */
@Controller
@RequestMapping("/inventario/existencias-iniciales")
public class ExistenciaInicialControlador {

    private final MovimientoInventarioServicio movimientoServicio;
    private final MedicamentoServicio medicamentoServicio;

    public ExistenciaInicialControlador(
            MovimientoInventarioServicio movimientoServicio,
            MedicamentoServicio medicamentoServicio) {
        this.movimientoServicio = movimientoServicio;
        this.medicamentoServicio = medicamentoServicio;
    }

    @GetMapping
    public String formulario(Model model) {
        if (!model.containsAttribute("formulario")) {
            model.addAttribute("formulario", ExistenciaInicialFormulario.vacio());
        }
        model.addAttribute("medicamentos", medicamentoServicio.listarActivos());
        return "inventario/existencias-iniciales";
    }

    @PostMapping
    public String registrar(
            @Valid @ModelAttribute("formulario") ExistenciaInicialFormulario formulario,
            BindingResult validacion,
            Authentication autenticacion,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (validacion.hasErrors()) {
            model.addAttribute("medicamentos", medicamentoServicio.listarActivos());
            return "inventario/existencias-iniciales";
        }

        try {
            String usuario = autenticacion != null ? autenticacion.getName() : null;
            movimientoServicio.registrarExistenciaInicial(usuario, formulario);
            redirectAttributes.addFlashAttribute("exito", "Existencias iniciales registradas correctamente");
            return "redirect:/movimientos";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("medicamentos", medicamentoServicio.listarActivos());
            return "inventario/existencias-iniciales";
        }
    }
}
