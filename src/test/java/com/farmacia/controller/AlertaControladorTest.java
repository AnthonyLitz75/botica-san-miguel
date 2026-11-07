package com.farmacia.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.service.AlertaInventarioServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlertaControlador.class)
@Import(SeguridadConfiguracion.class)
class AlertaControladorTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AlertaInventarioServicio servicio;
    @MockitoBean private UsuarioDetallesServicio usuarios;

    @BeforeEach
    void configurarUsuarios() {
        when(usuarios.loadUserByUsername(org.mockito.ArgumentMatchers.any())).thenAnswer(invocacion -> {
            String nombre = invocacion.getArgument(0);
            String rol = switch (nombre) {
                case "admin" -> "ADMINISTRADOR";
                case "compras" -> "COMPRAS";
                case "almacen" -> "ALMACENERO";
                case "vendedor" -> "VENDEDOR";
                default -> throw new UsernameNotFoundException("Usuario no encontrado");
            };
            return User.withUsername(nombre).password("{noop}clave").roles(rol).build();
        });
    }

    @Test
    void todosLosRolesInternosPuedenConsultarReposicionYSinSesionSeRechaza() throws Exception {
        mvc.perform(get("/api/alertas/reposicion"))
                .andExpect(status().isUnauthorized());
        for (String usuario : new String[] { "admin", "compras", "almacen", "vendedor" }) {
            mvc.perform(get("/api/alertas/reposicion").header("Authorization", credenciales(usuario)))
                    .andExpect(status().isOk());
        }
        verify(servicio, org.mockito.Mockito.times(4)).necesidadesReposicion();
    }

    private String credenciales(String nombre) {
        return "Basic " + Base64.getEncoder().encodeToString((nombre + ":clave").getBytes());
    }
}
