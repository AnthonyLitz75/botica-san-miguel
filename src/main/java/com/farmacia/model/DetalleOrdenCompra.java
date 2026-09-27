package com.farmacia.model;

import java.math.BigDecimal;

public record DetalleOrdenCompra(
        Integer idDetalleOrden,
        Integer idOrden,
        Integer idMedicamento,
        Integer cantidadSolicitada,
        BigDecimal costoUnitario,
        BigDecimal importeEstimado) {
}