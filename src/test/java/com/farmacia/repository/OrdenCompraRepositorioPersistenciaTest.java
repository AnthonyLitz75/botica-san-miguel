package com.farmacia.repository;

import com.farmacia.model.OrdenCompraListadoVista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de persistencia para OrdenCompraRepositorio (spec 018, CA-1.1 a CA-1.3).
 */
class OrdenCompraRepositorioPersistenciaTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private OrdenCompraRepositorio repositorio;

    private Integer idUsuario;
    private Integer idProveedorActivo;
    private Integer idProveedorInactivo;

    @BeforeEach
    void prepararDatos() {
        limpiarBaseDeDatos();

        idUsuario = jdbcTemplate.queryForObject("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES ('Admin Pruebas', 'admin_test', 'hash123', 'ADMINISTRADOR', true)
                RETURNING id_usuario
                """, Integer.class);

        idProveedorActivo = jdbcTemplate.queryForObject("""
                INSERT INTO proveedor (ruc, razon_social, activo)
                VALUES ('20123456789', 'Droguería Farmaperú S.A.C.', true)
                RETURNING id_proveedor
                """, Integer.class);

        idProveedorInactivo = jdbcTemplate.queryForObject("""
                INSERT INTO proveedor (ruc, razon_social, activo)
                VALUES ('20987654321', 'Laboratorios Medifarma Inactivo', false)
                RETURNING id_proveedor
                """, Integer.class);
    }

    @Test
    @DisplayName("El listado trae la razon social de proveedor activo e inactivo con un solo query (CA-1.1, CA-1.2, CA-1.3)")
    void listadoTraeRazonSocialProveedorActivoEInactivo() {
        repositorio.guardar("OC-2026-0001", idUsuario, idProveedorActivo, new BigDecimal("120.00"), "Orden proveedor activo");
        repositorio.guardar("OC-2026-0002", idUsuario, idProveedorInactivo, new BigDecimal("250.00"), "Orden proveedor inactivo");

        List<OrdenCompraListadoVista> listado = repositorio.listarVista();

        assertNotNull(listado);
        assertEquals(2, listado.size(), "Debe retornar las 2 ordenes registradas");

        OrdenCompraListadoVista orden1 = listado.stream()
                .filter(o -> o.numeroOrden().equals("OC-2026-0001"))
                .findFirst()
                .orElseThrow();
        assertEquals("Droguería Farmaperú S.A.C.", orden1.razonSocialProveedor());
        assertEquals(idProveedorActivo, orden1.idProveedor());

        OrdenCompraListadoVista orden2 = listado.stream()
                .filter(o -> o.numeroOrden().equals("OC-2026-0002"))
                .findFirst()
                .orElseThrow();
        assertEquals("Laboratorios Medifarma Inactivo", orden2.razonSocialProveedor());
        assertEquals(idProveedorInactivo, orden2.idProveedor());
    }
}
