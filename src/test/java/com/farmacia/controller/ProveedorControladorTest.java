package com.farmacia.controller;

import com.farmacia.service.ProveedorServicio;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProveedorControladorTest {
    @ParameterizedTest
    @CsvSource({
            "ADMINISTRADOR, Administrador, true, true, true",
            "COMPRAS, Compras, true, true, false",
            "ALMACENERO, Almacenero, false, true, true",
            "VENDEDOR, Vendedor, false, false, false"
    })
    void mantieneContextoYPermisos(String rol, String etiqueta, boolean gestiona,
            boolean consultaCompras, boolean consultaMovimientos) {
        ProveedorServicio servicio = mock(ProveedorServicio.class);
        when(servicio.buscarPorEstado("", true)).thenReturn(List.of());
        var controlador = new ProveedorControlador(servicio);
        var modelo = new ExtendedModelMap();
        var autenticacion = UsernamePasswordAuthenticationToken.authenticated(
                "usuario_demo", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));

        new NavegacionCompartida().configurarNavegacion(modelo, autenticacion);
        assertEquals("proveedores/listado", controlador.listar("", true, modelo, autenticacion));
        assertEquals("usuario_demo", modelo.get("nombreUsuario"));
        assertEquals(etiqueta, modelo.get("rolUsuario"));
        assertEquals(gestiona, modelo.get("puedeGestionarProveedores"));
        assertEquals(consultaCompras, modelo.get("puedeConsultarProveedores"));
        assertEquals(consultaCompras, modelo.get("puedeConsultarOrdenesCompra"));
        assertEquals(consultaMovimientos, modelo.get("puedeConsultarMovimientos"));
        controlador.nuevo(modelo);
        assertEquals(etiqueta, modelo.get("rolUsuario"));
    }
}
