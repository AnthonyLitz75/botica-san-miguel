package com.farmacia.controller;

import com.farmacia.dto.ProveedorFormulario;
import com.farmacia.exception.ValidacionContactoException;
import com.farmacia.model.Proveedor;
import com.farmacia.service.ProveedorServicio;
import com.farmacia.util.AvisoFlash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
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
    public Object guardar(
            @Valid @ModelAttribute("formulario") ProveedorFormulario formulario,
            BindingResult resultado,
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
            return "proveedores/formulario";
        }

        try {
            servicio.guardar(formulario);
            if (esAjax) {
                AvisoFlash.guardar(request, response, "/proveedores", "Proveedor registrado correctamente.");
                return ResponseEntity.ok(Map.of("ok", true, "url", "/proveedores"));
            }
        } catch (ValidacionContactoException excepcion) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of("errores", excepcion.getErroresPorCampo()));
            }
            excepcion.getErroresPorCampo().forEach((campo, msg) ->
                    resultado.rejectValue(campo, campo + ".duplicado", msg));
            return "proveedores/formulario";
        } catch (DuplicateKeyException excepcion) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of("errores",
                        Map.of("ruc", "Ya existe un proveedor con este RUC")));
            }
            resultado.rejectValue(
                    "ruc",
                    "ruc.duplicado",
                    "Ya existe un proveedor con este RUC");

            return "proveedores/formulario";
        }

        redireccion.addFlashAttribute("mensaje", "Proveedor registrado correctamente.");
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
    public Object actualizar(
            @PathVariable("id") Integer id,
            @Valid @ModelAttribute("formulario") ProveedorFormulario formulario,
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
                        "Proveedor no encontrado"));

        model.addAttribute("idProveedor", id);

        if (resultado.hasErrors()) {
            if (esAjax) {
                Map<String, String> errores = new LinkedHashMap<>();
                for (FieldError err : resultado.getFieldErrors()) {
                    errores.putIfAbsent(err.getField(), err.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(Map.of("errores", errores));
            }
            return "proveedores/formulario";
        }

        try {
            boolean actualizado = servicio.actualizar(id, formulario);

            if (!actualizado) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Proveedor no encontrado");
            }
            if (esAjax) {
                AvisoFlash.guardar(request, response, "/proveedores", "Cambios del proveedor guardados correctamente.");
                return ResponseEntity.ok(Map.of("ok", true, "url", "/proveedores"));
            }
            redireccion.addFlashAttribute("mensaje", "Cambios del proveedor guardados correctamente.");
            return "redirect:/proveedores";
        } catch (ValidacionContactoException excepcion) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of("errores", excepcion.getErroresPorCampo()));
            }
            excepcion.getErroresPorCampo().forEach((campo, msg) ->
                    resultado.rejectValue(campo, campo + ".duplicado", msg));
            return "proveedores/formulario";
        } catch (DuplicateKeyException excepcion) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of("errores",
                        Map.of("ruc", "Ya existe un proveedor con este RUC")));
            }
            resultado.rejectValue(
                    "ruc",
                    "ruc.duplicado",
                    "Ya existe un proveedor con este RUC");

            return "proveedores/formulario";
        }
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
