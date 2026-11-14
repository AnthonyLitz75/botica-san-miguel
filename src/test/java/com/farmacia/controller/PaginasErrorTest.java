package com.farmacia.controller;

import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 013 (CA-2.1, CA-2.2): 403, 404 y 500 usan las plantillas propias de templates/error y no muestran
 * detalles internos. MockMvc no reenvía los errores al contenedor, así que se invoca /error con los mismos
 * atributos que pondría el servidor. Usa el contexto completo (la resolución de vistas de error y las
 * propiedades server.error.* no se cargan en un @WebMvcTest).
 */
@AutoConfigureMockMvc
class PaginasErrorTest extends AbstractRepositorioIntegrationTest {

    @Autowired private MockMvc mvc;

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "403, Acceso denegado",
            "404, Página no encontrada",
            "500, Ocurrió un problema"
    })
    @DisplayName("CA-2.1: cada código usa su página propia en español con enlace al inicio")
    void paginaPropiaPorCodigo(int codigo, String titulo) throws Exception {
        mvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, codigo)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/ventas/99")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is(codigo))
                .andExpect(content().string(containsString(titulo)))
                .andExpect(content().string(containsString("ERROR " + codigo)))
                .andExpect(content().string(containsString("Volver al inicio")))
                .andExpect(content().string(not(containsString("Whitelabel"))));
    }

    @Test
    @DisplayName("CA-2.2: un error 500 no muestra la excepción, su mensaje ni la traza")
    void error500SinDetallesInternos() throws Exception {
        var excepcion = new IllegalStateException("SELECT * FROM usuario WHERE clave_hash = 'secreto'");
        mvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, excepcion)
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, excepcion.getMessage())
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("secreto"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.farmacia"))));
    }

    @Test
    @DisplayName("CA-2.2: la respuesta JSON de error tampoco incluye mensaje ni traza")
    void errorJsonSinDetallesInternos() throws Exception {
        var excepcion = new IllegalStateException("detalle interno");
        mvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, excepcion)
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, excepcion.getMessage())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("detalle interno"))))
                .andExpect(content().string(not(containsString("trace"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
