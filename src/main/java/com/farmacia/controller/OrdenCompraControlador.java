package com.farmacia.controller;

import com.farmacia.service.OrdenCompraServicio;
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
import org.springframework.web.server.ResponseStatusException;

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
        public String nuevo(Model model) {
                model.addAttribute(
                                "formulario",
                                new OrdenCompraFormulario(
                                                null,
                                                "",
                                                List.of(new DetalleOrdenCompraFormulario(
                                                                null, null, null))));

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

        @GetMapping("/{idOrden}")
        public String detalle(
                        @PathVariable("idOrden") Integer idOrden,
                        Model model) {

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

                return "ordenes-compra/detalle";
        }
}