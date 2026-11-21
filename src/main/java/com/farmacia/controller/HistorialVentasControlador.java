package com.farmacia.controller;

import com.farmacia.dto.FiltroVentasFormulario;
import com.farmacia.dto.PaginacionModelo;
import com.farmacia.service.VentaServicio;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            model.addAttribute("ticketPromedio", historial.ticketPromedio());
            model.addAttribute("pagina", historial.pagina());
            model.addAttribute("paginas", historial.paginas());
            model.addAttribute("total", historial.total());
            model.addAttribute("desdeEfectivo", historial.desde());
            model.addAttribute("hastaEfectivo", historial.hasta());

            Map<String, Object> params = filtro != null
                    ? filtro.aParametros(historial.desde(), historial.hasta())
                    : Map.of();
            model.addAttribute("paginacion", PaginacionModelo.crear(
                    historial.pagina(), historial.total(), VentaServicio.VENTAS_POR_PAGINA,
                    "/ventas", params));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("filas", List.of());
            model.addAttribute("totalFiltrado", BigDecimal.ZERO);
            model.addAttribute("ticketPromedio", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            model.addAttribute("pagina", 1);
            model.addAttribute("paginas", 1);
            model.addAttribute("total", 0);
            model.addAttribute("paginacion", PaginacionModelo.crear(
                    1, 0, VentaServicio.VENTAS_POR_PAGINA,
                    "/ventas", Map.of()));
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
            @RequestParam(name = "pagina", defaultValue = "1") int pagina,
            Authentication autenticacion,
            Model model) {
        try {
            String usuario = autenticacion != null ? autenticacion.getName() : null;
            var resultado = ventaServicio.listarMisVentas(usuario, desde, hasta, pagina);
            model.addAttribute("filas", resultado.filas());
            model.addAttribute("desde", resultado.desde());
            model.addAttribute("hasta", resultado.hasta());
            model.addAttribute("total", resultado.total());
            model.addAttribute("pagina", resultado.pagina());
            model.addAttribute("paginas", resultado.paginas());

            Map<String, Object> params = new LinkedHashMap<>();
            if (resultado.desde() != null) {
                params.put("desde", resultado.desde().toString());
            }
            if (resultado.hasta() != null) {
                params.put("hasta", resultado.hasta().toString());
            }
            model.addAttribute("paginacion", PaginacionModelo.crear(
                    resultado.pagina(), resultado.total(), VentaServicio.VENTAS_POR_PAGINA,
                    "/ventas/mias", params));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("filas", List.of());
            model.addAttribute("desde", desde);
            model.addAttribute("hasta", hasta);
            model.addAttribute("total", 0);
            model.addAttribute("pagina", 1);
            model.addAttribute("paginas", 1);
            model.addAttribute("paginacion", PaginacionModelo.crear(
                    1, 0, VentaServicio.VENTAS_POR_PAGINA,
                    "/ventas/mias", Map.of()));
        }
        return "ventas/mis-ventas";
    }
}
