package com.farmacia.controller;

import com.farmacia.dto.ClienteFormulario;
import com.farmacia.model.Cliente;
import com.farmacia.service.ClienteServicio;
import jakarta.validation.Valid;
import java.util.List;
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
        List<Cliente> clientes = servicio.listar(criterio);
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
    public String guardar(@Valid @ModelAttribute("formulario") ClienteFormulario formulario,
            BindingResult resultado, Model model, RedirectAttributes redireccion) {
        if (resultado.hasErrors()) {
            model.addAttribute("idCliente", null);
            return "clientes/formulario";
        }
        try {
            servicio.guardar(formulario);
            redireccion.addFlashAttribute("mensaje", "Cliente registrado correctamente.");
            return "redirect:/clientes";
        } catch (DuplicateKeyException | IllegalArgumentException excepcion) {
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
    public String actualizar(@PathVariable Integer idCliente,
            @Valid @ModelAttribute("formulario") ClienteFormulario formulario,
            BindingResult resultado, Model model, RedirectAttributes redireccion) {
        if (resultado.hasErrors()) {
            model.addAttribute("idCliente", idCliente);
            return "clientes/formulario";
        }
        try {
            if (!servicio.actualizar(idCliente, formulario)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
            }
            redireccion.addFlashAttribute("mensaje", "Cambios del cliente guardados correctamente.");
            return "redirect:/clientes";
        } catch (DuplicateKeyException | IllegalArgumentException excepcion) {
            resultado.reject("cliente.invalido", excepcion.getMessage());
            model.addAttribute("idCliente", idCliente);
            return "clientes/formulario";
        }
    }
}
