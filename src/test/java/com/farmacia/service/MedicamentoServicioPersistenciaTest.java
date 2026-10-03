package com.farmacia.service;

import com.farmacia.dto.MedicamentoFormulario;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class MedicamentoServicioPersistenciaTest {

    @Autowired
    private MedicamentoServicio servicio;

    @Test
    void creaActualizaYDesactivaSinBorrarElRegistro() {
        String codigo = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        var nuevo = new MedicamentoFormulario(codigo, "Prueba API", "500 mg",
                "Caja de prueba", "CAJA", new BigDecimal("12.50"), 5);

        Integer id = servicio.guardar(nuevo);
        assertNotNull(id);
        assertEquals(codigo, servicio.buscarPorId(id).orElseThrow().codigo());

        var editado = new MedicamentoFormulario(codigo, "Prueba editada", "500 mg",
                "Caja de prueba", "CAJA", new BigDecimal("15.00"), 5);
        assertTrue(servicio.actualizar(id, editado));
        assertEquals("Prueba editada", servicio.buscarPorId(id).orElseThrow().nombre());

        assertTrue(servicio.desactivar(id));
        assertFalse(servicio.buscarPorId(id).orElseThrow().activo());
    }
}
