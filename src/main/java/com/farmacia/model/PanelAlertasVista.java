package com.farmacia.model;

import java.util.List;

public record PanelAlertasVista(
        List<AlertaStockVista> agotados,
        List<AlertaStockVista> stockBajo,
        List<AlertaVencimientoVista> vencidos,
        List<AlertaVencimientoVista> porVencer) {
}
