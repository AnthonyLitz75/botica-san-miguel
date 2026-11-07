package com.farmacia.service;

import com.farmacia.model.AlertaStockVista;
import com.farmacia.model.AlertaVencimientoVista;
import com.farmacia.model.PanelAlertasVista;
import com.farmacia.model.ResumenStockInventario;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.MedicamentoRepositorio;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AlertaInventarioServicio {
    private static final long HORIZONTE_VENCIMIENTO_DIAS = 90;

    private final MedicamentoRepositorio medicamentoRepositorio;
    private final LoteRepositorio loteRepositorio;
    private final Clock clock;

    public AlertaInventarioServicio(MedicamentoRepositorio medicamentoRepositorio,
            LoteRepositorio loteRepositorio, Clock clock) {
        this.medicamentoRepositorio = medicamentoRepositorio;
        this.loteRepositorio = loteRepositorio;
        this.clock = clock;
    }

    public PanelAlertasVista consultarAlertas() {
        List<AlertaStockVista> stock = alertasStock();
        LocalDate hoy = LocalDate.now(clock);
        return new PanelAlertasVista(
                stock.stream().filter(alerta -> alerta.tipoAlerta().equals("AGOTADO")).toList(),
                stock.stream().filter(alerta -> alerta.tipoAlerta().equals("STOCK_BAJO")).toList(),
                loteRepositorio.listarVencidos(hoy),
                loteRepositorio.listarPorVencer(hoy, hoy.plusDays(HORIZONTE_VENCIMIENTO_DIAS)));
    }

    public List<AlertaStockVista> alertasStock() {
        return medicamentoRepositorio.listarResumenStock(LocalDate.now(clock)).stream()
                .filter(resumen -> resumen.stockDisponible() <= resumen.stockMinimo())
                .map(this::crearAlertaStock)
                .toList();
    }

    public List<AlertaStockVista> necesidadesReposicion() {
        return medicamentoRepositorio.listarResumenStock(LocalDate.now(clock)).stream()
                .map(this::crearAlertaStock)
                .filter(alerta -> alerta.cantidadSugerida() > 0)
                .toList();
    }

    public List<AlertaVencimientoVista> listarVencidos() {
        return loteRepositorio.listarVencidos(LocalDate.now(clock));
    }

    public List<AlertaVencimientoVista> listarPorVencer() {
        LocalDate hoy = LocalDate.now(clock);
        return loteRepositorio.listarPorVencer(hoy, hoy.plusDays(HORIZONTE_VENCIMIENTO_DIAS));
    }

    private AlertaStockVista crearAlertaStock(ResumenStockInventario resumen) {
        int sugerida = Math.max(0,
                resumen.stockMinimo() - resumen.stockDisponible() - resumen.pendienteRecibir());
        String tipo = resumen.stockDisponible() == 0 ? "AGOTADO" : "STOCK_BAJO";
        return new AlertaStockVista(resumen.idMedicamento(), resumen.codigoMedicamento(),
                resumen.nombreMedicamento(), resumen.unidadControl(), resumen.stockDisponible(),
                resumen.stockMinimo(), resumen.pendienteRecibir(), sugerida, tipo);
    }
}
