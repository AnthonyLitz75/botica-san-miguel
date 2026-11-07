package com.farmacia.controller;

import com.farmacia.model.AlertaStockVista;
import com.farmacia.model.AlertaVencimientoVista;
import com.farmacia.model.PanelAlertasVista;
import com.farmacia.service.AlertaInventarioServicio;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alertas")
public class AlertaControlador {
    private final AlertaInventarioServicio servicio;

    public AlertaControlador(AlertaInventarioServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public PanelAlertasVista consultar() {
        return servicio.consultarAlertas();
    }

    @GetMapping("/stock")
    public List<AlertaStockVista> stock() {
        return servicio.alertasStock();
    }

    @GetMapping("/reposicion")
    public List<AlertaStockVista> reposicion() {
        return servicio.necesidadesReposicion();
    }

    @GetMapping("/vencidos")
    public List<AlertaVencimientoVista> vencidos() {
        return servicio.listarVencidos();
    }

    @GetMapping("/por-vencer")
    public List<AlertaVencimientoVista> porVencer() {
        return servicio.listarPorVencer();
    }
}
