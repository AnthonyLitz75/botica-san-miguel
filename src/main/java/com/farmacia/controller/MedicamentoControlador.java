package com.farmacia.controller;

import com.farmacia.dto.MedicamentoFormulario;
import com.farmacia.model.Medicamento;
import com.farmacia.service.MedicamentoServicio;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;

@Controller
@RequestMapping("/medicamentos")
public class MedicamentoControlador {

    private final MedicamentoServicio servicio;

    public MedicamentoControlador(MedicamentoServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String listar(
            @RequestParam(name = "buscar", defaultValue = "") String buscar,
            @RequestParam(name = "activo", defaultValue = "true") boolean activo,
            Model model,
            Authentication autenticacion) {

        boolean esAdministrador = autenticacion.getAuthorities().stream()
                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMINISTRADOR"));

        model.addAttribute(
                "medicamentos",
                servicio.buscarPorEstado(buscar, activo));
        model.addAttribute("buscar", buscar);
        model.addAttribute("activo", activo);
        model.addAttribute("esAdministrador", esAdministrador);

        boolean puedeConsultarProveedores = autenticacion.getAuthorities().stream()
                .anyMatch(autoridad -> switch (autoridad.getAuthority()) {
                    case "ROLE_ADMINISTRADOR",
                            "ROLE_COMPRAS",
                            "ROLE_ALMACENERO" ->
                        true;
                    default -> false;
                });

        model.addAttribute(
                "puedeConsultarProveedores",
                puedeConsultarProveedores);

        boolean puedeConsultarMovimientos = autenticacion.getAuthorities().stream()
                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMINISTRADOR")
                        || autoridad.getAuthority().equals("ROLE_ALMACENERO"));

        model.addAttribute(
                "puedeConsultarMovimientos",
                puedeConsultarMovimientos);

        model.addAttribute(
                "puedeConsultarOrdenesCompra",
                puedeConsultarProveedores);
        return "medicamentos/listado";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute(
                "formulario",
                new MedicamentoFormulario("", "", "", "", "", null, 0));
        return "medicamentos/formulario";
    }

    @PostMapping
    public String guardar(
            @Valid @ModelAttribute("formulario") MedicamentoFormulario formulario,
            BindingResult resultado) {
        if (resultado.hasErrors()) {
            return "medicamentos/formulario";
        }

        try {
            servicio.guardar(formulario);
        } catch (DuplicateKeyException excepcion) {
            resultado.rejectValue(
                    "codigo",
                    "codigo.duplicado",
                    "Ya existe un medicamento con este codigo");
            return "medicamentos/formulario";
        }

        return "redirect:/medicamentos";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable("id") Integer id, Model model) {
        Medicamento medicamento = servicio.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Medicamento no encontrado"));

        MedicamentoFormulario formulario = new MedicamentoFormulario(
                medicamento.codigo(),
                medicamento.nombre(),
                medicamento.concentracion(),
                medicamento.presentacion(),
                medicamento.unidadControl(),
                medicamento.precioVenta(),
                medicamento.stockMinimo());

        model.addAttribute("formulario", formulario);
        model.addAttribute("idMedicamento", id);
        return "medicamentos/formulario";
    }

    @PostMapping("/{id}/editar")
    public String actualizar(
            @PathVariable("id") Integer id,
            @Valid @ModelAttribute("formulario") MedicamentoFormulario formulario,
            BindingResult resultado,
            Model model) {
        servicio.buscarPorId(id).orElseThrow(
                () -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Medicamento no encontrado"));

        model.addAttribute("idMedicamento", id);

        if (resultado.hasErrors()) {
            return "medicamentos/formulario";
        }

        try {
            boolean actualizado = servicio.actualizar(id, formulario);

            if (!actualizado) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Medicamento no encontrado");
            }
        } catch (DuplicateKeyException excepcion) {
            resultado.rejectValue(
                    "codigo",
                    "codigo.duplicado",
                    "Ya existe un medicamento con este codigo");
            return "medicamentos/formulario";
        }

        return "redirect:/medicamentos";
    }

    @PostMapping("/{id}/desactivar")
    public String desactivar(@PathVariable("id") Integer id) {
        boolean desactivado = servicio.desactivar(id);

        if (!desactivado) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Medicamento no encontrado");
        }

        return "redirect:/medicamentos";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable("id") Integer id) {
        boolean activado = servicio.activar(id);

        if (!activado) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Medicamento no encontrado");
        }

        return "redirect:/medicamentos";
    }
}