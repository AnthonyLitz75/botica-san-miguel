package com.farmacia.model;

public record LineaRecepcionVista(
        Integer idDetalleOrden,
        Integer idMedicamento,
        String codigo,
        String nombre,
        Integer cantidadSolicitada,
        Integer cantidadRecibida,
        Integer cantidadPendiente) {}
