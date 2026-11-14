package com.farmacia.calidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.farmacia.dto.FiltroMovimientosFormulario;
import com.farmacia.dto.FiltroVentasFormulario;
import com.farmacia.dto.MedicamentoFormulario;
import com.farmacia.model.Medicamento;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import com.farmacia.repository.AuditoriaRepositorio;
import com.farmacia.repository.ClienteRepositorio;
import com.farmacia.repository.MedicamentoRepositorio;
import com.farmacia.repository.MovimientoInventarioRepositorio;
import com.farmacia.repository.ProveedorRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import com.farmacia.repository.VentaRepositorio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integración contra inyección SQL en repositorios relacionales con filtros (RNF-03, CA-1.3).
 * Verifica que payloads maliciosos tipo `' OR '1'='1` sean tratados como cadenas literales sin alterar
 * la sintaxis ni extraer registros no autorizados.
 */
@DisplayName("Pruebas de Inyección SQL en Repositorios (Testcontainers + PostgreSQL)")
class InyeccionSqlRepositoriosIntegrationTest extends AbstractRepositorioIntegrationTest {

    private static final String PAYLOAD_SQL_INJECTION = "' OR '1'='1";
    private static final String PAYLOAD_COMENTARIO = "admin'--";

    @Autowired private MedicamentoRepositorio medicamentoRepositorio;
    @Autowired private ClienteRepositorio clienteRepositorio;
    @Autowired private ProveedorRepositorio proveedorRepositorio;
    @Autowired private UsuarioRepositorio usuarioRepositorio;
    @Autowired private VentaRepositorio ventaRepositorio;
    @Autowired private MovimientoInventarioRepositorio movimientoRepositorio;
    @Autowired private AuditoriaRepositorio auditoriaRepositorio;

    @BeforeEach
    void setUp() {
        limpiarBaseDeDatos();
    }

    @Test
    @DisplayName("CA-1.3: MedicamentoRepositorio neutraliza payloads en búsqueda por texto y estado")
    void medicamento_noPermiteInyeccionSql() {
        medicamentoRepositorio.guardar(new MedicamentoFormulario(
                "MED-SEC-01", "Paracetamol 500", "500 mg", "TABLETA", "CAJA", new BigDecimal("5.00"), 10
        ));

        // Intento de extraer todos los registros con OR '1'='1
        List<Medicamento> res1 = medicamentoRepositorio.buscarPorEstado(PAYLOAD_SQL_INJECTION, true);
        assertTrue(res1.isEmpty(), "No debe retornar filas extra ni romper la sintaxis SQL");

        List<Medicamento> res2 = medicamentoRepositorio.buscarActivos(PAYLOAD_COMENTARIO);
        assertTrue(res2.isEmpty(), "No debe retornar filas al inyectar comentario SQL");
    }

    @Test
    @DisplayName("CA-1.3: ClienteRepositorio neutraliza payload en búsqueda por documento o nombre")
    void cliente_noPermiteInyeccionSql() {
        jdbcTemplate.update("""
                INSERT INTO cliente (tipo_documento, numero_documento, nombre_completo)
                VALUES ('DNI', '12345678', 'Juan Perez')
                """);

        var resultados = clienteRepositorio.listar(PAYLOAD_SQL_INJECTION);
        assertTrue(resultados.isEmpty(), "Búsqueda con payload no debe extraer clientes existentes");
    }

    @Test
    @DisplayName("CA-1.3: ProveedorRepositorio neutraliza payload en búsqueda por RUC o razón social")
    void proveedor_noPermiteInyeccionSql() {
        jdbcTemplate.update("""
                INSERT INTO proveedor (ruc, razon_social, telefono, correo, direccion, activo)
                VALUES ('20123456789', 'Distribuidora Medica SAC', '999888777', 'ventas@dist.pe', 'Av. Central 123', TRUE)
                """);

        var resultados = proveedorRepositorio.buscarPorEstado(PAYLOAD_SQL_INJECTION, true);
        assertTrue(resultados.isEmpty(), "Búsqueda con payload no debe extraer proveedores");
    }

    @Test
    @DisplayName("CA-1.3: UsuarioRepositorio neutraliza payload en búsqueda por usuario")
    void usuario_noPermiteInyeccionSql() {
        jdbcTemplate.update("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES ('Usuario Auditoria', 'audit_user', 'hash_secreto', 'ADMINISTRADOR', TRUE)
                """);

        var resultados = usuarioRepositorio.buscarPorNombreUsuario(PAYLOAD_SQL_INJECTION);
        assertTrue(resultados.isEmpty(), "Búsqueda con payload no debe extraer usuarios");
    }

    @Test
    @DisplayName("CA-1.3: VentaRepositorio neutraliza payload en filtro de número de comprobante")
    void venta_noPermiteInyeccionSql() {
        FiltroVentasFormulario filtro = new FiltroVentasFormulario(null, null, null, PAYLOAD_SQL_INJECTION, 1);
        var ventas = ventaRepositorio.listar(filtro, 10, 0);
        assertNotNull(ventas);
        assertTrue(ventas.isEmpty(), "Filtro de comprobante con payload debe retornar lista vacía");

        int conteo = ventaRepositorio.contar(filtro);
        assertEquals(0, conteo);
    }

    @Test
    @DisplayName("CA-1.3: MovimientoInventarioRepositorio neutraliza payload en motivo y lote")
    void movimiento_noPermiteInyeccionSql() {
        FiltroMovimientosFormulario filtro = new FiltroMovimientosFormulario(
                null, null, null, PAYLOAD_SQL_INJECTION, null, PAYLOAD_COMENTARIO, null, 1
        );
        var movimientos = movimientoRepositorio.listar(filtro, 10, 0);
        assertNotNull(movimientos);
        assertTrue(movimientos.isEmpty());

        int total = movimientoRepositorio.contar(filtro);
        assertEquals(0, total);
    }

    @Test
    @DisplayName("CA-1.3: AuditoriaRepositorio neutraliza payload en filtro de usuario")
    void auditoria_noPermiteInyeccionSql() {
        var filtro = new AuditoriaRepositorio.Filtro(null, null, PAYLOAD_SQL_INJECTION, null, null, null);
        var eventos = auditoriaRepositorio.buscar(filtro, 10, 0);
        assertNotNull(eventos);
        assertTrue(eventos.isEmpty());

        int total = auditoriaRepositorio.contar(filtro);
        assertEquals(0, total);
    }
}
