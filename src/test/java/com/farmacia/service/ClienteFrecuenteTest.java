package com.farmacia.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
    }

    @Test
    @DisplayName("CA-1.3: Con umbral configurado dinámicamente (ej. 5), se adapta al valor indicado")
    void umbralConfigurable() {
        ClienteServicio servicio = new ClienteServicio(mock(ClienteRepositorio.class), mock(AuditoriaServicio.class), 5);

        assertFalse(servicio.esClienteFrecuente(4), "4 compras con umbral 5 no es frecuente");
        assertTrue(servicio.esClienteFrecuente(5), "5 compras con umbral 5 debe ser frecuente");
        assertTrue(servicio.esClienteFrecuente(6), "6 compras con umbral 5 debe ser frecuente");
    }
}
