package com.farmacia.controller;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {
    MedicamentoControlador.class, ProveedorControlador.class,
    OrdenCompraControlador.class, MovimientoInventarioControlador.class,
    LoteControlador.class, SalidaInventarioControlador.class
})
public class NavegacionCompartida {
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
        modelo.addAttribute("puedeConsultarMovimientos", roles.contains("ROLE_ADMINISTRADOR")
                || roles.contains("ROLE_ALMACENERO"));
    }

}
