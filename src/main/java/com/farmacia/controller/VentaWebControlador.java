package com.farmacia.controller;

import com.farmacia.model.Medicamento;
import com.farmacia.service.MedicamentoServicio;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ventas")
public class VentaWebControlador {

    private final MedicamentoServicio medicamentoServicio;

    public VentaWebControlador(MedicamentoServicio medicamentoServicio) {
        this.medicamentoServicio = medicamentoServicio;
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        List<Medicamento> medicamentos = medicamentoServicio.listarActivos();
        model.addAttribute("medicamentos", medicamentos);
        return "ventas/preparacion";
    }
}
