package com.farmacia.controller;

import com.farmacia.dto.UsuarioClaveFormulario;
import com.farmacia.dto.UsuarioFormulario;
import com.farmacia.model.Usuario;
import com.farmacia.service.UsuarioServicio;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
public class UsuarioControlador {

    private static final List<String> ROLES_DISPONIBLES = List.of(
            "ADMINISTRADOR", "VENDEDOR", "ALMACENERO", "COMPRAS");

    private final UsuarioServicio servicio;

    public UsuarioControlador(UsuarioServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String listar(Model model, Authentication autenticacion) {
        List<Usuario> usuarios = servicio.listar(autenticacion.getName());
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("formularioClave", new UsuarioClaveFormulario(""));
        return "usuarios/listado";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("formulario", new UsuarioFormulario("", "", "VENDEDOR", ""));
        model.addAttribute("roles", ROLES_DISPONIBLES);
        model.addAttribute("idUsuario", null);
        return "usuarios/formulario";
    }

    @PostMapping
    public String guardar(
            @Valid @ModelAttribute("formulario") UsuarioFormulario formulario,
            BindingResult resultado,
            Model model,
            RedirectAttributes redireccion,
            Authentication autenticacion) {

        if (resultado.hasErrors()) {
            model.addAttribute("roles", ROLES_DISPONIBLES);
            model.addAttribute("idUsuario", null);
            return "usuarios/formulario";
        }

        try {
            servicio.crear(autenticacion.getName(), formulario);
            redireccion.addFlashAttribute("mensaje", "Usuario registrado correctamente.");
            return "redirect:/usuarios";
        } catch (IllegalArgumentException excepcion) {
            resultado.reject("usuario.invalido", excepcion.getMessage());
            model.addAttribute("roles", ROLES_DISPONIBLES);
            model.addAttribute("idUsuario", null);
            return "usuarios/formulario";
        }
    }

    @GetMapping("/{idUsuario}/editar")
    public String editar(
            @PathVariable Integer idUsuario,
            Model model,
            Authentication autenticacion) {

        Usuario usuario = servicio.buscarPorId(idUsuario, autenticacion.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        model.addAttribute("idUsuario", idUsuario);
        model.addAttribute("formulario", new UsuarioFormulario(
                usuario.nombre(),
                usuario.nombreUsuario(),
                usuario.rol(),
                ""));
        model.addAttribute("roles", ROLES_DISPONIBLES);
        return "usuarios/formulario";
    }

    @PostMapping("/{idUsuario}/editar")
    public String actualizar(
            @PathVariable Integer idUsuario,
            @Valid @ModelAttribute("formulario") UsuarioFormulario formulario,
            BindingResult resultado,
            Model model,
            RedirectAttributes redireccion,
            Authentication autenticacion) {

        if (resultado.hasErrors()) {
            model.addAttribute("idUsuario", idUsuario);
            model.addAttribute("roles", ROLES_DISPONIBLES);
            return "usuarios/formulario";
        }

        try {
            servicio.actualizar(autenticacion.getName(), idUsuario, formulario);
            redireccion.addFlashAttribute("mensaje", "Cambios del usuario guardados correctamente.");
            return "redirect:/usuarios";
        } catch (IllegalStateException | IllegalArgumentException excepcion) {
            resultado.reject("usuario.invalido", excepcion.getMessage());
            model.addAttribute("idUsuario", idUsuario);
            model.addAttribute("roles", ROLES_DISPONIBLES);
            return "usuarios/formulario";
        }
    }

    @PostMapping("/{idUsuario}/clave")
    public String cambiarClave(
            @PathVariable Integer idUsuario,
            @Valid @ModelAttribute("formularioClave") UsuarioClaveFormulario formulario,
            BindingResult resultado,
            RedirectAttributes redireccion,
            Authentication autenticacion) {

        if (resultado.hasErrors()) {
            redireccion.addFlashAttribute("error", "La contraseña debe tener entre 10 y 100 caracteres.");
            return "redirect:/usuarios";
        }

        try {
            servicio.cambiarClave(autenticacion.getName(), idUsuario, formulario);
            redireccion.addFlashAttribute("mensaje", "Contraseña restablecida correctamente.");
        } catch (IllegalArgumentException excepcion) {
            redireccion.addFlashAttribute("error", excepcion.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/{idUsuario}/desactivar")
    public String desactivar(
            @PathVariable Integer idUsuario,
            RedirectAttributes redireccion,
            Authentication autenticacion) {

        try {
            servicio.desactivar(autenticacion.getName(), idUsuario);
            redireccion.addFlashAttribute("mensaje", "Usuario desactivado correctamente.");
        } catch (IllegalStateException | IllegalArgumentException excepcion) {
            redireccion.addFlashAttribute("error", excepcion.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/{idUsuario}/activar")
    public String activar(
            @PathVariable Integer idUsuario,
            RedirectAttributes redireccion,
            Authentication autenticacion) {

        try {
            servicio.activar(autenticacion.getName(), idUsuario);
            redireccion.addFlashAttribute("mensaje", "Usuario activado correctamente.");
        } catch (IllegalArgumentException excepcion) {
            redireccion.addFlashAttribute("error", excepcion.getMessage());
        }
        return "redirect:/usuarios";
    }
}
