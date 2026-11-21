package com.farmacia.controller;

import com.farmacia.service.AlertaInventarioServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {
                MedicamentoControlador.class, ProveedorControlador.class,
                OrdenCompraControlador.class, MovimientoInventarioControlador.class,
                LoteControlador.class, SalidaInventarioControlador.class,
                PanelAlertaControlador.class, ClienteWebControlador.class,
                VentaWebControlador.class, AuditoriaControlador.class,
                HistorialVentasControlador.class, ExistenciaInicialControlador.class,
                UsuarioControlador.class, ReporteControlador.class,
                CategoriaControlador.class
})
public class NavegacionCompartida {
        private final AlertaInventarioServicio alertaInventarioServicio;

        public NavegacionCompartida(@Autowired(required = false) AlertaInventarioServicio alertaInventarioServicio) {
                this.alertaInventarioServicio = alertaInventarioServicio;
        }

        @ModelAttribute
        public void configurarNavegacion(Model modelo, Authentication autenticacion) {
                var roles = autenticacion.getAuthorities().stream()
                                .map(autoridad -> autoridad.getAuthority())
                                .toList();
                modelo.addAttribute("nombreUsuario", autenticacion.getName());
                modelo.addAttribute("esAdministrador", roles.contains("ROLE_ADMINISTRADOR"));
                String rolUsuario = roles.stream()
                                .map(rol -> switch (rol) {
                                        case "ROLE_ADMINISTRADOR" -> "Administrador";
                                        case "ROLE_COMPRAS" -> "Compras";
                                        case "ROLE_ALMACENERO" -> "Almacenero";
                                        case "ROLE_VENDEDOR" -> "Vendedor";
                                        default -> "";
                                })
                                .filter(rol -> !rol.isEmpty()).findFirst().orElse("Usuario");
                modelo.addAttribute("rolUsuario", rolUsuario);
                boolean consultaCompras = roles.contains("ROLE_ADMINISTRADOR")
                                || roles.contains("ROLE_COMPRAS") || roles.contains("ROLE_ALMACENERO");
                modelo.addAttribute("puedeConsultarProveedores", consultaCompras);
                modelo.addAttribute("puedeConsultarOrdenesCompra", consultaCompras);
                boolean puedeConsultarAlertas = roles.stream().anyMatch(rol ->
                                rol.equals("ROLE_ADMINISTRADOR") || rol.equals("ROLE_COMPRAS")
                                        || rol.equals("ROLE_ALMACENERO") || rol.equals("ROLE_VENDEDOR"));
                modelo.addAttribute("puedeConsultarAlertas", puedeConsultarAlertas);
                if (puedeConsultarAlertas && alertaInventarioServicio != null) {
                        try {
                                modelo.addAttribute("totalAlertas", alertaInventarioServicio.totalAlertasActivas());
                        } catch (Exception e) {
                                modelo.addAttribute("totalAlertas", 0);
                        }
                }
                modelo.addAttribute("puedeConsultarClientes", roles.contains("ROLE_ADMINISTRADOR")
                                || roles.contains("ROLE_VENDEDOR"));
                boolean puedePrepararVenta = roles.contains("ROLE_ADMINISTRADOR")
                                || roles.contains("ROLE_VENDEDOR");
                modelo.addAttribute("puedePrepararVenta", puedePrepararVenta);
                modelo.addAttribute("puedeConsultarVentas", roles.contains("ROLE_ADMINISTRADOR"));
                modelo.addAttribute("puedeVerMisVentas", roles.contains("ROLE_VENDEDOR"));
                boolean puedeConsultarMovimientos = roles.contains("ROLE_ADMINISTRADOR")
                                || roles.contains("ROLE_ALMACENERO");
                modelo.addAttribute("puedeConsultarMovimientos", puedeConsultarMovimientos);
                modelo.addAttribute("puedeRegistrarSalida", puedeConsultarMovimientos);
                modelo.addAttribute("puedeConsultarAuditoria", roles.contains("ROLE_ADMINISTRADOR"));
                modelo.addAttribute("puedeGestionarUsuarios", roles.contains("ROLE_ADMINISTRADOR"));
                modelo.addAttribute("puedeVerReportes", roles.contains("ROLE_ADMINISTRADOR"));
        }

}
