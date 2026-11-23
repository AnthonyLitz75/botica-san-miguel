package com.farmacia.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.farmacia.model.PanelAlertasVista;
import com.farmacia.service.AlertaInventarioServicio;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Valida el seed de demostración (db/demo/R__datos_demo.sql) cargado igual que con el perfil demo
 * (migraciones versionadas + db/demo) en un esquema propio del contenedor PostgreSQL de las pruebas.
 */
class DatosDemostracionSeedTest extends AbstractRepositorioIntegrationTest {

    private static final String ESQUEMA = "demo_seed";
    private static final Path SEED = Path.of("src", "main", "resources", "db", "demo", "R__datos_demo.sql");

    private static Flyway flyway;
    private static DriverManagerDataSource fuenteEsquema;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void cargarSeedEnEsquemaPropio() {
        flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(ESQUEMA)
                .locations("classpath:db/migration", "classpath:db/demo")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        String url = postgres.getJdbcUrl();
        fuenteEsquema = new DriverManagerDataSource(
                url + (url.contains("?") ? "&" : "?") + "currentSchema=" + ESQUEMA,
                postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(fuenteEsquema);
    }

    @AfterAll
    static void eliminarEsquema() {
        flyway.clean();
        new JdbcTemplate(fuenteEsquema).execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
    }

    @Test
    @DisplayName("(a) El seed carga un volumen mínimo realista y órdenes en los 4 estados")
    void volumenMinimo() {
        assertThat(contar("medicamento")).isGreaterThanOrEqualTo(25);
        assertThat(contar("venta")).isGreaterThanOrEqualTo(50);
        assertThat(contar("cliente")).isGreaterThanOrEqualTo(10);
        assertThat(contar("proveedor")).isGreaterThanOrEqualTo(5);
        assertThat(contar("orden_compra")).isGreaterThanOrEqualTo(5);
        assertThat(jdbc.queryForList("SELECT DISTINCT estado FROM orden_compra", String.class))
                .containsExactlyInAnyOrder("PENDIENTE", "PARCIALMENTE_RECIBIDA", "RECIBIDA", "CANCELADA");
        assertThat(jdbc.queryForList("SELECT nombre_usuario FROM usuario", String.class))
                .containsExactlyInAnyOrder("admin_demo", "almacen_demo", "compras_demo", "vendedor_demo");
    }

    @Test
    @DisplayName("(a) El estado de cada orden coincide con lo recibido según detalle_movimiento")
    void estadoDeOrdenesCoherenteConRecepciones() {
        List<Map<String, Object>> incoherentes = jdbc.queryForList("""
                WITH recibido AS (
                    SELECT d.id_orden, SUM(d.cantidad_solicitada) AS solicitado,
                           COALESCE(SUM(r.cantidad), 0) AS recibido
                    FROM detalle_orden_compra d
                    LEFT JOIN (SELECT dm.id_detalle_orden, SUM(dm.cantidad) AS cantidad
                               FROM detalle_movimiento dm GROUP BY dm.id_detalle_orden) r
                           ON r.id_detalle_orden = d.id_detalle_orden
                    GROUP BY d.id_orden
                )
                SELECT o.numero_orden, o.estado, r.solicitado, r.recibido
                FROM orden_compra o JOIN recibido r ON r.id_orden = o.id_orden
                WHERE NOT (
                    (o.estado = 'RECIBIDA' AND r.recibido = r.solicitado)
                    OR (o.estado = 'PARCIALMENTE_RECIBIDA' AND r.recibido > 0 AND r.recibido < r.solicitado)
                    OR (o.estado IN ('PENDIENTE', 'CANCELADA') AND r.recibido = 0))
                """);
        assertThat(incoherentes).as("órdenes con estado incoherente").isEmpty();
    }

    @Test
    @DisplayName("(b) Para cada lote, stock_actual = entradas - salidas según detalle_movimiento")
    void stockCoherenteConMovimientos() {
        List<Map<String, Object>> incoherentes = jdbc.queryForList("""
                SELECT l.numero_lote, l.stock_actual,
                       COALESCE(SUM(CASE WHEN mi.tipo = 'ENTRADA' THEN dm.cantidad
                                         WHEN mi.tipo = 'SALIDA' THEN -dm.cantidad END), 0) AS calculado
                FROM lote l
                LEFT JOIN detalle_movimiento dm ON dm.id_lote = l.id_lote
                LEFT JOIN movimiento_inventario mi ON mi.id_movimiento = dm.id_movimiento
                GROUP BY l.id_lote, l.numero_lote, l.stock_actual
                HAVING l.stock_actual <> COALESCE(SUM(CASE WHEN mi.tipo = 'ENTRADA' THEN dm.cantidad
                                                           WHEN mi.tipo = 'SALIDA' THEN -dm.cantidad END), 0)
                """);
        assertThat(incoherentes).as("lotes con stock incoherente").isEmpty();
        assertThat(contar("lote")).isGreaterThanOrEqualTo(contar("medicamento"));
    }

    @Test
    @DisplayName("(c) Cada venta tiene un único movimiento de salida que refleja sus líneas y su total cuadra")
    void ventasCoherentes() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM venta v
                WHERE (SELECT COUNT(*) FROM movimiento_inventario mi
                       WHERE mi.id_venta = v.id_venta AND mi.tipo = 'SALIDA') <> 1
                """, Integer.class)).as("ventas sin exactamente un movimiento de salida").isZero();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM venta v
                WHERE v.total <> (SELECT COALESCE(SUM(d.importe), 0) FROM detalle_venta d WHERE d.id_venta = v.id_venta)
                   OR NOT EXISTS (SELECT 1 FROM detalle_venta d WHERE d.id_venta = v.id_venta)
                """, Integer.class)).as("ventas cuyo total no cuadra con sus líneas").isZero();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT d.id_venta, d.id_lote, SUM(d.cantidad) AS cantidad FROM detalle_venta d
                    GROUP BY d.id_venta, d.id_lote
                    EXCEPT
                    SELECT mi.id_venta, dm.id_lote, SUM(dm.cantidad) FROM detalle_movimiento dm
                    JOIN movimiento_inventario mi ON mi.id_movimiento = dm.id_movimiento
                    WHERE mi.id_venta IS NOT NULL
                    GROUP BY mi.id_venta, dm.id_lote
                ) diferencias
                """, Integer.class)).as("líneas de venta sin su detalle de movimiento").isZero();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM venta
                WHERE fecha_hora < (NOW() AT TIME ZONE 'America/Lima') - INTERVAL '46 days'
                   OR fecha_hora > (NOW() AT TIME ZONE 'America/Lima')
                """, Integer.class)).as("ventas fuera de los últimos 45 días").isZero();
    }

    @Test
    @DisplayName("(d) Los comprobantes son únicos y la secuencia no está por detrás del mayor número usado")
    void comprobantesYSecuencia() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) - COUNT(DISTINCT numero_comprobante) FROM venta", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM venta WHERE numero_comprobante !~ '^B001-[0-9]{8}$'", Integer.class)).isZero();
        Long mayorUsado = jdbc.queryForObject(
                "SELECT COALESCE(MAX(substring(numero_comprobante FROM 6)::BIGINT), 0) FROM venta", Long.class);
        Long ultimoDeSecuencia = jdbc.queryForObject(
                "SELECT CASE WHEN is_called THEN last_value ELSE last_value - 1 END FROM seq_numero_comprobante",
                Long.class);
        assertThat(mayorUsado).isPositive();
        assertThat(ultimoDeSecuencia).isGreaterThanOrEqualTo(mayorUsado);
    }

    @Test
    @DisplayName("(e) El panel de alertas de la aplicación muestra los 4 tipos con el seed")
    void alertasDeLosCuatroTipos() {
        Clock reloj = Clock.systemDefaultZone();
        AlertaInventarioServicio servicio = new AlertaInventarioServicio(
                new MedicamentoRepositorio(jdbc, reloj), new LoteRepositorio(jdbc), reloj, 90);
        PanelAlertasVista panel = servicio.consultarAlertas();

        assertThat(panel.vencidos()).as("lotes vencidos").hasSizeGreaterThanOrEqualTo(2);
        assertThat(panel.porVencer()).as("lotes por vencer").hasSizeGreaterThanOrEqualTo(4);
        assertThat(panel.agotados()).as("medicamentos agotados").hasSizeGreaterThanOrEqualTo(2);
        assertThat(panel.stockBajo()).as("medicamentos con stock bajo").hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("(e) Varios medicamentos tienen 2 lotes vigentes con stock para repartir FEFO por tramos")
    void medicamentosConDosLotesVigentes() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT id_medicamento FROM lote
                    WHERE stock_actual > 0 AND fecha_vencimiento > CURRENT_DATE
                    GROUP BY id_medicamento HAVING COUNT(*) >= 2) m
                """, Integer.class)).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("(f) Hay al menos un cliente frecuente (10 o más compras)")
    void clienteFrecuente() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT id_cliente FROM venta WHERE id_cliente IS NOT NULL
                    GROUP BY id_cliente HAVING COUNT(*) >= 10) frecuentes
                """, Integer.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("(g) Volver a ejecutar el seed no duplica datos")
    void seedIdempotente() throws IOException, SQLException {
        Map<String, Integer> antes = conteos();
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        String sql = Files.readString(SEED, StandardCharsets.UTF_8);
        try (Connection conexion = fuenteEsquema.getConnection(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute("SET search_path TO " + ESQUEMA);
            sentencia.execute(sql);
        }
        assertThat(conteos()).isEqualTo(antes);
    }

    @Test
    @DisplayName("(h) El seed no inventa filas en la bitácora de auditoría")
    void sinFilasDeAuditoria() {
        assertThat(contar("auditoria")).isZero();
    }

    private Map<String, Integer> conteos() {
        return Map.ofEntries(
                Map.entry("usuario", contar("usuario")), Map.entry("categoria", contar("categoria")),
                Map.entry("medicamento", contar("medicamento")), Map.entry("lote", contar("lote")),
                Map.entry("proveedor", contar("proveedor")), Map.entry("cliente", contar("cliente")),
                Map.entry("orden_compra", contar("orden_compra")),
                Map.entry("detalle_orden_compra", contar("detalle_orden_compra")),
                Map.entry("venta", contar("venta")), Map.entry("detalle_venta", contar("detalle_venta")),
                Map.entry("movimiento_inventario", contar("movimiento_inventario")),
                Map.entry("detalle_movimiento", contar("detalle_movimiento")));
    }

    private int contar(String tabla) {
        // Identificadores constantes de esta prueba (no provienen del usuario).
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }
}
