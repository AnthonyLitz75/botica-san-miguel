package com.farmacia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.farmacia.model.ClienteResumenVista;
import com.farmacia.repository.ClienteRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T1902: Prueba del umbral esFrecuente (9 -> no, 10 -> si, configurable). (CA-1.3)
 */
class ClienteFrecuenteTest {

    @Test
    @DisplayName("CA-1.3: Con umbral por defecto (10), 9 compras no es frecuente y 10 sí lo es")
    void umbralPorDefectoDiez() {
        ClienteServicio servicio = new ClienteServicio(mock(ClienteRepositorio.class), mock(AuditoriaServicio.class));

        assertFalse(servicio.esClienteFrecuente(0), "0 compras no es frecuente");
        assertFalse(servicio.esClienteFrecuente(9), "9 compras no alcanza el umbral de 10");
        assertTrue(servicio.esClienteFrecuente(10), "10 compras debe ser frecuente");
        assertTrue(servicio.esClienteFrecuente(15), "15 compras supera el umbral y debe ser frecuente");
        assertEquals(10, servicio.getUmbralClienteFrecuente(), "El umbral por defecto debe ser 10");
    }

    @Test
    @DisplayName("CA-1.3: Con umbral configurado dinámicamente (ej. 5), se adapta al valor indicado")
    void umbralConfigurable() {
        ClienteServicio servicio = new ClienteServicio(mock(ClienteRepositorio.class), mock(AuditoriaServicio.class), 5);

        assertFalse(servicio.esClienteFrecuente(4), "4 compras con umbral 5 no es frecuente");
        assertTrue(servicio.esClienteFrecuente(5), "5 compras con umbral 5 debe ser frecuente");
        assertTrue(servicio.esClienteFrecuente(6), "6 compras con umbral 5 debe ser frecuente");
        assertEquals(5, servicio.getUmbralClienteFrecuente(), "El umbral configurado debe ser 5");
    }

    @Test
    @DisplayName("listar: normaliza texto nulo a vacio y limpia espacios al invocar repositorio")
    void listarNormalizaTexto() {
        var repo = mock(ClienteRepositorio.class);
        var auditoria = mock(AuditoriaServicio.class);
        ClienteServicio servicio = new ClienteServicio(repo, auditoria);

        org.mockito.Mockito.when(repo.listar("")).thenReturn(java.util.List.of(resumen("Cliente 1")));
        org.mockito.Mockito.when(repo.listar("Perez")).thenReturn(java.util.List.of(resumen("Juan Perez")));

        var resNulo = servicio.listar(null);
        assertEquals(1, resNulo.size());
        org.mockito.Mockito.verify(repo).listar("");

        var resConTexto = servicio.listar("  Perez  ");
        assertEquals(1, resConTexto.size());
        org.mockito.Mockito.verify(repo).listar("Perez");
    }

    @Test
    @DisplayName("CA-2.1: Cálculo de iniciales para avatar de clientes personales y jurídicos")
    void calculoInicialesAvatar() {
        assertEquals("AS", resumen("Alberto Antonio Suárez Díaz").iniciales());
        assertEquals("JP", resumen("Juan Pérez Quispe").iniciales());
        assertEquals("MT", resumen("María Torres León").iniciales());
        assertEquals("CS", resumen("Clínica Santa Rosa S.A.C.").iniciales());
        assertEquals("SO", resumen("Solo").iniciales());
        assertEquals("CL", resumen("").iniciales());
        assertEquals("CL", resumen(null).iniciales());
    }

    private ClienteResumenVista resumen(String nombre) {
        return new ClienteResumenVista(1, "DNI", "12345678", nombre, "987654321", "correo@test.pe", 0L);
    }
}
