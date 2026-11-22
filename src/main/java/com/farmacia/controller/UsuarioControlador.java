package com.farmacia.controller;

import com.farmacia.dto.PaginacionModelo;
import com.farmacia.dto.UsuarioClaveFormulario;
import com.farmacia.dto.UsuarioFormulario;
import com.farmacia.model.Usuario;
import com.farmacia.model.UsuarioListadoVista;
import com.farmacia.service.UsuarioServicio;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/usuarios")
public class UsuarioControlador {

    private static final List<String> ROLES_DISPONIBLES = List.of(
            "ADMINISTRADOR", "VENDEDOR", "ALMACENERO", "COMPRAS");

    private final UsuarioServicio servicio;

    public UsuarioControlador(UsuarioServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String listar(@RequestParam(name = "pagina", defaultValue = "1") int pagina,
                         Model model, Authentication autenticacion) {
        String usuario = autenticacion != null ? autenticacion.getName() : null;
        UsuarioServicio.UsuariosPaginados paginados = null;
        try {
            paginados = servicio.listarParaListado(usuario, pagina);
        } catch (Exception ignored) {
            // fallback si no está configurado
        }

        if (paginados == null) {
            List<UsuarioListadoVista> listaVista = servicio.listarParaListado(usuario);
            if (listaVista != null && !listaVista.isEmpty()) {
                paginados = new UsuarioServicio.UsuariosPaginados(listaVista, listaVista.size(), 1, 1);
            }
        }

        List<UsuarioListadoVista> usuarios = paginados != null ? paginados.filas() : List.of();
        int total = paginados != null ? paginados.total() : 0;
        int pagActual = paginados != null ? paginados.pagina() : 1;
        int paginas = paginados != null ? paginados.paginas() : 1;

        if (usuarios.isEmpty()) {
            List<Usuario> lista = servicio.listar(usuario);
            if (lista != null && !lista.isEmpty()) {
                usuarios = lista.stream()
                        .map(u -> new UsuarioListadoVista(u.idUsuario(), u.nombre(), u.nombreUsuario(), u.rol(), u.activo(), null))
                        .toList();
                total = usuarios.size();
            }
        }

        model.addAttribute("usuarios", usuarios);
        model.addAttribute("total", total);
        model.addAttribute("pagina", pagActual);
        model.addAttribute("paginas", paginas);
        model.addAttribute("paginacion", PaginacionModelo.crear(
                pagActual, total, UsuarioServicio.USUARIOS_POR_PAGINA,
                "/usuarios", Map.of()));
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
    public Object guardar(
            @Valid @ModelAttribute("formulario") UsuarioFormulario formulario,
            BindingResult resultado,
            Model model,
            RedirectAttributes redireccion,
            Authentication autenticacion,
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
            model.addAttribute("roles", ROLES_DISPONIBLES);
            model.addAttribute("idUsuario", null);
            return "usuarios/formulario";
        }

        try {
            servicio.crear(autenticacion.getName(), formulario);
            if (esAjax) {
                return ResponseEntity.ok(Map.of("ok", true, "url", "/usuarios"));
            }
            redireccion.addFlashAttribute("mensaje", "Usuario registrado correctamente.");
            return "redirect:/usuarios";
        } catch (IllegalArgumentException excepcion) {
            if (esAjax) {
                Map<String, String> errores = new LinkedHashMap<>();
                String msg = excepcion.getMessage();
                if (msg != null && msg.toLowerCase().contains("nombre de usuario")) {
                    errores.put("nombreUsuario", msg);
                } else if (msg != null && msg.toLowerCase().contains("contraseña")) {
                    errores.put("clave", msg);
                } else {
                    errores.put("nombre", msg != null ? msg : "Error al registrar usuario");
                }
                return ResponseEntity.badRequest().body(Map.of("errores", errores));
            }
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
    public Object actualizar(
            @PathVariable Integer idUsuario,
            @Valid @ModelAttribute("formulario") UsuarioFormulario formulario,
            BindingResult resultado,
            Model model,
            RedirectAttributes redireccion,
            Authentication autenticacion,
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
            model.addAttribute("idUsuario", idUsuario);
            model.addAttribute("roles", ROLES_DISPONIBLES);
            return "usuarios/formulario";
        }

        try {
            servicio.actualizar(autenticacion.getName(), idUsuario, formulario);
            if (esAjax) {
                return ResponseEntity.ok(Map.of("ok", true, "url", "/usuarios"));
            }
            redireccion.addFlashAttribute("mensaje", "Cambios del usuario guardados correctamente.");
            return "redirect:/usuarios";
        } catch (IllegalStateException | IllegalArgumentException excepcion) {
            if (esAjax) {
                Map<String, String> errores = new LinkedHashMap<>();
                String msg = excepcion.getMessage();
                if (msg != null && msg.toLowerCase().contains("rol")) {
                    errores.put("rol", msg);
                } else {
                    errores.put("nombre", msg != null ? msg : "Error al actualizar usuario");
                }
                return ResponseEntity.badRequest().body(Map.of("errores", errores));
            }
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
