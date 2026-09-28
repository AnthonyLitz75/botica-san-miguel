package com.farmacia.controller;

import com.farmacia.service.MedicamentoServicio;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MedicamentoControladorTest {
    @ParameterizedTest
    @CsvSource({
        "ADMINISTRADOR, Administrador, true, true, true",
        "ALMACENERO, Almacenero, false, true, true",
        "COMPRAS, Compras, false, true, false",
        "VENDEDOR, Vendedor, false, false, false"
    })
    void adaptaInicioAlRol(String rol, String etiqueta, boolean administra,
            boolean consultaCompras, boolean consultaMovimientos) {
        MedicamentoServicio servicio = mock(MedicamentoServicio.class);
        when(servicio.buscarPorEstado("", true)).thenReturn(List.of());
        var autenticacion = UsernamePasswordAuthenticationToken.authenticated(
                "usuario_demo", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
        var modelo = new ExtendedModelMap();
        new NavegacionCompartida().configurarNavegacion(modelo, autenticacion);

        String vista = new MedicamentoControlador(servicio).listar("", true, modelo, autenticacion);

        assertEquals("medicamentos/listado", vista);
        assertEquals("usuario_demo", modelo.get("nombreUsuario"));
        assertEquals(etiqueta, modelo.get("rolUsuario"));
        assertEquals(administra, modelo.get("esAdministrador"));
        assertEquals(consultaCompras, modelo.get("puedeConsultarProveedores"));
        assertEquals(consultaCompras, modelo.get("puedeConsultarOrdenesCompra"));
        assertEquals(consultaMovimientos, modelo.get("puedeConsultarMovimientos"));
    }
}
