package com.farmacia.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

class AvisoFlashTest {

    @Test
    @DisplayName("guardar con parámetros válidos crea FlashMap, asigna atributos y persiste en manager")
    void guardar_conParametrosValidos_asignaAtributosFlashYPersiste() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        SessionFlashMapManager manager = new SessionFlashMapManager();
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, manager);

        AvisoFlash.guardar(request, response, "/usuarios?creado=1", "Usuario registrado exitosamente");

        FlashMap flashMap = (FlashMap) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
        assertThat((Object) flashMap).isNotNull();
        assertThat(flashMap.get("mensaje")).isEqualTo("Usuario registrado exitosamente");
        assertThat(flashMap.get("mensajeExito")).isEqualTo("Usuario registrado exitosamente");
        assertThat(flashMap.getTargetRequestPath()).isEqualTo("/usuarios");
    }

    @Test
    @DisplayName("guardar con FlashMap preexistente reutiliza la instancia y asigna atributos")
    void guardar_conFlashMapExistente_reutilizaInstancia() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        SessionFlashMapManager manager = new SessionFlashMapManager();
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, manager);

        FlashMap previo = new FlashMap();
        previo.put("otro", "valor");
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, previo);

        AvisoFlash.guardar(request, response, "/clientes", "Cliente guardado");

        FlashMap flashMap = (FlashMap) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
        assertThat((Object) flashMap).isSameAs(previo);
        assertThat(flashMap.get("otro")).isEqualTo("valor");
        assertThat(flashMap.get("mensaje")).isEqualTo("Cliente guardado");
        assertThat(flashMap.getTargetRequestPath()).isEqualTo("/clientes");
    }

    @Test
    @DisplayName("guardar sin manager o sin response no lanza excepción")
    void guardar_sinManagerOSinResponse_noLanzaExcepcion() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatCode(() -> AvisoFlash.guardar(request, null, "/proveedores", "Proveedor guardado"))
                .doesNotThrowAnyException();

        FlashMap flashMap = (FlashMap) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
        assertThat((Object) flashMap).isNotNull();
        assertThat(flashMap.get("mensaje")).isEqualTo("Proveedor guardado");
    }

    @Test
    @DisplayName("guardar con request nulo, mensaje nulo o en blanco no realiza ninguna acción")
    void guardar_parametrosInvalidos_noHaceNada() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatCode(() -> AvisoFlash.guardar(null, response, "/usuarios", "Mensaje"))
                .doesNotThrowAnyException();

        MockHttpServletRequest request = new MockHttpServletRequest();
        AvisoFlash.guardar(request, response, "/usuarios", null);
        assertThat((Object) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE)).isNull();

        AvisoFlash.guardar(request, response, "/usuarios", "   ");
        assertThat((Object) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("guardar con targetPath nulo o en blanco no asigna ruta de destino")
    void guardar_targetPathVacio_noAsignaRutaDestino() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AvisoFlash.guardar(request, response, null, "Mensaje sin ruta");
        FlashMap flashMap = (FlashMap) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
        assertThat((Object) flashMap).isNotNull();
        assertThat(flashMap.getTargetRequestPath()).isNull();

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        AvisoFlash.guardar(request2, response, "   ", "Mensaje con ruta en blanco");
        FlashMap flashMap2 = (FlashMap) request2.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
        assertThat((Object) flashMap2).isNotNull();
        assertThat(flashMap2.getTargetRequestPath()).isNull();
    }

    @Test
    @DisplayName("Constructor privado de clase utilitaria es accesible vía reflexión")
    void constructorPrivado_coberturaReflexion() throws Exception {
        Constructor<AvisoFlash> constructor = AvisoFlash.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AvisoFlash instancia = constructor.newInstance();
        assertThat(instancia).isNotNull();
    }
}
