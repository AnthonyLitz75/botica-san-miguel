package com.farmacia.controller;

import com.farmacia.service.AlertaInventarioServicio;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/alertas")
public class PanelAlertaControlador {
    private final AlertaInventarioServicio servicio;

    public PanelAlertaControlador(AlertaInventarioServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String consultar(Model model, Authentication autenticacion) {
        model.addAttribute("panel", servicio.consultarAlertas());
        model.addAttribute("reposicion", servicio.necesidadesReposicion());
        model.addAttribute("diasPorVencer", servicio.diasPorVencer());
        model.addAttribute("puedeCrearOrden", autenticacion.getAuthorities().stream()
                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMINISTRADOR")
                        || autoridad.getAuthority().equals("ROLE_COMPRAS")));
        model.addAttribute("puedeRegistrarSalida", autenticacion.getAuthorities().stream()
                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMINISTRADOR")
                        || autoridad.getAuthority().equals("ROLE_ALMACENERO")));
        return "alertas/listado";
    }
}
