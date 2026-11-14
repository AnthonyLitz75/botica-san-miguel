package com.farmacia.controller;

import com.farmacia.dto.FiltroReporteFormulario;
import com.farmacia.model.CompraReporteVista;
import com.farmacia.model.EstadoComprasResumenVista;
import com.farmacia.model.InventarioReporteVista;
import com.farmacia.model.MedicamentoRotacionVista;
import com.farmacia.model.MermaVista;
import com.farmacia.model.VentasDiaVista;
import com.farmacia.service.ProveedorServicio;
import com.farmacia.service.ReporteServicio;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/reportes")
public class ReporteControlador {

    private final ReporteServicio reporteServicio;
    private final ProveedorServicio proveedorServicio;

    public ReporteControlador(ReporteServicio reporteServicio, ProveedorServicio proveedorServicio) {
        this.reporteServicio = reporteServicio;
        this.proveedorServicio = proveedorServicio;
    }

    @GetMapping("/ventas")
    public String ventasPorPeriodo(
            @ModelAttribute("filtro") FiltroReporteFormulario filtro,
            Principal principal,
            Model model) {
        String usuario = principal != null ? principal.getName() : null;
        FiltroReporteFormulario efectivo = reporteServicio.normalizarFiltro(filtro);
        model.addAttribute("filtro", efectivo);

        try {
            List<VentasDiaVista> reporte = reporteServicio.ventasPorDia(usuario, efectivo);
            model.addAttribute("reporte", reporte);

            long totalVentas = reporte.stream().mapToLong(VentasDiaVista::cantidadVentas).sum();
            BigDecimal subtotal = reporte.stream().map(VentasDiaVista::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal descuento = reporte.stream().map(VentasDiaVista::descuento).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal total = reporte.stream().map(VentasDiaVista::total).reduce(BigDecimal.ZERO, BigDecimal::add);

            model.addAttribute("totalVentas", totalVentas);
            model.addAttribute("subtotal", subtotal);
            model.addAttribute("descuento", descuento);
            model.addAttribute("total", total);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("reporte", List.of());
            model.addAttribute("totalVentas", 0L);
            model.addAttribute("subtotal", BigDecimal.ZERO);
            model.addAttribute("descuento", BigDecimal.ZERO);
            model.addAttribute("total", BigDecimal.ZERO);
        }

        return "reportes/ventas";
    }

    @GetMapping("/mas-vendidos")
    public String masVendidos(
            @ModelAttribute("filtro") FiltroReporteFormulario filtro,
            Principal principal,
            Model model) {
        String usuario = principal != null ? principal.getName() : null;
        FiltroReporteFormulario efectivo = reporteServicio.normalizarFiltro(filtro);
        model.addAttribute("filtro", efectivo);

        try {
            List<MedicamentoRotacionVista> reporte = reporteServicio.masVendidos(usuario, efectivo);
            model.addAttribute("reporte", reporte);

            long totalUnidades = reporte.stream().mapToLong(MedicamentoRotacionVista::unidadesVendidas).sum();
            BigDecimal totalVendido = reporte.stream().map(MedicamentoRotacionVista::totalVendido).reduce(BigDecimal.ZERO, BigDecimal::add);

            model.addAttribute("totalUnidades", totalUnidades);
            model.addAttribute("totalVendido", totalVendido);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("reporte", List.of());
            model.addAttribute("totalUnidades", 0L);
            model.addAttribute("totalVendido", BigDecimal.ZERO);
        }

        return "reportes/mas-vendidos";
    }

    @GetMapping("/mermas")
    public String mermas(
            @ModelAttribute("filtro") FiltroReporteFormulario filtro,
            Principal principal,
            Model model) {
        String usuario = principal != null ? principal.getName() : null;
        FiltroReporteFormulario efectivo = reporteServicio.normalizarFiltro(filtro);
        model.addAttribute("filtro", efectivo);

        try {
            List<MermaVista> reporte = reporteServicio.mermas(usuario, efectivo);
            model.addAttribute("reporte", reporte);

            long totalUnidades = reporte.stream().mapToLong(MermaVista::unidadesBaja).sum();
            model.addAttribute("totalUnidades", totalUnidades);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("reporte", List.of());
            model.addAttribute("totalUnidades", 0L);
        }

        return "reportes/mermas";
    }

    @GetMapping("/panel")
    public String panel(Principal principal, Model model) {
        String usuario = principal != null ? principal.getName() : null;
        var resumen = reporteServicio.panelResumen(usuario);
        model.addAttribute("resumen", resumen);
        return "reportes/panel";
    }

    @GetMapping("/inventario")
    public String reporteInventario(
            @RequestParam(name = "estado", required = false) String estado,
            Principal principal,
            Model model) {
        String usuario = principal != null ? principal.getName() : null;
        List<InventarioReporteVista> reporte = reporteServicio.reporteInventario(usuario, estado);
        model.addAttribute("reporte", reporte);
        model.addAttribute("estadoSeleccionado", estado != null ? estado.trim().toUpperCase() : "");

        long totalDisponible = reporte.stream().mapToLong(InventarioReporteVista::stockDisponible).sum();
        long totalVencido = reporte.stream().mapToLong(InventarioReporteVista::stockVencido).sum();
        long totalAlertas = reporte.stream().filter(i -> !"OK".equalsIgnoreCase(i.estado())).count();

        model.addAttribute("totalDisponible", totalDisponible);
        model.addAttribute("totalVencido", totalVencido);
        model.addAttribute("totalAlertas", totalAlertas);

        return "reportes/inventario";
    }

    @GetMapping("/compras")
    public String compras(
            @ModelAttribute("filtro") FiltroReporteFormulario filtro,
            Principal principal,
            Model model) {
        String usuario = principal != null ? principal.getName() : null;
        FiltroReporteFormulario efectivo = reporteServicio.normalizarFiltro(filtro);
        model.addAttribute("filtro", efectivo);
        model.addAttribute("proveedores", proveedorServicio.listarActivos());

        try {
            List<CompraReporteVista> reporte = reporteServicio.reporteCompras(usuario, efectivo);
            EstadoComprasResumenVista resumen = reporteServicio.resumenCompras(reporte);

            model.addAttribute("reporte", reporte);
            model.addAttribute("resumen", resumen);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("reporte", List.of());
            model.addAttribute("resumen", reporteServicio.resumenCompras(List.of()));
        }

        return "reportes/compras";
    }

    @GetMapping("/ventas.csv")
    public ResponseEntity<byte[]> ventasCsv(
            @ModelAttribute("filtro") FiltroReporteFormulario filtro,
            Principal principal) {
        String usuario = principal != null ? principal.getName() : null;
        String csv = reporteServicio.exportarVentasCsv(usuario, filtro);
        return descargarCsv(csv, "ventas.csv");
    }

    @GetMapping("/mas-vendidos.csv")
    public ResponseEntity<byte[]> masVendidosCsv(
            @ModelAttribute("filtro") FiltroReporteFormulario filtro,
            Principal principal) {
        String usuario = principal != null ? principal.getName() : null;
        String csv = reporteServicio.exportarMasVendidosCsv(usuario, filtro);
        return descargarCsv(csv, "mas_vendidos.csv");
    }

    @GetMapping("/mermas.csv")
    public ResponseEntity<byte[]> mermasCsv(
            @ModelAttribute("filtro") FiltroReporteFormulario filtro,
            Principal principal) {
        String usuario = principal != null ? principal.getName() : null;
        String csv = reporteServicio.exportarMermasCsv(usuario, filtro);
        return descargarCsv(csv, "mermas.csv");
    }

    @GetMapping("/inventario.csv")
    public ResponseEntity<byte[]> inventarioCsv(
            @RequestParam(name = "estado", required = false) String estado,
            Principal principal) {
        String usuario = principal != null ? principal.getName() : null;
        String csv = reporteServicio.exportarInventarioCsv(usuario, estado);
        return descargarCsv(csv, "inventario.csv");
    }

    @GetMapping("/compras.csv")
    public ResponseEntity<byte[]> comprasCsv(
            @ModelAttribute("filtro") FiltroReporteFormulario filtro,
            Principal principal) {
        String usuario = principal != null ? principal.getName() : null;
        String csv = reporteServicio.exportarComprasCsv(usuario, filtro);
        return descargarCsv(csv, "compras.csv");
    }

    private ResponseEntity<byte[]> descargarCsv(String csv, String nombreArchivo) {
        byte[] cuerpo = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                .body(cuerpo);
    }
}
