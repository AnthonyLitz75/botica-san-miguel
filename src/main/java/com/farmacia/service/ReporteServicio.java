package com.farmacia.service;

import com.farmacia.dto.FiltroReporteFormulario;
import com.farmacia.model.CompraReporteVista;
import com.farmacia.model.EstadoComprasResumenVista;
import com.farmacia.model.InventarioReporteVista;
import com.farmacia.model.MedicamentoRotacionVista;
import com.farmacia.model.MermaVista;
import com.farmacia.model.PanelVista;
import com.farmacia.model.Usuario;
import com.farmacia.model.VentasDiaVista;
import com.farmacia.repository.ReporteRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReporteServicio {

    private final ReporteRepositorio reporteRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final Clock clock;
    private final AlertaInventarioServicio alertaInventarioServicio;
    private final AuditoriaServicio auditoriaServicio;

    public ReporteServicio(
            ReporteRepositorio reporteRepositorio,
            UsuarioRepositorio usuarioRepositorio,
            Clock clock,
            AlertaInventarioServicio alertaInventarioServicio,
            AuditoriaServicio auditoriaServicio) {
        this.reporteRepositorio = reporteRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.clock = clock;
        this.alertaInventarioServicio = alertaInventarioServicio;
        this.auditoriaServicio = auditoriaServicio;
    }

    public List<VentasDiaVista> ventasPorDia(String usuarioAutenticado, FiltroReporteFormulario filtro) {
        validarAdministrador(usuarioAutenticado);
        FiltroReporteFormulario f = normalizarFiltro(filtro);
        f.validar();
        return reporteRepositorio.ventasPorDia(f.desde(), f.hasta());
    }

    public List<MedicamentoRotacionVista> masVendidos(String usuarioAutenticado, FiltroReporteFormulario filtro) {
        validarAdministrador(usuarioAutenticado);
        FiltroReporteFormulario f = normalizarFiltro(filtro);
        f.validar();
        return reporteRepositorio.masVendidos(f.desde(), f.hasta(), f.limite());
    }

    public List<MermaVista> mermas(String usuarioAutenticado, FiltroReporteFormulario filtro) {
        validarAdministrador(usuarioAutenticado);
        FiltroReporteFormulario f = normalizarFiltro(filtro);
        f.validar();
        return reporteRepositorio.mermas(f.desde(), f.hasta());
    }

    public PanelVista panelResumen(String usuarioAutenticado) {
        validarAdministrador(usuarioAutenticado);
        LocalDate hoy = LocalDate.now(clock);
        LocalDate inicioMes = hoy.withDayOfMonth(1);

        List<VentasDiaVista> ventasMes = reporteRepositorio.ventasPorDia(inicioMes, hoy);
        long cantidadVentasMes = ventasMes.stream().mapToLong(VentasDiaVista::cantidadVentas).sum();
        BigDecimal totalMes = ventasMes.stream().map(VentasDiaVista::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ticketPromedio = cantidadVentasMes > 0
                ? totalMes.divide(BigDecimal.valueOf(cantidadVentasMes), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long medicamentosAlertaStock = alertaInventarioServicio.alertasStock().size();
        long lotesPorVencer = alertaInventarioServicio.listarPorVencer().size();
        int dias = alertaInventarioServicio.diasPorVencer();

        return new PanelVista(totalMes, cantidadVentasMes, ticketPromedio, medicamentosAlertaStock, lotesPorVencer, dias);
    }

    public List<InventarioReporteVista> reporteInventario(String usuarioAutenticado, String estado) {
        validarAdministrador(usuarioAutenticado);
        LocalDate hoy = LocalDate.now(clock);
        return reporteRepositorio.reporteInventario(hoy, estado);
    }

    public List<CompraReporteVista> reporteCompras(String usuarioAutenticado, FiltroReporteFormulario filtro) {
        validarAdministrador(usuarioAutenticado);
        FiltroReporteFormulario f = normalizarFiltro(filtro);
        f.validar();
        return reporteRepositorio.reporteCompras(f.desde(), f.hasta(), f.estado(), f.idProveedor());
    }

    public EstadoComprasResumenVista resumenCompras(List<CompraReporteVista> ordenes) {
        Map<String, EstadoComprasResumenVista.CantidadYMonto> map = new LinkedHashMap<>();
        for (String est : List.of("PENDIENTE", "PARCIALMENTE_RECIBIDA", "RECIBIDA", "CANCELADA")) {
            map.put(est, new EstadoComprasResumenVista.CantidadYMonto(0, BigDecimal.ZERO));
        }

        int totalOrdenes = 0;
        BigDecimal montoTotal = BigDecimal.ZERO;

        if (ordenes != null) {
            for (CompraReporteVista o : ordenes) {
                totalOrdenes++;
                montoTotal = montoTotal.add(o.totalEstimado());

                String estado = o.estado();
                var actual = map.getOrDefault(estado, new EstadoComprasResumenVista.CantidadYMonto(0, BigDecimal.ZERO));
                map.put(estado, new EstadoComprasResumenVista.CantidadYMonto(
                        actual.cantidad() + 1,
                        actual.monto().add(o.totalEstimado())
                ));
            }
        }

        return new EstadoComprasResumenVista(map, totalOrdenes, montoTotal);
    }

    @Transactional(rollbackFor = Exception.class)
    public String exportarVentasCsv(String usuarioAutenticado, FiltroReporteFormulario filtro) {
        FiltroReporteFormulario f = normalizarFiltro(filtro);
        List<VentasDiaVista> datos = ventasPorDia(usuarioAutenticado, f);
        var csv = new CsvEscritor().fila("fecha", "cantidad_ventas", "subtotal", "descuento", "total");
        for (var v : datos) {
            csv.fila(v.fecha(), v.cantidadVentas(), v.subtotal(), v.descuento(), v.total());
        }
        auditoriaServicio.registrarCambio(AccionAuditoria.EXPORTAR, "REPORTE_VENTAS", null,
                "filas: " + datos.size() + "; desde: " + f.desde() + "; hasta: " + f.hasta());
        return csv.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public String exportarMasVendidosCsv(String usuarioAutenticado, FiltroReporteFormulario filtro) {
        FiltroReporteFormulario f = normalizarFiltro(filtro);
        List<MedicamentoRotacionVista> datos = masVendidos(usuarioAutenticado, f);
        var csv = new CsvEscritor().fila("codigo", "nombre", "unidades_vendidas", "total_vendido");
        for (var m : datos) {
            csv.fila(m.codigo(), m.nombre(), m.unidadesVendidas(), m.totalVendido());
        }
        auditoriaServicio.registrarCambio(AccionAuditoria.EXPORTAR, "REPORTE_MAS_VENDIDOS", null,
                "filas: " + datos.size() + "; desde: " + f.desde() + "; hasta: " + f.hasta());
        return csv.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public String exportarMermasCsv(String usuarioAutenticado, FiltroReporteFormulario filtro) {
        FiltroReporteFormulario f = normalizarFiltro(filtro);
        List<MermaVista> datos = mermas(usuarioAutenticado, f);
        var csv = new CsvEscritor().fila("codigo", "nombre", "unidades_baja");
        for (var m : datos) {
            csv.fila(m.codigo(), m.nombre(), m.unidadesBaja());
        }
        auditoriaServicio.registrarCambio(AccionAuditoria.EXPORTAR, "REPORTE_MERMAS", null,
                "filas: " + datos.size() + "; desde: " + f.desde() + "; hasta: " + f.hasta());
        return csv.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public String exportarInventarioCsv(String usuarioAutenticado, String estado) {
        List<InventarioReporteVista> datos = reporteInventario(usuarioAutenticado, estado);
        var csv = new CsvEscritor().fila("codigo", "nombre", "unidad_control", "stock_disponible", "stock_vencido", "stock_minimo", "estado");
        for (var i : datos) {
            csv.fila(i.codigo(), i.nombre(), i.unidadControl(), i.stockDisponible(), i.stockVencido(), i.stockMinimo(), i.estado());
        }
        auditoriaServicio.registrarCambio(AccionAuditoria.EXPORTAR, "REPORTE_INVENTARIO", null,
                "filas: " + datos.size() + "; estado: " + (estado != null && !estado.isBlank() ? estado : "TODOS"));
        return csv.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public String exportarComprasCsv(String usuarioAutenticado, FiltroReporteFormulario filtro) {
        FiltroReporteFormulario f = normalizarFiltro(filtro);
        List<CompraReporteVista> datos = reporteCompras(usuarioAutenticado, f);
        var csv = new CsvEscritor().fila("numero_orden", "fecha_emision", "proveedor", "estado", "total_estimado", "unidades_solicitadas", "unidades_recibidas", "unidades_pendientes");
        for (var o : datos) {
            csv.fila(o.numeroOrden(), o.fechaEmision(), o.proveedor(), o.estado(), o.totalEstimado(), o.unidadesSolicitadas(), o.unidadesRecibidas(), o.unidadesPendientes());
        }
        auditoriaServicio.registrarCambio(AccionAuditoria.EXPORTAR, "REPORTE_COMPRAS", null,
                "filas: " + datos.size() + "; desde: " + f.desde() + "; hasta: " + f.hasta());
        return csv.toString();
    }

    public FiltroReporteFormulario normalizarFiltro(FiltroReporteFormulario filtro) {
        if (filtro == null) {
            return FiltroReporteFormulario.porDefecto(clock);
        }
        return filtro.conRangoPorDefecto(clock);
    }

    public Clock clock() {
        return clock;
    }

    private void validarAdministrador(String usuarioAutenticado) {
        if (usuarioAutenticado == null || usuarioAutenticado.isBlank()) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        Usuario usuario = usuarioRepositorio.buscarPorNombreUsuario(usuarioAutenticado)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        if (!usuario.activo() || !usuario.rol().equals("ADMINISTRADOR")) {
            throw new AccessDeniedException("Solo el administrador puede acceder a los reportes");
        }
    }
}
