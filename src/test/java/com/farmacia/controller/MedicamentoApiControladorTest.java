package com.farmacia.controller;

import com.farmacia.config.SeguridadConfiguracion;
import com.farmacia.model.Medicamento;
import com.farmacia.service.MedicamentoServicio;
import com.farmacia.service.UsuarioDetallesServicio;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MedicamentoApiControlador.class)
@Import(SeguridadConfiguracion.class)
class MedicamentoApiControladorTest {

        private static final String MEDICAMENTO_JSON = """
                        {"codigo":"MED-003","nombre":"Amoxicilina","concentracion":"500 mg",
                         "presentacion":"Caja de 10 capsulas","unidadControl":"CAJA",
                         "precioVenta":12.50,"stockMinimo":5}
                        """;

        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private MedicamentoServicio servicio;

        @MockitoBean
        private UsuarioDetallesServicio usuarios;

        @BeforeEach
        void configurarUsuarios() {
                when(usuarios.loadUserByUsername(any())).thenAnswer(invocacion -> {
                        String nombre = invocacion.getArgument(0);
                        String rol = switch (nombre) {
                                case "admin" -> "ADMINISTRADOR";
                                case "vendedor" -> "VENDEDOR";
                                default -> throw new UsernameNotFoundException("Usuario no encontrado");
                        };
                        return User.withUsername(nombre).password("{noop}clave").roles(rol).build();
                });
        }

        @Test
        void requiereAutenticacionBasic() throws Exception {
                mvc.perform(get("/api/medicamentos"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void vendedorPuedeConsultarPeroNoModificar() throws Exception {
                when(servicio.buscarPorEstado("", true)).thenReturn(List.of(medicamento()));

                mvc.perform(get("/api/medicamentos").header("Authorization", credenciales("vendedor")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].codigo").value("MED-003"));

                mvc.perform(post("/api/medicamentos").header("Authorization", credenciales("vendedor"))
                                .contentType(MediaType.APPLICATION_JSON).content(MEDICAMENTO_JSON))
                                .andExpect(status().isForbidden());

                mvc.perform(put("/api/medicamentos/7").header("Authorization", credenciales("vendedor"))
                                .contentType(MediaType.APPLICATION_JSON).content(MEDICAMENTO_JSON))
                                .andExpect(status().isForbidden());

                mvc.perform(delete("/api/medicamentos/7").header("Authorization", credenciales("vendedor")))
                                .andExpect(status().isForbidden());
        }

        @Test
        void administradorPuedeCrearEditarYDesactivar() throws Exception {
                when(servicio.guardar(any())).thenReturn(7);
                when(servicio.buscarPorId(7)).thenReturn(Optional.of(medicamento()));
                when(servicio.actualizar(eq(7), any())).thenReturn(true);
                when(servicio.desactivar(7)).thenReturn(true);

                mvc.perform(post("/api/medicamentos").header("Authorization", credenciales("admin"))
                                .contentType(MediaType.APPLICATION_JSON).content(MEDICAMENTO_JSON))
                                .andExpect(status().isCreated())
                                .andExpect(header().string("Location", "/api/medicamentos/7"))
                                .andExpect(jsonPath("$.idMedicamento").value(7));

                mvc.perform(put("/api/medicamentos/7").header("Authorization", credenciales("admin"))
                                .contentType(MediaType.APPLICATION_JSON).content(MEDICAMENTO_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.nombre").value("Amoxicilina"));

                mvc.perform(delete("/api/medicamentos/7").header("Authorization", credenciales("admin")))
                                .andExpect(status().isNoContent());

                verify(servicio).desactivar(7);
        }

        @Test
        void validaDatosYCodigoDuplicado() throws Exception {
                mvc.perform(post("/api/medicamentos").header("Authorization", credenciales("admin"))
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.campos.codigo").exists());

                when(servicio.guardar(any())).thenThrow(new DuplicateKeyException("codigo duplicado"));
                mvc.perform(post("/api/medicamentos").header("Authorization", credenciales("admin"))
                                .contentType(MediaType.APPLICATION_JSON).content(MEDICAMENTO_JSON))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void idInexistenteDevuelve404() throws Exception {
                mvc.perform(get("/api/medicamentos/999").header("Authorization", credenciales("vendedor")))
                                .andExpect(status().isNotFound());

                mvc.perform(delete("/api/medicamentos/999").header("Authorization", credenciales("admin")))
                                .andExpect(status().isNotFound());
        }

        private String credenciales(String nombre) {
                String valor = nombre + ":clave";
                return "Basic " + Base64.getEncoder().encodeToString(valor.getBytes(StandardCharsets.UTF_8));
        }

        private Medicamento medicamento() {
                return new Medicamento(7, "MED-003", "Amoxicilina", "500 mg",
                                "Caja de 10 capsulas", "CAJA", new BigDecimal("12.50"), 5, true);
        }
}
