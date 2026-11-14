package com.farmacia.controller;

import com.farmacia.dto.CategoriaFormulario;
import com.farmacia.estructuras.ArbolCategorias;
import com.farmacia.model.Categoria;
import com.farmacia.service.CategoriaServicio;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador web para la visualización del árbol y administración de categorías taxonómicas (spec 009).
 */
@Controller
@RequestMapping("/categorias")
public class CategoriaControlador {

    private final CategoriaServicio categoriaServicio;

    public CategoriaControlador(CategoriaServicio categoriaServicio) {
        this.categoriaServicio = categoriaServicio;
    }

    @GetMapping
    public String arbol(Model model) {
        ArbolCategorias arbol = categoriaServicio.arbol();
        List<Categoria> activas = categoriaServicio.listarActivas();

        java.util.Map<Integer, Integer> stockPorRama = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> alertasPorRama = new java.util.HashMap<>();
        for (Categoria cat : activas) {
            stockPorRama.put(cat.idCategoria(), categoriaServicio.stockPorRama(cat.idCategoria()));
            alertasPorRama.put(cat.idCategoria(), categoriaServicio.alertasPorRama(cat.idCategoria()));
        }

        model.addAttribute("arbol", arbol);
        model.addAttribute("categorias", activas);
        model.addAttribute("stockPorRama", stockPorRama);
        model.addAttribute("alertasPorRama", alertasPorRama);
        model.addAttribute("totalCategorias", arbol.tamanio());
        model.addAttribute("raices", arbol.raices());
        return "categorias/arbol";
    }

    @GetMapping("/nueva")
    public String nuevaCategoriaForm(Model model) {
        model.addAttribute("formulario", CategoriaFormulario.vacio());
        model.addAttribute("padresDisponibles", categoriaServicio.listarActivas());
        model.addAttribute("esEdicion", false);
        return "categorias/formulario";
    }

    @PostMapping("/nueva")
    public String guardarCategoria(
            @Valid @ModelAttribute("formulario") CategoriaFormulario formulario,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("padresDisponibles", categoriaServicio.listarActivas());
            model.addAttribute("esEdicion", false);
            return "categorias/formulario";
        }

        try {
            categoriaServicio.crear(formulario.nombre(), formulario.idPadre());
            redirectAttributes.addFlashAttribute("mensajeExito", "Categoría '" + formulario.nombre() + "' registrada exitosamente.");
            return "redirect:/categorias";
        } catch (IllegalArgumentException e) {
            model.addAttribute("padresDisponibles", categoriaServicio.listarActivas());
            model.addAttribute("esEdicion", false);
            model.addAttribute("errorNegocio", e.getMessage());
            return "categorias/formulario";
        }
    }

    @GetMapping("/{id}/editar")
    public String editarCategoriaForm(@PathVariable Integer id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Categoria> optCategoria = categoriaServicio.buscarPorId(id);
        if (optCategoria.isEmpty()) {
            redirectAttributes.addFlashAttribute("mensajeError", "Categoría no encontrada con ID: " + id);
            return "redirect:/categorias";
        }

        Categoria categoria = optCategoria.get();
        CategoriaFormulario formulario = new CategoriaFormulario(categoria.nombre(), categoria.idPadre());

        // Para evitar ciclos en la UI, filtramos el nodo actual y sus descendientes de la lista de padres disponibles
        ArbolCategorias arbol = categoriaServicio.arbol();
        List<Integer> idsExcluidos = arbol.contiene(id) ? arbol.descendientesIds(id) : List.of(id);

        List<Categoria> padresPermitidos = categoriaServicio.listarActivas().stream()
                .filter(c -> !idsExcluidos.contains(c.idCategoria()))
                .toList();

        model.addAttribute("formulario", formulario);
        model.addAttribute("categoriaId", id);
        model.addAttribute("padresDisponibles", padresPermitidos);
        model.addAttribute("esEdicion", true);
        return "categorias/formulario";
    }

    @PostMapping("/{id}/editar")
    public String actualizarCategoria(
            @PathVariable Integer id,
            @Valid @ModelAttribute("formulario") CategoriaFormulario formulario,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            prepararModeloEdicion(id, model);
            return "categorias/formulario";
        }

        try {
            categoriaServicio.actualizar(id, formulario.nombre(), formulario.idPadre());
            redirectAttributes.addFlashAttribute("mensajeExito", "Categoría '" + formulario.nombre() + "' actualizada correctamente.");
            return "redirect:/categorias";
        } catch (IllegalArgumentException e) {
            prepararModeloEdicion(id, model);
            model.addAttribute("errorNegocio", e.getMessage());
            return "categorias/formulario";
        }
    }

    @PostMapping("/{id}/desactivar")
    public String desactivarCategoria(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            categoriaServicio.desactivar(id);
            redirectAttributes.addFlashAttribute("mensajeExito", "Categoría desactivada exitosamente.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/categorias";
    }

    private void prepararModeloEdicion(Integer id, Model model) {
        ArbolCategorias arbol = categoriaServicio.arbol();
        List<Integer> idsExcluidos = arbol.contiene(id) ? arbol.descendientesIds(id) : List.of(id);
        List<Categoria> padresPermitidos = categoriaServicio.listarActivas().stream()
                .filter(c -> !idsExcluidos.contains(c.idCategoria()))
                .toList();

        model.addAttribute("categoriaId", id);
        model.addAttribute("padresDisponibles", padresPermitidos);
        model.addAttribute("esEdicion", true);
    }
}
