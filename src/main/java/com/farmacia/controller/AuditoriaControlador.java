package com.farmacia.controller;

import com.farmacia.dto.FiltroAuditoriaFormulario;
import com.farmacia.service.AccionAuditoria;
import com.farmacia.service.AuditoriaServicio;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Consulta de la bitácora de auditoría para el ADMINISTRADOR (spec 014, US-4). */
@Controller
public class AuditoriaControlador {
    private static final List<String> RESULTADOS = List.of("EXITO", "FALLO", "DENEGADO");
    private static final List<String> ENTIDADES = List.of("SESION", "RUTA", "MEDICAMENTO", "PROVEEDOR", "CLIENTE",
            "ORDEN_COMPRA", "LOTE", "VENTA", "AUDITORIA");

    private final AuditoriaServicio servicio;

    public AuditoriaControlador(AuditoriaServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/auditoria")
    public String listar(@ModelAttribute("filtro") FiltroAuditoriaFormulario filtro,
            @RequestParam(name = "pagina", defaultValue = "1") int pagina,
            Authentication autenticacion, Model model) {
        model.addAttribute("acciones", Arrays.stream(AccionAuditoria.values()).map(Enum::name).toList());
        model.addAttribute("resultados", RESULTADOS);
        model.addAttribute("entidades", ENTIDADES);
        try {
            model.addAttribute("consulta", servicio.consultar(autenticacion.getName(), filtro, pagina));
        } catch (IllegalArgumentException error) {
            model.addAttribute("error", error.getMessage());
        }
        return "auditoria/listado";
    }

    @GetMapping("/auditoria.csv")
    public ResponseEntity<byte[]> exportar(@ModelAttribute FiltroAuditoriaFormulario filtro,
            Authentication autenticacion) {
        String csv = servicio.exportarCsv(autenticacion.getName(), filtro);
        // BOM UTF-8 para que las hojas de cálculo muestren bien las tildes
        byte[] cuerpo = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auditoria.csv\"")
                .body(cuerpo);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> filtroInvalido(IllegalArgumentException error) {
        return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(error.getMessage());
    }
}
