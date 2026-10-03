package com.farmacia.controller;

import com.farmacia.model.Medicamento;
import com.farmacia.service.LoteServicio;
import com.farmacia.service.MedicamentoServicio;
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

        public LoteControlador(
                        LoteServicio loteServicio,
                        MedicamentoServicio medicamentoServicio) {
                this.loteServicio = loteServicio;
                this.medicamentoServicio = medicamentoServicio;
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
                model.addAttribute(
                                "lotes",
                                loteServicio.listarPorMedicamento(idMedicamento));

                boolean puedeRegistrarSalida = authentication.getAuthorities()
                                .stream()
                                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMINISTRADOR")
                                                || autoridad.getAuthority().equals("ROLE_ALMACENERO"));

                model.addAttribute("puedeRegistrarSalida", puedeRegistrarSalida);
                return "lotes/listado";
        }
}