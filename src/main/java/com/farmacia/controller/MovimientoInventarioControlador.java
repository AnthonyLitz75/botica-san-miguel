package com.farmacia.controller;

import com.farmacia.dto.FiltroMovimientosFormulario;
import com.farmacia.dto.PaginacionModelo;
import com.farmacia.model.MovimientoInventario;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.MovimientoInventarioServicio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/movimientos")
public class MovimientoInventarioControlador {

    private final MovimientoInventarioServicio servicio;
    private final MedicamentoServicio medicamentoServicio;

    public MovimientoInventarioControlador(
            MovimientoInventarioServicio servicio,
            MedicamentoServicio medicamentoServicio) {
        this.servicio = servicio;
        this.medicamentoServicio = medicamentoServicio;
    }

    @GetMapping
    public String listar(
            @ModelAttribute("filtro") FiltroMovimientosFormulario filtro,
            @RequestParam(name = "pagina", required = false) Integer pagina,
            HttpServletRequest request,
            Model model) {
        if (request != null) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("exito") != null) {
                model.addAttribute("exito", session.getAttribute("exito"));
                session.removeAttribute("exito");
            }
        }
        model.addAttribute("medicamentos", medicamentoServicio.listarActivos());
        model.addAttribute("usuarios", servicio.listarUsuariosActivos());

        try {
            Integer paginaEfectiva = pagina;
            if (paginaEfectiva == null && filtro != null && filtro.pagina() != null) {
                paginaEfectiva = filtro.pagina();
            }

            int paginaNum = paginaEfectiva != null && paginaEfectiva > 0 ? paginaEfectiva : 1;
            var kardex = servicio.listarKardex(filtro, paginaNum);
            model.addAttribute("movimientos", kardex.filas());
            model.addAttribute("total", kardex.total());
            model.addAttribute("pagina", kardex.pagina());
            model.addAttribute("paginas", kardex.paginas());

            Map<String, Object> params = filtro != null ? filtro.aParametros() : Map.of();
            model.addAttribute("paginacion", PaginacionModelo.crear(
                    kardex.pagina(), kardex.total(), MovimientoInventarioServicio.MOVIMIENTOS_POR_PAGINA,
                    "/movimientos", params));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("movimientos", List.of());
            model.addAttribute("total", 0);
            model.addAttribute("pagina", 1);
            model.addAttribute("paginas", 1);
            model.addAttribute("paginacion", PaginacionModelo.crear(
                    1, 0, MovimientoInventarioServicio.MOVIMIENTOS_POR_PAGINA,
                    "/movimientos", Map.of()));
        }

        return "movimientos/listado";
    }

    private static boolean esVacio(FiltroMovimientosFormulario filtro) {
        return filtro == null || (
                (filtro.tipo() == null || filtro.tipo().isBlank()) &&
                filtro.desde() == null &&
                filtro.hasta() == null &&
                (filtro.motivo() == null || filtro.motivo().isBlank()) &&
                filtro.idMedicamento() == null &&
                (filtro.numeroLote() == null || filtro.numeroLote().isBlank()) &&
                filtro.idUsuario() == null &&
                (filtro.pagina() == null || filtro.pagina() <= 1)
        );
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable("id") Integer id, Authentication authentication, Model model) {
        MovimientoInventario movimiento = servicio.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Movimiento no encontrado"));

        model.addAttribute("movimiento", movimiento);
        model.addAttribute("detalles", servicio.listarDetalles(id));

        String enlaceOrigen = servicio.resolverEnlaceOrigen(movimiento, authentication);
        if (enlaceOrigen != null) {
            model.addAttribute("enlaceOrigen", enlaceOrigen);
        }

        return "movimientos/detalle";
    }
}