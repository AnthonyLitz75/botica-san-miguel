package com.farmacia.model;

import java.math.BigDecimal;

public record DetalleOrdenCompraVista(
        Integer idDetalleOrden,
        Integer idMedicamento,
        String codigoMedicamento,
        String nombreMedicamento,
        String unidadControl,
        Integer cantidadSolicitada,
        Integer cantidadRecibida,
        Integer cantidadPendiente,
        BigDecimal costoUnitario,
        BigDecimal importeEstimado) {
}
