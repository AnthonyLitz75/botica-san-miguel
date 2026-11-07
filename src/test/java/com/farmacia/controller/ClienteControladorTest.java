package com.farmacia.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.Cliente;
import com.farmacia.service.ClienteServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClienteControlador.class)
@Import(SeguridadConfiguracion.class)
class ClienteControladorTest {
    private static final String CLIENTE_JSON = """
            {"tipoDocumento":"DNI","numeroDocumento":"12345678","nombreCompleto":"Ana Pérez",
             "telefono":"987654321","correo":"ana@example.com"}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private ClienteServicio servicio;
    @MockitoBean private UsuarioDetallesServicio usuarios;

    @BeforeEach
    void configurarUsuarios() {
        when(usuarios.loadUserByUsername(any())).thenAnswer(invocacion -> {
            String nombre = invocacion.getArgument(0);
            String rol = switch (nombre) {
                case "admin" -> "ADMINISTRADOR";
                case "vendedor" -> "VENDEDOR";
                case "compras" -> "COMPRAS";
                default -> throw new UsernameNotFoundException("Usuario no encontrado");
            };
            return User.withUsername(nombre).password("{noop}clave").roles(rol).build();
        });
    }

    @Test
    void permiteConsultarAlVendedorYBloqueaCompras() throws Exception {
        when(servicio.buscarPorId(4)).thenReturn(Optional.of(
                new Cliente(4, "DNI", "12345678", "Ana Pérez", null, null)));

        mvc.perform(get("/api/clientes/4").header("Authorization", credenciales("vendedor")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombreCompleto").value("Ana Pérez"));
        mvc.perform(get("/api/clientes/4").header("Authorization", credenciales("compras")))
                .andExpect(status().isForbidden());
    }

    @Test
    void validaCreacionYTraduceDuplicados() throws Exception {
        when(servicio.guardar(any())).thenReturn(4);
        when(servicio.buscarPorId(4)).thenReturn(Optional.of(
                new Cliente(4, "DNI", "12345678", "Ana Pérez", "987654321", "ana@example.com")));
        mvc.perform(post("/api/clientes").header("Authorization", credenciales("vendedor"))
                        .contentType("application/json").content(CLIENTE_JSON))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.idCliente").value(4));

        mvc.perform(post("/api/clientes").header("Authorization", credenciales("vendedor"))
                        .contentType("application/json")
                        .content(CLIENTE_JSON.replace("12345678", "123")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.numeroDocumento").exists());

        when(servicio.guardar(any())).thenThrow(new DuplicateKeyException("duplicado"));
        mvc.perform(post("/api/clientes").header("Authorization", credenciales("admin"))
                        .contentType("application/json").content(CLIENTE_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void actualizaClienteExistenteYDevuelve404CuandoNoExiste() throws Exception {
        Cliente cliente = new Cliente(4, "DNI", "12345678", "Ana Pérez", "987654321", "ana@example.com");
        when(servicio.actualizar(org.mockito.ArgumentMatchers.eq(4), any())).thenReturn(true);
        when(servicio.buscarPorId(4)).thenReturn(Optional.of(cliente));
        when(servicio.buscarPorId(999)).thenReturn(Optional.empty());

        mvc.perform(put("/api/clientes/4").header("Authorization", credenciales("vendedor"))
                        .contentType("application/json").content(CLIENTE_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idCliente").value(4));
        mvc.perform(get("/api/clientes/999").header("Authorization", credenciales("vendedor")))
                .andExpect(status().isNotFound());
    }

    private String credenciales(String usuario) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (usuario + ":clave").getBytes(StandardCharsets.UTF_8));
    }
}
