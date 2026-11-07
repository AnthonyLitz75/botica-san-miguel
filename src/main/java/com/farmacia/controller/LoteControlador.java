package com.farmacia.controller;

import com.farmacia.model.Medicamento;
import com.farmacia.service.LoteServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.LotePrioridadServicio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;

@Controller
@RequestMapping("/medicamentos/{idMedicamento}/lotes")
public class LoteControlador {

        private final LoteServicio loteServicio;
        private final MedicamentoServicio medicamentoServicio;
        private final LotePrioridadServicio prioridadServicio;

        public LoteControlador(
                        LoteServicio loteServicio,
                        MedicamentoServicio medicamentoServicio,
                        LotePrioridadServicio prioridadServicio) {
                this.loteServicio = loteServicio;
                this.medicamentoServicio = medicamentoServicio;
                this.prioridadServicio = prioridadServicio;
        }

        @GetMapping
        public String listar(
                        @PathVariable("idMedicamento") Integer idMedicamento,
                        Model model,
                        Authentication authentication) {

                Medicamento medicamento = medicamentoServicio
                                .buscarPorId(idMedicamento)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Medicamento no encontrado"));

                model.addAttribute("medicamento", medicamento);
                var lotes = loteServicio.listarPorMedicamento(idMedicamento);
                model.addAttribute("lotes", lotes);
                model.addAttribute("idsLotesPrioritarios", prioridadServicio.identificarLotesPrioritarios(
                                loteServicio.listarDisponiblesPorMedicamento(idMedicamento)));

                boolean puedeRegistrarSalida = authentication.getAuthorities()
                                .stream()
                                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMINISTRADOR")
                                                || autoridad.getAuthority().equals("ROLE_ALMACENERO"));

                model.addAttribute("puedeRegistrarSalida", puedeRegistrarSalida);
                return "lotes/listado";
        }
}
