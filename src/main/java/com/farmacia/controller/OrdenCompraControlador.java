package com.farmacia.controller;

import com.farmacia.service.OrdenCompraServicio;
import com.farmacia.dto.RecepcionOrdenFormulario;
import com.farmacia.dto.DetalleRecepcionFormulario;
import com.farmacia.model.LineaRecepcionVista;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.farmacia.service.ProveedorServicio;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.dto.OrdenCompraFormulario;
import com.farmacia.dto.DetalleOrdenCompraFormulario;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@Controller
@RequestMapping("/ordenes-compra")
public class OrdenCompraControlador {

        private final OrdenCompraServicio servicio;
        private final ProveedorServicio proveedorServicio;
        private final MedicamentoServicio medicamentoServicio;

        public OrdenCompraControlador(
                        OrdenCompraServicio servicio,
                        ProveedorServicio proveedorServicio,
                        MedicamentoServicio medicamentoServicio) {

                this.servicio = servicio;
                this.proveedorServicio = proveedorServicio;
                this.medicamentoServicio = medicamentoServicio;
        }

        @GetMapping
        public String listar(
                        Model model,
                        Authentication autenticacion) {

                boolean puedeCrearOrden = autenticacion.getAuthorities()
                                .stream()
                                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMINISTRADOR")
                                                || autoridad.getAuthority().equals("ROLE_COMPRAS"));

                model.addAttribute("ordenes", servicio.listar());
                model.addAttribute("puedeCrearOrden", puedeCrearOrden);

                return "ordenes-compra/listado";
        }

        @GetMapping("/nuevo")
        public String nuevo(
                        @RequestParam(name = "medicamentoId", required = false) Integer medicamentoId,
                        @RequestParam(name = "cantidad", required = false) Integer cantidad,
                        Model model) {
                Integer idMedicamentoInicial = null;
                Integer cantidadInicial = null;
                if (medicamentoId != null && medicamentoId > 0 && cantidad != null && cantidad > 0
                                && medicamentoServicio.listarActivos().stream()
                                                .anyMatch(medicamento -> medicamento.idMedicamento().equals(medicamentoId))) {
                        idMedicamentoInicial = medicamentoId;
                        cantidadInicial = cantidad;
                }
                model.addAttribute(
                                "formulario",
                                new OrdenCompraFormulario(
                                                null,
                                                "",
                                                List.of(new DetalleOrdenCompraFormulario(
                                                                idMedicamentoInicial, cantidadInicial, null))));

                cargarOpciones(model);

                return "ordenes-compra/formulario";
        }

        private void cargarOpciones(Model model) {
                model.addAttribute(
                                "proveedores",
                                proveedorServicio.listarActivos());

                model.addAttribute(
                                "medicamentos",
                                medicamentoServicio.listarActivos());
        }

        @PostMapping
        public String guardar(
                        @Valid @ModelAttribute("formulario") OrdenCompraFormulario formulario,
                        BindingResult resultado,
                        Model model,
                        Authentication autenticacion,
                        RedirectAttributes redireccion) {

                if (resultado.hasErrors()) {
                        cargarOpciones(model);
                        return "ordenes-compra/formulario";
                }

                try {
                        servicio.guardar(autenticacion.getName(), formulario);
                } catch (IllegalArgumentException excepcion) {
                        resultado.reject(
                                        "orden.invalida",
                                        excepcion.getMessage());

                        cargarOpciones(model);
                        return "ordenes-compra/formulario";
                }

                redireccion.addFlashAttribute(
                                "mensaje",
                                "Orden de compra registrada correctamente");

                return "redirect:/ordenes-compra";
        }

        @GetMapping("/{idOrden}/recepcion")
        public String prepararRecepcion(@PathVariable("idOrden") Integer idOrden, Model model) {
                var orden = servicio.buscarPorId(idOrden).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Orden de compra no encontrada"));
                if (!orden.estado().equals("PENDIENTE") && !orden.estado().equals("PARCIALMENTE_RECIBIDA"))
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden no admite recepción");
                List<LineaRecepcionVista> lineas = servicio.listarLineasRecepcion(idOrden);
                model.addAttribute("orden", orden);
                model.addAttribute("lineas", lineas);
                model.addAttribute("totalPendiente", lineas.stream()
                        .mapToInt(LineaRecepcionVista::cantidadPendiente).sum());
                model.addAttribute("minFechaVencimiento", LocalDate.now().plusDays(1));
                model.addAttribute("formulario", new RecepcionOrdenFormulario(
                        lineas.stream().map(linea -> new DetalleRecepcionFormulario(
                                linea.idDetalleOrden(), "", null, null)).toList(), ""));
                return "ordenes-compra/recepcion";
        }

        @PostMapping("/{idOrden}/recepcion")
        public String registrarRecepcion(@PathVariable("idOrden") Integer idOrden,
                        @Valid @ModelAttribute("formulario") RecepcionOrdenFormulario formulario,
                        BindingResult resultado, Authentication autenticacion, RedirectAttributes redireccion) {
                if (resultado.hasErrors()) {
                        redireccion.addFlashAttribute("error", "Revisa lote, vencimiento y cantidad de cada producto seleccionado");
                        return "redirect:/ordenes-compra/" + idOrden + "/recepcion";
                }
                try {
                        servicio.registrarRecepcion(idOrden, autenticacion.getName(), formulario);
                        redireccion.addFlashAttribute("mensaje", "Recepción registrada correctamente");
                } catch (IllegalArgumentException excepcion) {
                        redireccion.addFlashAttribute("error", excepcion.getMessage());
                }
                return "redirect:/ordenes-compra/" + idOrden;
        }

        @PostMapping("/{idOrden}/cancelar")
        public String cancelar(@PathVariable("idOrden") Integer idOrden,
                        Authentication autenticacion, RedirectAttributes redireccion) {
                try {
                        servicio.cancelarOrden(idOrden, autenticacion.getName());
                        redireccion.addFlashAttribute("mensaje", "Orden cancelada correctamente");
                } catch (IllegalArgumentException excepcion) {
                        redireccion.addFlashAttribute("error", excepcion.getMessage());
                }
                return "redirect:/ordenes-compra/" + idOrden;
        }

        @GetMapping("/{idOrden}")
        public String detalle(
                        @PathVariable("idOrden") Integer idOrden,
                        Model model,
                        Authentication autenticacion) {

                var orden = servicio.buscarPorId(idOrden)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Orden de compra no encontrada"));

                model.addAttribute("orden", orden);
                model.addAttribute(
                                "detalles",
                                servicio.listarDetallesVista(idOrden));

                var proveedor = proveedorServicio
                                .buscarPorId(orden.idProveedor())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Proveedor no encontrado"));

                model.addAttribute("proveedor", proveedor);
                boolean administrador = tieneRol(autenticacion, "ROLE_ADMINISTRADOR");
                boolean compras = tieneRol(autenticacion, "ROLE_COMPRAS");
                boolean almacenero = tieneRol(autenticacion, "ROLE_ALMACENERO");
                model.addAttribute("puedeCancelar", (administrador || compras)
                        && orden.estado().equals("PENDIENTE"));
                model.addAttribute("puedeRecibir", (administrador || almacenero)
                        && (orden.estado().equals("PENDIENTE") || orden.estado().equals("PARCIALMENTE_RECIBIDA")));
                return "ordenes-compra/detalle";
        }

        private boolean tieneRol(Authentication autenticacion, String rol) {
                return autenticacion.getAuthorities().stream()
                        .anyMatch(autoridad -> autoridad.getAuthority().equals(rol));
        }
}
