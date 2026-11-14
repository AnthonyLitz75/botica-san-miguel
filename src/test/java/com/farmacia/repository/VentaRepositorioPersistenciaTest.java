package com.farmacia.repository;

import com.farmacia.dto.FiltroVentasFormulario;
import com.farmacia.model.VentaResumenVista;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de integración con persistencia real (PostgreSQL Testcontainers)
 * para VentaRepositorio.listar, contar y sumarTotal (Spec 005, T501).
 */
class VentaRepositorioPersistenciaTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private VentaRepositorio repositorio;

    private int idVendedor1;
    private int idVendedor2;
    private int idCliente1;

    @BeforeEach
    void preparar() {
        limpiarBaseDeDatos();

        idVendedor1 = jdbcTemplate.queryForObject("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol)
                VALUES ('Juan Perez', 'vendedor1', 'hash1', 'VENDEDOR')
                RETURNING id_usuario
                """, Integer.class);

        idVendedor2 = jdbcTemplate.queryForObject("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol)
                VALUES ('Maria Gomez', 'vendedor2', 'hash2', 'VENDEDOR')
                RETURNING id_usuario
                """, Integer.class);

        idCliente1 = jdbcTemplate.queryForObject("""
                INSERT INTO cliente (tipo_documento, numero_documento, nombre_completo)
                VALUES ('DNI', '99887766', 'Carlos Mendoza')
                RETURNING id_cliente
                """, Integer.class);
    }

    @AfterEach
    void limpiar() {
        limpiarBaseDeDatos();
    }

    private int insertarVenta(int idUsuario, Integer idCliente, BigDecimal subtotal,
            BigDecimal descuento, BigDecimal total, String comprobante,
            LocalDateTime fechaHora) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO venta (id_usuario, id_cliente, subtotal, descuento_total, total, numero_comprobante, fecha_hora)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        RETURNING id_venta
                        """,
                Integer.class, idUsuario, idCliente, subtotal, descuento, total, comprobante, fechaHora);
    }

    @Test
    @DisplayName("CA-1.1: Filtrar por rango de fechas devuelve solo las ventas del rango, de la más reciente a la más antigua")
    void listar_filtroRangoFechas() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime t1 = LocalDateTime.of(hoy.minusDays(5), LocalTime.of(10, 0));
        LocalDateTime t2 = LocalDateTime.of(hoy.minusDays(2), LocalTime.of(14, 0));
        LocalDateTime t3 = LocalDateTime.of(hoy, LocalTime.of(16, 0));

        insertarVenta(idVendedor1, idCliente1, new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"),
                "B001-00000001", t1);
        insertarVenta(idVendedor1, null, new BigDecimal("20.00"), BigDecimal.ZERO, new BigDecimal("20.00"),
                "B001-00000002", t2);
        insertarVenta(idVendedor1, idCliente1, new BigDecimal("30.00"), BigDecimal.ZERO, new BigDecimal("30.00"),
                "B001-00000003", t3);

        FiltroVentasFormulario filtro = new FiltroVentasFormulario(hoy.minusDays(3), hoy, null, null, 1);
        List<VentaResumenVista> resultado = repositorio.listar(filtro, 20, 0);

        assertEquals(2, resultado.size());
        assertEquals("B001-00000003", resultado.get(0).numeroComprobante(), "Más reciente primero");
        assertEquals("B001-00000002", resultado.get(1).numeroComprobante(), "Más antigua después");
        assertEquals("Carlos Mendoza", resultado.get(0).clienteVisible());
        assertEquals("Público general", resultado.get(1).clienteVisible());
    }

    @Test
    @DisplayName("CA-1.3: Filtrar por vendedor y por prefijo de comprobante")
    void listar_filtroVendedorYComprobante() {
        LocalDateTime ahora = LocalDateTime.now();
        insertarVenta(idVendedor1, null, new BigDecimal("15.00"), BigDecimal.ZERO, new BigDecimal("15.00"),
                "B001-00000010", ahora);
        insertarVenta(idVendedor2, null, new BigDecimal("25.00"), BigDecimal.ZERO, new BigDecimal("25.00"),
                "B001-00000020", ahora);
        insertarVenta(idVendedor1, null, new BigDecimal("35.00"), BigDecimal.ZERO, new BigDecimal("35.00"),
                "B001-00000030", ahora);

        FiltroVentasFormulario filtroVendedor = new FiltroVentasFormulario(null, null, idVendedor1, null, 1);
        List<VentaResumenVista> ventasV1 = repositorio.listar(filtroVendedor, 20, 0);
        assertEquals(2, ventasV1.size());
        assertTrue(ventasV1.stream().allMatch(v -> v.idVendedor().equals(idVendedor1)));

        FiltroVentasFormulario filtroComprobante = new FiltroVentasFormulario(null, null, null, "B001-0000002", 1);
        List<VentaResumenVista> ventasComp = repositorio.listar(filtroComprobante, 20, 0);
        assertEquals(1, ventasComp.size());
        assertEquals("B001-00000020", ventasComp.get(0).numeroComprobante());
    }

    @Test
    @DisplayName("CA-1.5: sumarTotal acumula el total de todas las ventas coincidentes")
    void sumarTotal_filtro() {
        LocalDateTime ahora = LocalDateTime.now();
        insertarVenta(idVendedor1, null, new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"),
                "B001-00000101", ahora);
        insertarVenta(idVendedor1, null, new BigDecimal("25.50"), new BigDecimal("5.50"), new BigDecimal("20.00"),
                "B001-00000102", ahora);
        insertarVenta(idVendedor2, null, new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"),
                "B001-00000103", ahora);

        FiltroVentasFormulario filtroV1 = new FiltroVentasFormulario(null, null, idVendedor1, null, 1);
        BigDecimal totalV1 = repositorio.sumarTotal(filtroV1);
        assertEquals(0, new BigDecimal("30.00").compareTo(totalV1));

        FiltroVentasFormulario filtroInexistente = new FiltroVentasFormulario(null, null, 999999, null, 1);
        BigDecimal totalVacio = repositorio.sumarTotal(filtroInexistente);
        assertEquals(0, BigDecimal.ZERO.compareTo(totalVacio));
    }

    @Test
    @DisplayName("CA-1.6: contar y paginación con límite y offset")
    void contarYPaginacion() {
        LocalDateTime base = LocalDateTime.now().minusHours(10);
        for (int i = 1; i <= 5; i++) {
            insertarVenta(idVendedor1, null, new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"),
                    String.format("B001-%08d", i), base.plusMinutes(i));
        }

        FiltroVentasFormulario filtro = FiltroVentasFormulario.vacio();
        assertEquals(5, repositorio.contar(filtro));

        List<VentaResumenVista> pag1 = repositorio.listar(filtro, 2, 0);
        assertEquals(2, pag1.size());
        assertEquals("B001-00000005", pag1.get(0).numeroComprobante());
        assertEquals("B001-00000004", pag1.get(1).numeroComprobante());

        List<VentaResumenVista> pag2 = repositorio.listar(filtro, 2, 2);
        assertEquals(2, pag2.size());
        assertEquals("B001-00000003", pag2.get(0).numeroComprobante());
        assertEquals("B001-00000002", pag2.get(1).numeroComprobante());

        List<VentaResumenVista> pag3 = repositorio.listar(filtro, 2, 4);
        assertEquals(1, pag3.size());
        assertEquals("B001-00000001", pag3.get(0).numeroComprobante());
    }

    @Test
    @DisplayName("Inyección SQL: Búsqueda con caracteres maliciosos no inyecta SQL y es segura")
    void proteccionInyeccionSql() {
        insertarVenta(idVendedor1, null, new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"),
                "B001-00000555", LocalDateTime.now());

        FiltroVentasFormulario filtroInyeccion = new FiltroVentasFormulario(null, null, null, "' OR '1'='1", 1);
        List<VentaResumenVista> resultado = repositorio.listar(filtroInyeccion, 20, 0);
        assertEquals(0, resultado.size());
        assertEquals(0, repositorio.contar(filtroInyeccion));
        assertEquals(0, BigDecimal.ZERO.compareTo(repositorio.sumarTotal(filtroInyeccion)));
    }
}
