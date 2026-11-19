package com.farmacia.service;

import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.farmacia.dto.ClienteFormulario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClienteServicioPersistenciaTest extends AbstractRepositorioIntegrationTest {
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

    @Test
    void listarTraeComprasCorrectasParaClientesYRespetaFiltroTexto() {
        int idUsuario = jdbcTemplate.queryForObject("""
                INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol)
                VALUES ('Cajero Test T1901', 'cajero_t1901', 'x', 'VENDEDOR') RETURNING id_usuario
                """, Integer.class);

        Integer idCli0 = servicio.guardar(formulario("DNI", "11112222", "Carlos Sin Compras"));
        Integer idCli1 = servicio.guardar(formulario("DNI", "33334444", "Beto Una Compra"));
        Integer idCli12 = servicio.guardar(formulario("DNI", "55556666", "Ana Doce Compras"));

        // 1 venta para Beto
        jdbcTemplate.update("""
                INSERT INTO venta(id_usuario, id_cliente, subtotal, total, numero_comprobante)
                VALUES (?, ?, 10.00, 10.00, 'B001-90000001')
                """, idUsuario, idCli1);

        // 12 ventas para Ana
        for (int i = 1; i <= 12; i++) {
            jdbcTemplate.update("""
                    INSERT INTO venta(id_usuario, id_cliente, subtotal, total, numero_comprobante)
                    VALUES (?, ?, 10.00, 10.00, ?)
                    """, idUsuario, idCli12, String.format("B001-91%06d", i));
        }

        var listado = servicio.listar("");
        var c0 = listado.stream().filter(c -> c.idCliente().equals(idCli0)).findFirst().orElseThrow();
        assertEquals(0L, c0.compras(), "Cliente sin compras debe tener 0");

        var c1 = listado.stream().filter(c -> c.idCliente().equals(idCli1)).findFirst().orElseThrow();
        assertEquals(1L, c1.compras(), "Cliente con 1 venta debe tener 1 compra");

        var c12 = listado.stream().filter(c -> c.idCliente().equals(idCli12)).findFirst().orElseThrow();
        assertEquals(12L, c12.compras(), "Cliente con 12 ventas debe tener 12 compras");

        // CA-2.3: Filtro por texto conserva el conteo
        var filtroNombre = servicio.listar("Doce");
        assertEquals(1, filtroNombre.size());
        assertEquals(idCli12, filtroNombre.get(0).idCliente());
        assertEquals(12L, filtroNombre.get(0).compras());

        var filtroDoc = servicio.listar("33334444");
        assertEquals(1, filtroDoc.size());
        assertEquals(idCli1, filtroDoc.get(0).idCliente());
        assertEquals(1L, filtroDoc.get(0).compras());
    }

    private ClienteFormulario formulario(String tipo, String numero, String nombre) {
        return new ClienteFormulario(tipo, numero, nombre, "987654321", "ana@example.com");
    }
}
