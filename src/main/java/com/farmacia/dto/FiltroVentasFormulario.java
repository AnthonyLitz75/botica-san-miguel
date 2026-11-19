package com.farmacia.dto;

import com.farmacia.model.MetodoPago;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Filtros de GET /ventas (spec 005 y spec 016). Los campos nulos o vacíos no aplican filtro. */
public record FiltroVentasFormulario(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        Integer idVendedor,
        String comprobante,
        MetodoPago metodoPago,
        Integer pagina) {

    public FiltroVentasFormulario(LocalDate desde, LocalDate hasta, Integer idVendedor, String comprobante, Integer pagina) {
        this(desde, hasta, idVendedor, comprobante, null, pagina);
    }

    public static FiltroVentasFormulario vacio() {
        return new FiltroVentasFormulario(null, null, null, null, null, null);
    }
}
