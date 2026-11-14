package com.farmacia.controller;

import com.farmacia.dto.FiltroVentasFormulario;
import com.farmacia.service.VentaServicio;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Consulta del historial general de ventas para el ADMINISTRADOR (spec 005, US-1, RF-001).
 */
@Controller
public class HistorialVentasControlador {

    private final VentaServicio ventaServicio;

    public HistorialVentasControlador(VentaServicio ventaServicio) {
        this.ventaServicio = ventaServicio;
    }

    @GetMapping("/ventas")
    public String listar(@ModelAttribute("filtro") FiltroVentasFormulario filtro,
                         @RequestParam(name = "pagina", defaultValue = "1") int pagina,
                         Authentication autenticacion,
                         Model model) {
        model.addAttribute("vendedores", ventaServicio.listarVendedores());

        try {
            int paginaEfectiva = (filtro != null && filtro.pagina() != null && filtro.pagina() > 0)
                    ? filtro.pagina()
                    : pagina;
            String usuario = autenticacion != null ? autenticacion.getName() : null;
            var historial = ventaServicio.listarHistorial(usuario, filtro, paginaEfectiva);
            model.addAttribute("historial", historial);
            model.addAttribute("filas", historial.filas());
            model.addAttribute("totalFiltrado", historial.totalFiltrado());
            model.addAttribute("pagina", historial.pagina());
            model.addAttribute("paginas", historial.paginas());
            model.addAttribute("total", historial.total());
            model.addAttribute("desdeEfectivo", historial.desde());
            model.addAttribute("hastaEfectivo", historial.hasta());
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("filas", List.of());
            model.addAttribute("totalFiltrado", BigDecimal.ZERO);
            model.addAttribute("pagina", 1);
            model.addAttribute("paginas", 1);
            model.addAttribute("total", 0);
        }

        return "ventas/historial";
    }

    /**
     * Consulta de ventas propias del VENDEDOR autenticado para reimpresión (spec 005, US-4, RF-005).
     */
    @GetMapping("/ventas/mias")
    public String misVentas(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication autenticacion,
            Model model) {
        try {
            String usuario = autenticacion != null ? autenticacion.getName() : null;
            var resultado = ventaServicio.listarMisVentas(usuario, desde, hasta);
            model.addAttribute("filas", resultado.filas());
            model.addAttribute("desde", resultado.desde());
            model.addAttribute("hasta", resultado.hasta());
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("filas", List.of());
            model.addAttribute("desde", desde);
            model.addAttribute("hasta", hasta);
        }
        return "ventas/mis-ventas";
    }
}
