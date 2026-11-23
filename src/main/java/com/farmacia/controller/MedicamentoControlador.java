package com.farmacia.controller;

import com.farmacia.dto.MedicamentoFormulario;
import com.farmacia.estructuras.ArbolCategorias;
import com.farmacia.model.Medicamento;
import com.farmacia.service.CategoriaServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.util.AvisoFlash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    public Object guardar(
            @Valid @ModelAttribute("formulario") MedicamentoFormulario formulario,
            BindingResult resultado,
            Model model,
            RedirectAttributes redireccion,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            @RequestHeader(value = "Accept", required = false) String accept) {

        boolean esAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.contains("application/json"));

        if (resultado.hasErrors()) {
            if (esAjax) {
                Map<String, String> errores = new LinkedHashMap<>();
                for (FieldError err : resultado.getFieldErrors()) {
                    errores.putIfAbsent(err.getField(), err.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(Map.of("errores", errores));
            }
            model.addAttribute("categorias", categoriaServicio.listarActivas());
            return "medicamentos/formulario";
        }

        try {
            servicio.guardar(formulario);
            if (esAjax) {
                AvisoFlash.guardar(request, response, "/medicamentos", "Medicamento registrado correctamente.");
                return ResponseEntity.ok(Map.of("ok", true, "url", "/medicamentos"));
            }
            redireccion.addFlashAttribute("mensaje", "Medicamento registrado correctamente.");
            return "redirect:/medicamentos";
        } catch (DuplicateKeyException excepcion) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of(
                        "errores", Map.of("codigo", "Ya existe un medicamento con este código")));
            }
            resultado.rejectValue(
                    "codigo",
                    "codigo.duplicado",
                    "Ya existe un medicamento con este codigo");
            model.addAttribute("categorias", categoriaServicio.listarActivas());
            return "medicamentos/formulario";
        }
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
    public Object actualizar(
            @PathVariable("id") Integer id,
            @Valid @ModelAttribute("formulario") MedicamentoFormulario formulario,
            BindingResult resultado,
            Model model,
            RedirectAttributes redireccion,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            @RequestHeader(value = "Accept", required = false) String accept) {

        boolean esAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.contains("application/json"));

        servicio.buscarPorId(id).orElseThrow(
                () -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Medicamento no encontrado"));

        model.addAttribute("idMedicamento", id);
        model.addAttribute("categorias", categoriaServicio.listarActivas());

        if (resultado.hasErrors()) {
            if (esAjax) {
                Map<String, String> errores = new LinkedHashMap<>();
                for (FieldError err : resultado.getFieldErrors()) {
                    errores.putIfAbsent(err.getField(), err.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(Map.of("errores", errores));
            }
            return "medicamentos/formulario";
        }

        try {
            boolean actualizado = servicio.actualizar(id, formulario);

            if (!actualizado) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Medicamento no encontrado");
            }
            if (esAjax) {
                AvisoFlash.guardar(request, response, "/medicamentos", "Cambios del medicamento guardados correctamente.");
                return ResponseEntity.ok(Map.of("ok", true, "url", "/medicamentos"));
            }
            redireccion.addFlashAttribute("mensaje", "Cambios del medicamento guardados correctamente.");
            return "redirect:/medicamentos";
        } catch (DuplicateKeyException excepcion) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of(
                        "errores", Map.of("codigo", "Ya existe un medicamento con este código")));
            }
            resultado.rejectValue(
                    "codigo",
                    "codigo.duplicado",
                    "Ya existe un medicamento con este codigo");
            return "medicamentos/formulario";
        }
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
