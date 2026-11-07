package com.farmacia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.farmacia.dto.ClienteFormulario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ClienteServicioPersistenciaTest {
    @Autowired private ClienteServicio servicio;

    @Test
    void creaBuscaActualizaYRechazaDocumentoDuplicado() {
        Integer id = servicio.guardar(formulario(" DNI ", "12345678", " Ana Pérez "));

        assertEquals("Ana Pérez", servicio.buscarPorId(id).orElseThrow().nombreCompleto());
        assertEquals(id, servicio.buscarPorDocumento("DNI", "12345678").orElseThrow().idCliente());
        assertTrue(servicio.listar("12345678").stream().anyMatch(cliente -> cliente.idCliente().equals(id)));

        assertTrue(servicio.actualizar(id, formulario("DNI", "12345678", "Ana María Pérez")));
        assertEquals("Ana María Pérez", servicio.buscarPorId(id).orElseThrow().nombreCompleto());
        assertThrows(DuplicateKeyException.class,
                () -> servicio.guardar(formulario("DNI", "12345678", "Otra Persona")));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.guardar(formulario("DNI", "20123456789", "RUC con tipo DNI")));
        assertFalse(servicio.actualizar(Integer.MAX_VALUE,
                formulario("DNI", "12345678", "No existe")));
    }

    private ClienteFormulario formulario(String tipo, String numero, String nombre) {
        return new ClienteFormulario(tipo, numero, nombre, "987654321", "ana@example.com");
    }
}
