package com.farmacia.controller;

import com.farmacia.model.Medicamento;
import com.farmacia.service.ClienteServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.VentaServicio;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/ventas")
public class VentaWebControlador {

    private final MedicamentoServicio medicamentoServicio;
    private final ClienteServicio clienteServicio;
    private final VentaServicio ventaServicio;

    public VentaWebControlador(MedicamentoServicio medicamentoServicio, ClienteServicio clienteServicio,
            VentaServicio ventaServicio) {
        this.medicamentoServicio = medicamentoServicio;
        this.clienteServicio = clienteServicio;
        this.ventaServicio = ventaServicio;
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        List<Medicamento> medicamentos = medicamentoServicio.listarActivos();
        model.addAttribute("medicamentos", medicamentos);
        model.addAttribute("clientes", clienteServicio.listar(null));
        return "ventas/preparacion";
    }

    /** Comprobante interno imprimible (RF-13). El vendedor solo ve sus ventas (403 en otro caso). */
    @GetMapping("/{idVenta:[0-9]+}")
    public String comprobante(@PathVariable("idVenta") Integer idVenta, Authentication autenticacion, Model model) {
        var comprobante = ventaServicio.obtenerComprobante(idVenta, autenticacion.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada"));
        model.addAttribute("comprobante", comprobante);
        return "ventas/comprobante";
    }
}
