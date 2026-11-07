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
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        public String guardarSalidaDirecta(
                        @Valid @ModelAttribute("formulario") SalidaDirectaFormulario formulario,
                        BindingResult resultado,
                        Model model,
                        Authentication autenticacion) {

                if (resultado.hasErrors()) {
                        cargarLotesDisponibles(model);
                        return "salidas/formulario-directo";
                }

                try {
                        Integer idMovimiento = movimientoServicio.registrarSalida(
                                        formulario.idLote(),
                                        autenticacion.getName(),
                                        new SalidaInventarioFormulario(formulario.cantidad(), formulario.motivo()));

                        return "redirect:/movimientos/" + idMovimiento;
                } catch (IllegalArgumentException excepcion) {
                        resultado.reject("salida.invalida", excepcion.getMessage());
                        cargarLotesDisponibles(model);
                        return "salidas/formulario-directo";
                }
        }

        @GetMapping("/lotes/{idLote}/salida")
        public String nuevo(@PathVariable("idLote") Integer idLote, Model model) {
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
                model.addAttribute(
                                "formulario",
                                new SalidaInventarioFormulario(null, ""));

                return "salidas/formulario";
        }

        @PostMapping("/lotes/{idLote}/salida")
        public String guardar(
                        @PathVariable("idLote") Integer idLote,
                        @Valid @ModelAttribute("formulario") SalidaInventarioFormulario formulario,
                        BindingResult resultado,
                        Model model,
                        Authentication autenticacion) {

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
                        return "salidas/formulario";
                }

                try {
                        Integer idMovimiento = movimientoServicio.registrarSalida(
                                        idLote,
                                        autenticacion.getName(),
                                        formulario);

                        return "redirect:/movimientos/" + idMovimiento;
                } catch (IllegalArgumentException excepcion) {
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
