package com.farmacia.repository;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@SpringBootTest
public abstract class AbstractRepositorioIntegrationTest {

    private enum EstadoEsquema {
        NO_INICIALIZADO,
        INICIALIZADO,
        IRRECUPERABLE
    }

    // Contenedor Singleton de JVM: se inicia manualmente una sola vez y permanece activo
    // durante toda la suite de pruebas sin que la extensión de JUnit lo destruya entre clases.
    protected static final PostgreSQLContainer<?> postgres;
    private static volatile EstadoEsquema estadoEsquema = EstadoEsquema.NO_INICIALIZADO;
    private static volatile Throwable causaFalloFatal = null;
    private static final Object lockInicializacion = new Object();

    static {
        postgres = new PostgreSQLContainer<>("postgres:18-alpine");
        postgres.start();
    }

    @ServiceConnection
    static PostgreSQLContainer<?> postgresConnection = postgres;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void inicializarEsquemaPostgresql(@Autowired DataSource dataSource) throws Exception {
        // Barrera de circuito abierto: si una suite previa dejó el contenedor en estado corrupto, abortar de inmediato
        if (estadoEsquema == EstadoEsquema.IRRECUPERABLE) {
            throw new IllegalStateException(
                    "El contenedor PostgreSQL se encuentra en estado IRRECUPERABLE debido a un fallo previo en la inicialización del DDL. " +
                    "Se bloquea preventivamente la ejecución de esta suite.",
                    causaFalloFatal);
        }

        if (estadoEsquema == EstadoEsquema.NO_INICIALIZADO) {
            synchronized (lockInicializacion) {
                if (estadoEsquema == EstadoEsquema.IRRECUPERABLE) {
                    throw new IllegalStateException(
                            "El contenedor PostgreSQL se encuentra en estado IRRECUPERABLE debido a un fallo previo en la inicialización del DDL.",
                            causaFalloFatal);
                }

                if (estadoEsquema == EstadoEsquema.NO_INICIALIZADO) {
                    try (Connection conexion = dataSource.getConnection()) {
                        try {
                            Resource ddl = new ClassPathResource("scripts/01_creacion_tablas.sql");
                            if (!ddl.exists()) {
                                ddl = new FileSystemResource("scripts/01_creacion_tablas.sql");
                            }
                            ScriptUtils.executeSqlScript(conexion, ddl);
                            estadoEsquema = EstadoEsquema.INICIALIZADO;
                        } catch (Exception ex) {
                            estadoEsquema = EstadoEsquema.IRRECUPERABLE;
                            causaFalloFatal = ex;

                            // 1. Rollback explícito de cualquier transacción abierta en PostgreSQL
                            try (Statement stmt = conexion.createStatement()) {
                                stmt.execute("ROLLBACK;");
                            } catch (SQLException rollbackEx) {
                                ex.addSuppressed(rollbackEx);
                            }

                            // 2. Limpieza atómica sentencia por sentencia del esquema public
                            boolean limpiezaExitosa = false;
                            try (Statement stmt = conexion.createStatement()) {
                                stmt.execute("DROP SCHEMA IF EXISTS public CASCADE");
                                stmt.execute("CREATE SCHEMA public");
                                limpiezaExitosa = true;
                            } catch (SQLException cleanupEx) {
                                ex.addSuppressed(cleanupEx);
                            }

                            // 3. Si la limpieza falla, detención forzada del contenedor para evitar reuso
                            if (!limpiezaExitosa) {
                                try {
                                    postgres.stop();
                                } catch (Exception stopEx) {
                                    ex.addSuppressed(stopEx);
                                }
                            }

                            throw new IllegalStateException(
                                    "Fallo crítico en la inicialización del esquema DDL en PostgreSQL.",
                                    ex);
                        }
                    }
                }
            }
        }
    }

    /**
     * Limpia todas las tablas del esquema respetando la jerarquía de dependencias relacionales
     * de claves foráneas (de hojas a raíces), garantizando que cada prueba inicie en un estado limpio.
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
            DELETE FROM usuario;
        """);
    }
}
