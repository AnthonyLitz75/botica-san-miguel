package com.farmacia.controller;

import com.farmacia.dto.SalidaInventarioFormulario;
import com.farmacia.model.Lote;
import com.farmacia.model.Medicamento;
import com.farmacia.service.LoteServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.MovimientoInventarioServicio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequestMapping("/lotes/{idLote}/salida")
public class SalidaInventarioControlador {

    private final LoteServicio loteServicio;
    private final MedicamentoServicio medicamentoServicio;
    private final MovimientoInventarioServicio movimientoServicio;

    public SalidaInventarioControlador(
            LoteServicio loteServicio,
            MedicamentoServicio medicamentoServicio,
            MovimientoInventarioServicio movimientoServicio) {
        this.loteServicio = loteServicio;
        this.medicamentoServicio = medicamentoServicio;
        this.movimientoServicio = movimientoServicio;
    }

    @GetMapping
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
        model.addAttribute(
                "formulario",
                new SalidaInventarioFormulario(null, ""));

        return "salidas/formulario";
    }

    @PostMapping
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
}