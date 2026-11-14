package com.farmacia.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de las pruebas de integración con PostgreSQL real (Testcontainers).
 * El esquema lo crea Flyway al iniciar el contexto de Spring con las mismas migraciones
 * que usa la aplicación (src/main/resources/db/migration); si el contexto se reutiliza o se
 * crea otro sobre el mismo contenedor, Flyway detecta las migraciones ya aplicadas y no repite nada.
 */
@SpringBootTest
public abstract class AbstractRepositorioIntegrationTest {

    // Contenedor Singleton de JVM: se inicia manualmente una sola vez y permanece activo
    // durante toda la suite de pruebas sin que la extensión de JUnit lo destruya entre clases.
    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:18-alpine");
        postgres.start();
    }

    @ServiceConnection
    static PostgreSQLContainer<?> postgresConnection = postgres;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Limpia todas las tablas del esquema respetando la jerarquía de dependencias relacionales
     * de claves foráneas (de hojas a raíces), garantizando que cada prueba inicie en un estado limpio.
     * No toca flyway_schema_history.
     */
    protected void limpiarBaseDeDatos() {
        jdbcTemplate.execute("""
            DELETE FROM detalle_movimiento;
            DELETE FROM detalle_venta;
            DELETE FROM movimiento_inventario;
            DELETE FROM detalle_orden_compra;
            DELETE FROM orden_compra;
            DELETE FROM venta;
            DELETE FROM proveedor;
            DELETE FROM cliente;
            DELETE FROM lote;
            DELETE FROM medicamento;
            DELETE FROM categoria;
            DELETE FROM usuario;
        """);
    }
}
