package com.farmacia.service;

import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LotePrioridadServicioPersistenciaTest extends AbstractRepositorioIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LotePrioridadServicio servicio;
    private int medicamentoId;

    @BeforeEach
    void crearMedicamento() {
        String codigo = "FEFO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        medicamentoId = jdbc.queryForObject("""
                INSERT INTO medicamento(codigo,nombre,presentacion,unidad_control,precio_venta)
                VALUES (?, 'Medicamento FEFO', 'Caja', 'CAJA', 1) RETURNING id_medicamento
                """, Integer.class, codigo);
    }

    @AfterEach
    void limpiar() {
        if (medicamentoId != 0) {
            jdbc.update("DELETE FROM lote WHERE id_medicamento = ?", medicamentoId);
            jdbc.update("DELETE FROM medicamento WHERE id_medicamento = ?", medicamentoId);
        }
    }

    @Test
    void consultaLotesVigentesYProponeElMasProximoDesdePostgresql() {
        jdbc.update("INSERT INTO lote(id_medicamento,numero_lote,fecha_vencimiento,stock_actual) VALUES (?,?,?,?)",
                medicamentoId, "FEFO-LEJANO", LocalDate.now().plusDays(50), 8);
        jdbc.update("INSERT INTO lote(id_medicamento,numero_lote,fecha_vencimiento,stock_actual) VALUES (?,?,?,?)",
                medicamentoId, "FEFO-VENCIDO", LocalDate.now().minusDays(1), 10);
        jdbc.update("INSERT INTO lote(id_medicamento,numero_lote,fecha_vencimiento,stock_actual) VALUES (?,?,?,?)",
                medicamentoId, "FEFO-CERCANO", LocalDate.now().plusDays(5), 2);

        var asignaciones = servicio.planificarSalida(medicamentoId, 5);

        assertEquals(2, asignaciones.size());
        assertEquals("FEFO-CERCANO", asignaciones.getFirst().numeroLote());
        assertEquals(2, asignaciones.getFirst().cantidadAsignada());
        assertEquals("FEFO-LEJANO", asignaciones.getLast().numeroLote());
        assertEquals(3, asignaciones.getLast().cantidadAsignada());
        assertThrows(IllegalArgumentException.class,
                () -> servicio.planificarSalida(medicamentoId, 21));
    }
}
