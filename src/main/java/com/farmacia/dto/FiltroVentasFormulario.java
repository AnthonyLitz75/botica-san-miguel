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

    public java.util.Map<String, Object> aParametros(LocalDate desdeEfectivo, LocalDate hastaEfectivo) {
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        LocalDate d = desde != null ? desde : desdeEfectivo;
        LocalDate h = hasta != null ? hasta : hastaEfectivo;
        if (d != null) params.put("desde", d.toString());
        if (h != null) params.put("hasta", h.toString());
        if (idVendedor != null) params.put("idVendedor", idVendedor);
        if (comprobante != null && !comprobante.isBlank()) params.put("comprobante", comprobante);
        if (metodoPago != null) params.put("metodoPago", metodoPago.name());
        return params;
    }
}
