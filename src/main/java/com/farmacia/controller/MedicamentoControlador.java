package com.farmacia.controller;

import com.farmacia.dto.MedicamentoFormulario;
import com.farmacia.estructuras.ArbolCategorias;
import com.farmacia.model.Medicamento;
import com.farmacia.service.CategoriaServicio;
import com.farmacia.service.MedicamentoServicio;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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

@Controller
@RequestMapping("/medicamentos")
public class MedicamentoControlador {

    private final MedicamentoServicio servicio;
    private final CategoriaServicio categoriaServicio;

    public MedicamentoControlador(MedicamentoServicio servicio, CategoriaServicio categoriaServicio) {
        this.servicio = servicio;
        this.categoriaServicio = categoriaServicio;
    }

    @GetMapping
    public String listar(
            @RequestParam(name = "buscar", defaultValue = "") String buscar,
            @RequestParam(name = "activo", defaultValue = "true") boolean activo,
            @RequestParam(name = "categoria", required = false) Integer categoriaId,
            Model model,
            Authentication autenticacion) {

        List<Medicamento> medicamentos;
        if (categoriaId != null) {
            ArbolCategorias arbol = categoriaServicio.arbol();
            List<Integer> idsRama = arbol.contiene(categoriaId)
                    ? arbol.descendientesIds(categoriaId)
                    : List.of(categoriaId);
            medicamentos = servicio.buscarActivosPorCategorias(buscar, idsRama);
            categoriaServicio.buscarPorId(categoriaId)
                    .ifPresent(cat -> model.addAttribute("categoriaSeleccionada", cat));
        } else {
            medicamentos = servicio.buscarPorEstado(buscar, activo);
        }

        model.addAttribute("medicamentos", medicamentos);
        model.addAttribute("buscar", buscar);
        model.addAttribute("activo", activo);
        model.addAttribute("categoriaId", categoriaId);
        model.addAttribute("categorias", categoriaServicio.listarActivas());
        return "medicamentos/listado";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute(
                "formulario",
                new MedicamentoFormulario("", "", "", "", "", null, 0, null));
        model.addAttribute("categorias", categoriaServicio.listarActivas());
        return "medicamentos/formulario";
    }

    @PostMapping
    public String guardar(
            @Valid @ModelAttribute("formulario") MedicamentoFormulario formulario,
            BindingResult resultado,
            Model model) {
        if (resultado.hasErrors()) {
            model.addAttribute("categorias", categoriaServicio.listarActivas());
            return "medicamentos/formulario";
        }

        try {
            servicio.guardar(formulario);
        } catch (DuplicateKeyException excepcion) {
            resultado.rejectValue(
                    "codigo",
                    "codigo.duplicado",
                    "Ya existe un medicamento con este codigo");
            model.addAttribute("categorias", categoriaServicio.listarActivas());
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
                medicamento.stockMinimo(),
                medicamento.idCategoria());

        model.addAttribute("formulario", formulario);
        model.addAttribute("idMedicamento", id);
        model.addAttribute("categorias", categoriaServicio.listarActivas());
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
        model.addAttribute("categorias", categoriaServicio.listarActivas());

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
