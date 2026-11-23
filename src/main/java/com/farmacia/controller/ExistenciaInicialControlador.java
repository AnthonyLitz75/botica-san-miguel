package com.farmacia.controller;

import com.farmacia.dto.ExistenciaInicialFormulario;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.MovimientoInventarioServicio;
import com.farmacia.util.AvisoFlash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador web para registrar existencias iniciales sin orden de compra (spec 006, RF-001, US-1).
 */
@Controller
@RequestMapping("/inventario/existencias-iniciales")
public class ExistenciaInicialControlador {

    private final MovimientoInventarioServicio movimientoServicio;
    private final MedicamentoServicio medicamentoServicio;

    public ExistenciaInicialControlador(
            MovimientoInventarioServicio movimientoServicio,
            MedicamentoServicio medicamentoServicio) {
        this.movimientoServicio = movimientoServicio;
        this.medicamentoServicio = medicamentoServicio;
    }

    @GetMapping
    public String formulario(Model model) {
        if (!model.containsAttribute("formulario")) {
            model.addAttribute("formulario", ExistenciaInicialFormulario.vacio());
        }
        model.addAttribute("medicamentos", medicamentoServicio.listarActivos());
        return "inventario/existencias-iniciales";
    }

    @PostMapping
    public Object registrar(
            @Valid @ModelAttribute("formulario") ExistenciaInicialFormulario formulario,
            BindingResult validacion,
            Authentication autenticacion,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            @RequestHeader(value = "Accept", required = false) String accept) {

        boolean esAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.contains("application/json"));

        if (validacion.hasErrors()) {
            if (esAjax) {
                Map<String, String> errores = new LinkedHashMap<>();
                for (FieldError err : validacion.getFieldErrors()) {
                    errores.putIfAbsent(err.getField(), err.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(Map.of("errores", errores));
            }
            model.addAttribute("medicamentos", medicamentoServicio.listarActivos());
            return "inventario/existencias-iniciales";
        }

        try {
            String usuario = autenticacion != null ? autenticacion.getName() : null;
            movimientoServicio.registrarExistenciaInicial(usuario, formulario);

            if (esAjax) {
                AvisoFlash.guardar(request, response, "/movimientos", "Existencias iniciales registradas correctamente");
                if (session != null) {
                    session.setAttribute("exito", "Existencias iniciales registradas correctamente");
                }
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "url", "/movimientos",
                        "mensaje", "Existencias iniciales registradas correctamente"));
            }

            redirectAttributes.addFlashAttribute("exito", "Existencias iniciales registradas correctamente");
            return "redirect:/movimientos";
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Error al registrar existencias iniciales";
            if (esAjax) {
                Map<String, String> errores = new LinkedHashMap<>();
                String msgLower = msg.toLowerCase();
                if (msgLower.contains("lote") || msgLower.contains("sumar")) {
                    errores.put("numeroLote", msg);
                } else if (msgLower.contains("vencimiento")) {
                    errores.put("fechaVencimiento", msg);
                } else if (msgLower.contains("cantidad")) {
                    errores.put("cantidad", msg);
                } else if (msgLower.contains("medicamento")) {
                    errores.put("idMedicamento", msg);
                } else {
                    errores.put("motivo", msg);
                }
                return ResponseEntity.badRequest().body(Map.of("error", msg, "errores", errores));
            }
            model.addAttribute("error", msg);
            model.addAttribute("medicamentos", medicamentoServicio.listarActivos());
            return "inventario/existencias-iniciales";
        }
    }
}
