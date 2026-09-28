package com.farmacia.controller;

import com.farmacia.service.ProveedorServicio;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.farmacia.dto.ProveedorFormulario;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.Authentication;
import com.farmacia.model.Proveedor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/proveedores")
public class ProveedorControlador {

    private final ProveedorServicio servicio;

    public ProveedorControlador(ProveedorServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String listar(
            @RequestParam(name = "buscar", defaultValue = "") String buscar,
            @RequestParam(name = "activo", defaultValue = "true") boolean activo,
            Model model,
            Authentication autenticacion) {

        boolean puedeGestionarProveedores = autenticacion.getAuthorities().stream()
                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMINISTRADOR")
                        || autoridad.getAuthority().equals("ROLE_COMPRAS"));

        model.addAttribute(
                "proveedores",
                servicio.buscarPorEstado(buscar, activo));
        model.addAttribute("buscar", buscar);
        model.addAttribute("activo", activo);
        model.addAttribute(
                "puedeGestionarProveedores",
                puedeGestionarProveedores);

        return "proveedores/listado";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute(
                "formulario",
                new ProveedorFormulario("", "", "", "", ""));

        return "proveedores/formulario";
    }

    @PostMapping
    public String guardar(
            @Valid @ModelAttribute("formulario") ProveedorFormulario formulario,
            BindingResult resultado) {

        if (resultado.hasErrors()) {
            return "proveedores/formulario";
        }

        try {
            servicio.guardar(formulario);
        } catch (DuplicateKeyException excepcion) {
            resultado.rejectValue(
                    "ruc",
                    "ruc.duplicado",
                    "Ya existe un proveedor con este RUC");

            return "proveedores/formulario";
        }

        return "redirect:/proveedores";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable("id") Integer id, Model model) {
        Proveedor proveedor = servicio.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Proveedor no encontrado"));

        ProveedorFormulario formulario = new ProveedorFormulario(
                proveedor.ruc(),
                proveedor.razonSocial(),
                proveedor.telefono(),
                proveedor.correo(),
                proveedor.direccion());

        model.addAttribute("formulario", formulario);
        model.addAttribute("idProveedor", id);

        return "proveedores/formulario";
    }

    @PostMapping("/{id}/editar")
    public String actualizar(
            @PathVariable("id") Integer id,
            @Valid @ModelAttribute("formulario") ProveedorFormulario formulario,
            BindingResult resultado,
            Model model) {

        servicio.buscarPorId(id).orElseThrow(
                () -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Proveedor no encontrado"));

        model.addAttribute("idProveedor", id);

        if (resultado.hasErrors()) {
            return "proveedores/formulario";
        }

        try {
            boolean actualizado = servicio.actualizar(id, formulario);

            if (!actualizado) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Proveedor no encontrado");
            }
        } catch (DuplicateKeyException excepcion) {
            resultado.rejectValue(
                    "ruc",
                    "ruc.duplicado",
                    "Ya existe un proveedor con este RUC");

            return "proveedores/formulario";
        }

        return "redirect:/proveedores";
    }

    @PostMapping("/{id}/desactivar")
    public String desactivar(@PathVariable("id") Integer id) {
        boolean desactivado = servicio.desactivar(id);

        if (!desactivado) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Proveedor no encontrado");
        }

        return "redirect:/proveedores";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable("id") Integer id) {
        boolean activado = servicio.activar(id);

        if (!activado) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Proveedor no encontrado");
        }

        return "redirect:/proveedores";
    }
}
