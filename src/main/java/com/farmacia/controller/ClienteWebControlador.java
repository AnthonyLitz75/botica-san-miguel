package com.farmacia.controller;

import com.farmacia.dto.ClienteFormulario;
import com.farmacia.exception.ValidacionContactoException;
import com.farmacia.model.Cliente;
import com.farmacia.model.ClienteResumenVista;
import com.farmacia.service.ClienteServicio;
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
@RequestMapping("/clientes")
public class ClienteWebControlador {
    private final ClienteServicio servicio;

    public ClienteWebControlador(ClienteServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String listar(@RequestParam(name = "buscar", defaultValue = "") String buscar, Model model) {
        String criterio = buscar.strip();
        List<ClienteResumenVista> clientes = servicio.listar(criterio);
        model.addAttribute("clientes", clientes);
        model.addAttribute("buscar", criterio);
        return "clientes/listado";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("formulario", new ClienteFormulario("DNI", "", "", "", ""));
        model.addAttribute("idCliente", null);
        return "clientes/formulario";
    }

    @PostMapping
    public Object guardar(@Valid @ModelAttribute("formulario") ClienteFormulario formulario,
            BindingResult resultado, Model model, RedirectAttributes redireccion,
            HttpServletRequest request, HttpServletResponse response,
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
            model.addAttribute("idCliente", null);
            return "clientes/formulario";
        }
        try {
            servicio.guardar(formulario);
            if (esAjax) {
                AvisoFlash.guardar(request, response, "/clientes", "Cliente registrado correctamente.");
                return ResponseEntity.ok(Map.of("ok", true, "url", "/clientes"));
            }
            redireccion.addFlashAttribute("mensaje", "Cliente registrado correctamente.");
            return "redirect:/clientes";
        } catch (ValidacionContactoException excepcion) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of("errores", excepcion.getErroresPorCampo()));
            }
            excepcion.getErroresPorCampo().forEach((campo, msg) ->
                    resultado.rejectValue(campo, campo + ".duplicado", msg));
            model.addAttribute("idCliente", null);
            return "clientes/formulario";
        } catch (DuplicateKeyException | IllegalArgumentException excepcion) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of("error", excepcion.getMessage(),
                        "errores", Map.of("numeroDocumento", "Ya existe un cliente con este documento")));
            }
            resultado.reject("cliente.invalido", excepcion.getMessage());
            model.addAttribute("idCliente", null);
            return "clientes/formulario";
        }
    }

    @GetMapping("/{idCliente}/editar")
    public String editar(@PathVariable Integer idCliente, Model model) {
        Cliente cliente = servicio.buscarPorId(idCliente).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        model.addAttribute("idCliente", idCliente);
        model.addAttribute("formulario", new ClienteFormulario(cliente.tipoDocumento(), cliente.numeroDocumento(),
                cliente.nombreCompleto(), cliente.telefono(), cliente.correo()));
        return "clientes/formulario";
    }

    @PostMapping("/{idCliente}/editar")
    public Object actualizar(@PathVariable Integer idCliente,
            @Valid @ModelAttribute("formulario") ClienteFormulario formulario,
            BindingResult resultado, Model model, RedirectAttributes redireccion,
            HttpServletRequest request, HttpServletResponse response,
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
            model.addAttribute("idCliente", idCliente);
            return "clientes/formulario";
        }
        try {
            if (!servicio.actualizar(idCliente, formulario)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
            }
            if (esAjax) {
                AvisoFlash.guardar(request, response, "/clientes", "Cambios del cliente guardados correctamente.");
                return ResponseEntity.ok(Map.of("ok", true, "url", "/clientes"));
            }
            redireccion.addFlashAttribute("mensaje", "Cambios del cliente guardados correctamente.");
            return "redirect:/clientes";
        } catch (ValidacionContactoException excepcion) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of("errores", excepcion.getErroresPorCampo()));
            }
            excepcion.getErroresPorCampo().forEach((campo, msg) ->
                    resultado.rejectValue(campo, campo + ".duplicado", msg));
            model.addAttribute("idCliente", idCliente);
            return "clientes/formulario";
        } catch (DuplicateKeyException | IllegalArgumentException excepcion) {
            if (esAjax) {
                return ResponseEntity.badRequest().body(Map.of("error", excepcion.getMessage(),
                        "errores", Map.of("numeroDocumento", "Ya existe un cliente con este documento")));
            }
            resultado.reject("cliente.invalido", excepcion.getMessage());
            model.addAttribute("idCliente", idCliente);
            return "clientes/formulario";
        }
    }
}
