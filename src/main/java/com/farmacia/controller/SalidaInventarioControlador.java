package com.farmacia.controller;

import com.farmacia.dto.SalidaDirectaFormulario;
import com.farmacia.dto.SalidaInventarioFormulario;
import com.farmacia.model.Lote;
import com.farmacia.model.Medicamento;
import com.farmacia.service.LoteServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.MovimientoInventarioServicio;
import com.farmacia.service.LotePrioridadServicio;
import com.farmacia.model.LoteDisponibleVista;
import com.farmacia.util.AvisoFlash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class SalidaInventarioControlador {

        private final LoteServicio loteServicio;
        private final MedicamentoServicio medicamentoServicio;
        private final MovimientoInventarioServicio movimientoServicio;
        private final LotePrioridadServicio prioridadServicio;

        public SalidaInventarioControlador(
                        LoteServicio loteServicio,
                        MedicamentoServicio medicamentoServicio,
                        MovimientoInventarioServicio movimientoServicio,
                        LotePrioridadServicio prioridadServicio) {
                this.loteServicio = loteServicio;
                this.medicamentoServicio = medicamentoServicio;
                this.movimientoServicio = movimientoServicio;
                this.prioridadServicio = prioridadServicio;
        }

        @GetMapping("/salidas/nueva")
        public String nuevaSalidaDirecta(Model model) {
                cargarLotesDisponibles(model);
                model.addAttribute("formulario", new SalidaDirectaFormulario(null, null, ""));
                return "salidas/formulario-directo";
        }

        @PostMapping("/salidas/nueva")
        public Object guardarSalidaDirecta(
                        @Valid @ModelAttribute("formulario") SalidaDirectaFormulario formulario,
                        BindingResult resultado,
                        Model model,
                        Authentication autenticacion,
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
                        cargarLotesDisponibles(model);
                        return "salidas/formulario-directo";
                }

                try {
                        Integer idMovimiento = movimientoServicio.registrarSalida(
                                        formulario.idLote(),
                                        autenticacion.getName(),
                                        new SalidaInventarioFormulario(formulario.cantidad(), formulario.motivo()));

                        String destino = "/movimientos/" + idMovimiento;
                        String mensajeExito = "Salida registrada correctamente.";
                        redireccion.addFlashAttribute("mensaje", mensajeExito);
                        if (esAjax) {
                                AvisoFlash.guardar(request, response, destino, mensajeExito);
                                return ResponseEntity.ok(Map.of("ok", true, "url", destino));
                        }
                        return "redirect:" + destino;
                } catch (IllegalArgumentException excepcion) {
                        if (esAjax) {
                                return ResponseEntity.badRequest().body(Map.of("mensaje", excepcion.getMessage()));
                        }
                        resultado.reject("salida.invalida", excepcion.getMessage());
                        cargarLotesDisponibles(model);
                        return "salidas/formulario-directo";
                }
        }

        @GetMapping("/lotes/{idLote}/salida")
        public String nuevo(
                        @PathVariable("idLote") Integer idLote,
                        @org.springframework.web.bind.annotation.RequestParam(name = "motivo", required = false) String motivo,
                        Model model) {
                Lote lote = loteServicio.buscarPorId(idLote)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Lote no encontrado"));

                Medicamento medicamento = medicamentoServicio
                                .buscarPorId(lote.idMedicamento())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Medicamento no encontrado"));

                String motivoInicial = "";
                if (motivo != null && !motivo.isBlank()) {
                        String limpio = motivo.strip();
                        motivoInicial = limpio.length() > 250 ? limpio.substring(0, 250) : limpio;
                }

                model.addAttribute("lote", lote);
                model.addAttribute("medicamento", medicamento);
                model.addAttribute("lotePrioritario", esLotePrioritario(lote.idLote(), lote.idMedicamento()));
                model.addAttribute(
                                "formulario",
                                new SalidaInventarioFormulario(null, motivoInicial));

                return "salidas/formulario";
        }

        @PostMapping("/lotes/{idLote}/salida")
        public Object guardar(
                        @PathVariable("idLote") Integer idLote,
                        @Valid @ModelAttribute("formulario") SalidaInventarioFormulario formulario,
                        BindingResult resultado,
                        Model model,
                        Authentication autenticacion,
                        RedirectAttributes redireccion,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                        @RequestHeader(value = "Accept", required = false) String accept) {

                boolean esAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                                || (accept != null && accept.contains("application/json"));

                Lote lote = loteServicio.buscarPorId(idLote)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Lote no encontrado"));

                Medicamento medicamento = medicamentoServicio
                                .buscarPorId(lote.idMedicamento())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Medicamento no encontrado"));

                model.addAttribute("lote", lote);
                model.addAttribute("medicamento", medicamento);
                model.addAttribute("lotePrioritario", esLotePrioritario(lote.idLote(), lote.idMedicamento()));

                if (resultado.hasErrors()) {
                        if (esAjax) {
                                Map<String, String> errores = new LinkedHashMap<>();
                                for (FieldError err : resultado.getFieldErrors()) {
                                        errores.putIfAbsent(err.getField(), err.getDefaultMessage());
                                }
                                return ResponseEntity.badRequest().body(Map.of("errores", errores));
                        }
                        return "salidas/formulario";
                }

                try {
                        Integer idMovimiento = movimientoServicio.registrarSalida(
                                        idLote,
                                        autenticacion.getName(),
                                        formulario);

                        String destino = "/movimientos/" + idMovimiento;
                        String mensajeExito = "Salida registrada correctamente.";
                        redireccion.addFlashAttribute("mensaje", mensajeExito);
                        if (esAjax) {
                                AvisoFlash.guardar(request, response, destino, mensajeExito);
                                return ResponseEntity.ok(Map.of("ok", true, "url", destino));
                        }
                        return "redirect:" + destino;
                } catch (IllegalArgumentException excepcion) {
                        if (esAjax) {
                                return ResponseEntity.badRequest().body(Map.of("mensaje", excepcion.getMessage()));
                        }
                        resultado.reject(
                                        "salida.invalida",
                                        excepcion.getMessage());

                        return "salidas/formulario";
                }
        }

        private void cargarLotesDisponibles(Model model) {
                List<LoteDisponibleVista> lotes = loteServicio.listarDisponiblesConMedicamento();
                model.addAttribute("lotes", lotes);
                model.addAttribute("idsLotesPrioritarios", prioridadServicio.identificarLotesPrioritarios(lotes));
        }

        private boolean esLotePrioritario(Integer idLote, Integer idMedicamento) {
                return prioridadServicio.identificarLotesPrioritarios(
                                loteServicio.listarDisponiblesPorMedicamento(idMedicamento)).contains(idLote);
        }
}
