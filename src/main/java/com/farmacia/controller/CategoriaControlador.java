package com.farmacia.controller;

import com.farmacia.dto.CategoriaFormulario;
import com.farmacia.estructuras.ArbolCategorias;
import com.farmacia.model.Categoria;
import com.farmacia.service.CategoriaServicio;
import com.farmacia.util.AvisoFlash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public String arbol(
            @RequestParam(value = "resaltar", required = false) Integer resaltar,
            Model model) {
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
        model.addAttribute("categoriaResaltada", resaltar);
        return "categorias/arbol";
    }

    @GetMapping("/nueva")
    public String nuevaCategoriaForm(Model model) {
        model.addAttribute("formulario", CategoriaFormulario.vacio());
        model.addAttribute("padresDisponibles", categoriaServicio.listarActivas());
        model.addAttribute("esEdicion", false);
        return "categorias/formulario";
    }

    @PostMapping({"", "/nueva"})
    public Object guardarCategoria(
            @Valid @ModelAttribute("formulario") CategoriaFormulario formulario,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            @RequestHeader(value = "Accept", required = false) String accept) {

        boolean esAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.contains("application/json"));

        if (bindingResult.hasErrors()) {
            if (esAjax) {
                Map<String, String> errores = new LinkedHashMap<>();
                for (FieldError err : bindingResult.getFieldErrors()) {
                    errores.putIfAbsent(err.getField(), err.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(Map.of("errores", errores));
            }
            model.addAttribute("padresDisponibles", categoriaServicio.listarActivas());
            model.addAttribute("esEdicion", false);
            return "categorias/formulario";
        }

        try {
            Integer nuevoId = categoriaServicio.crear(formulario.nombre(), formulario.idPadre());
            if (esAjax) {
                AvisoFlash.guardar(request, response, "/categorias", "Categoría '" + formulario.nombre() + "' registrada exitosamente.");
                return ResponseEntity.ok(Map.of("ok", true, "url", "/categorias?resaltar=" + nuevoId, "id", nuevoId));
            }
            redirectAttributes.addFlashAttribute("mensajeExito", "Categoría '" + formulario.nombre() + "' registrada exitosamente.");
            return "redirect:/categorias";
        } catch (IllegalArgumentException e) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of("errores", Map.of("nombre", e.getMessage())));
            }
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
    public Object actualizarCategoria(
            @PathVariable Integer id,
            @Valid @ModelAttribute("formulario") CategoriaFormulario formulario,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            @RequestHeader(value = "Accept", required = false) String accept) {

        boolean esAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.contains("application/json"));

        if (bindingResult.hasErrors()) {
            if (esAjax) {
                Map<String, String> errores = new LinkedHashMap<>();
                for (FieldError err : bindingResult.getFieldErrors()) {
                    errores.putIfAbsent(err.getField(), err.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(Map.of("errores", errores));
            }
            prepararModeloEdicion(id, model);
            return "categorias/formulario";
        }

        try {
            categoriaServicio.actualizar(id, formulario.nombre(), formulario.idPadre());
            if (esAjax) {
                AvisoFlash.guardar(request, response, "/categorias", "Categoría '" + formulario.nombre() + "' actualizada correctamente.");
                return ResponseEntity.ok(Map.of("ok", true, "url", "/categorias?resaltar=" + id, "id", id));
            }
            redirectAttributes.addFlashAttribute("mensajeExito", "Categoría '" + formulario.nombre() + "' actualizada correctamente.");
            return "redirect:/categorias";
        } catch (IllegalArgumentException e) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of("errores", Map.of("nombre", e.getMessage())));
            }
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
