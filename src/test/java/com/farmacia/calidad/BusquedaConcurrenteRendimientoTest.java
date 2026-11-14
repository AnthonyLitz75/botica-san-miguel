package com.farmacia.calidad;

import com.farmacia.dto.FiltroVentasFormulario;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import com.farmacia.repository.MedicamentoRepositorio;
import com.farmacia.repository.ReporteRepositorio;
import com.farmacia.repository.VentaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec 010 (US-4 / CA-4.1, CA-4.2, CA-4.4): Pruebas de rendimiento con datos de volumen (RNF-06).
 * Verifica:
 * - CA-4.1: Carga de 5 000 medicamentos, 20 000 lotes y 50 000 ventas.
 * - CA-4.2: EXPLAIN (ANALYZE, BUFFERS) de consultas clave < 200 ms orientativo.
 * - CA-4.4: Búsqueda concurrente con 5 usuarios simultáneos (hilos) con P95 <= 3 s.
 * Excluida de la suite normal mediante @Tag("rendimiento").
 */
@Tag("rendimiento")
class BusquedaConcurrenteRendimientoTest extends AbstractRepositorioIntegrationTest {

    @Autowired private MedicamentoRepositorio medicamentoRepositorio;
    @Autowired private VentaRepositorio ventaRepositorio;
    @Autowired private ReporteRepositorio reporteRepositorio;

    private static boolean datosCargados = false;

    @BeforeEach
    void asegurarDatosDeVolumen() throws Exception {
        if (!datosCargados) {
            Integer cantidadMedicamentos = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM medicamento WHERE codigo LIKE 'VOL-MED-%'", Integer.class);
            if (cantidadMedicamentos == null || cantidadMedicamentos < 5000) {
                try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
                    ScriptUtils.executeSqlScript(conn, new ClassPathResource("sql/99_datos_volumen.sql"));
                }
            }
            datosCargados = true;
        }
    }

    @Test
    @DisplayName("CA-4.1: Comprueba que 99_datos_volumen.sql generó 5 000 medicamentos, 20 000 lotes y 50 000 ventas")
    void volumenDatosCargadosCorrectamente() {
        int medicamentos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medicamento WHERE codigo LIKE 'VOL-MED-%'", Integer.class);
        int lotes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lote WHERE numero_lote LIKE 'LT-VOL-%'", Integer.class);
        int ventas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM venta WHERE numero_comprobante LIKE 'B001-9%'", Integer.class);
        int detallesVenta = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalle_venta dv JOIN venta v ON v.id_venta = dv.id_venta WHERE v.numero_comprobante LIKE 'B001-9%'", Integer.class);
        int movimientos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario mi JOIN venta v ON v.id_venta = mi.id_venta WHERE v.numero_comprobante LIKE 'B001-9%'", Integer.class);

        assertEquals(5000, medicamentos, "Deben existir exactamente 5 000 medicamentos de volumen");
        assertEquals(20000, lotes, "Deben existir exactamente 20 000 lotes de volumen");
        assertEquals(50000, ventas, "Deben existir exactamente 50 000 ventas de volumen");
        assertEquals(50000, detallesVenta, "Deben existir 50 000 detalles de venta de volumen");
        assertEquals(50000, movimientos, "Deben existir 50 000 movimientos de inventario de volumen");
    }

    @Test
    @DisplayName("CA-4.4: 5 usuarios simultáneos (hilos) realizando búsquedas de medicamentos y stock responden con P95 <= 3 s")
    void busquedaConcurrenteCincoHilosPercentil95MenorA3Segundos() throws Exception {
        int hilos = 5;
        int solicitudesPorHilo = 20; // 100 solicitudes concurrentes en total
        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(hilos);
        List<Long> latenciasMs = new CopyOnWriteArrayList<>();

        String[] terminosBusqueda = {"Medicamento", "Volumen", "500", "Caja", "10", "VOL-MED", "99", "123"};

        for (int h = 0; h < hilos; h++) {
            final int hiloId = h;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Sincronizar arranque de los 5 hilos
                    for (int s = 0; s < solicitudesPorHilo; s++) {
                        String termino = terminosBusqueda[(hiloId + s) % terminosBusqueda.length];
                        long t0 = System.nanoTime();
                        var resultados = medicamentoRepositorio.buscarActivos(termino);
                        assertNotNull(resultados);
                        long duracion = (System.nanoTime() - t0) / 1_000_000;
                        latenciasMs.add(duracion);
                    }
                } catch (Exception e) {
                    fail("Error en hilo concurrente: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Disparar todos los hilos simultáneamente
        boolean terminado = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(terminado, "Todas las solicitudes concurrentes deben completarse antes del timeout");

        assertEquals(hilos * solicitudesPorHilo, latenciasMs.size());

        List<Long> ordenadas = new ArrayList<>(latenciasMs);
        Collections.sort(ordenadas);
        int indiceP95 = (int) Math.ceil(0.95 * ordenadas.size()) - 1;
        long p95Ms = ordenadas.get(indiceP95);
        long maxMs = ordenadas.getLast();
        double promedioMs = ordenadas.stream().mapToLong(Long::longValue).average().orElse(0.0);

        System.out.printf("Rendimiento 5 hilos concurrentes (%d solicitudes): Promedio=%.2f ms, P95=%d ms, Max=%d ms%n",
                ordenadas.size(), promedioMs, p95Ms, maxMs);

        // Meta RNF-06 / CA-4.4: P95 <= 3000 ms (3 segundos)
        assertTrue(p95Ms <= 3000, "El percentil 95 fue " + p95Ms + " ms, debe ser <= 3000 ms");
    }

    @Test
    @DisplayName("CA-4.2: EXPLAIN (ANALYZE, BUFFERS) de las 5 consultas críticas < 200 ms orientativo")
    void explainAnalyzeConsultasCriticas() {
        LocalDate hoy = LocalDate.now();

        // 1. Planificación FEFO: usa idx_lote_fefo
        Integer idMed = jdbcTemplate.queryForObject(
                "SELECT id_medicamento FROM medicamento WHERE codigo = 'VOL-MED-000001'", Integer.class);
        String sqlFefo = """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT l.id_lote, l.id_medicamento, l.numero_lote, l.fecha_vencimiento, l.stock_actual,
                       m.codigo AS codigo_medicamento, m.nombre AS nombre_medicamento, m.unidad_control
                FROM lote l
                JOIN medicamento m ON m.id_medicamento = l.id_medicamento
                WHERE l.id_medicamento = ? AND l.stock_actual > 0
                ORDER BY l.fecha_vencimiento ASC, l.id_lote ASC
                """;
        List<String> planFefo = jdbcTemplate.queryForList(sqlFefo, String.class, idMed);
        String planFefoTexto = String.join("\n", planFefo);
        System.out.println("=== EXPLAIN PLANIFICACION FEFO ===\n" + planFefoTexto);
        assertTrue(planFefoTexto.contains("idx_lote_fefo") || planFefoTexto.contains("Index Scan"),
                "La planificación FEFO debe utilizar índice");

        // 2. Alertas: resumen de stock sobre 5 000 medicamentos y 20 000 lotes
        String sqlAlertas = """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT l.id_lote, l.id_medicamento, m.codigo, m.nombre,
                       l.numero_lote, l.fecha_vencimiento, l.stock_actual, m.unidad_control
                FROM lote l
                JOIN medicamento m ON m.id_medicamento = l.id_medicamento
                WHERE m.activo = TRUE AND l.stock_actual > 0
                  AND l.fecha_vencimiento >= ? AND l.fecha_vencimiento <= ?
                ORDER BY l.fecha_vencimiento, m.nombre, l.id_lote
                """;
        List<String> planAlertas = jdbcTemplate.queryForList(sqlAlertas, String.class, hoy, hoy.plusDays(90));
        String planAlertasTexto = String.join("\n", planAlertas);
        System.out.println("=== EXPLAIN ALERTAS POR VENCER ===\n" + planAlertasTexto);

        // 3. Historial con filtro sobre 50 000 ventas
        String sqlHistorial = """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT v.id_venta, v.numero_comprobante, v.fecha_hora,
                       u.nombre AS vendedor, c.nombre_completo AS cliente,
                       v.subtotal, v.descuento_total, v.total
                FROM venta v
                JOIN usuario u ON u.id_usuario = v.id_usuario
                LEFT JOIN cliente c ON c.id_cliente = v.id_cliente
                WHERE v.fecha_hora >= ? AND v.fecha_hora < ?
                ORDER BY v.fecha_hora DESC, v.id_venta DESC
                LIMIT 20 OFFSET 0
                """;
        List<String> planHistorial = jdbcTemplate.queryForList(sqlHistorial, String.class,
                hoy.minusDays(30).atStartOfDay(), hoy.plusDays(1).atStartOfDay());
        String planHistorialTexto = String.join("\n", planHistorial);
        System.out.println("=== EXPLAIN HISTORIAL VENTAS ===\n" + planHistorialTexto);
        assertTrue(planHistorialTexto.contains("idx_venta_fecha") || planHistorialTexto.contains("Index Scan"),
                "El historial debe aprovechar índice temporal");

        // 4. Ventas por período sobre 50 000 ventas
        String sqlVentasPeriodo = """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT CAST(fecha_hora AS DATE) AS fecha,
                       COUNT(id_venta) AS cantidad_ventas,
                       COALESCE(SUM(subtotal), 0) AS subtotal,
                       COALESCE(SUM(descuento_total), 0) AS descuento,
                       COALESCE(SUM(total), 0) AS total
                FROM venta
                WHERE fecha_hora >= ? AND fecha_hora < ?
                GROUP BY CAST(fecha_hora AS DATE)
                ORDER BY fecha ASC
                """;
        List<String> planVentas = jdbcTemplate.queryForList(sqlVentasPeriodo, String.class,
                hoy.minusDays(30).atStartOfDay(), hoy.plusDays(1).atStartOfDay());
        String planVentasTexto = String.join("\n", planVentas);
        System.out.println("=== EXPLAIN VENTAS POR PERIODO ===\n" + planVentasTexto);

        // 5. Más vendidos sobre 50 000 ventas y detalles
        String sqlMasVendidos = """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT m.id_medicamento, m.codigo, m.nombre,
                       COALESCE(SUM(dv.cantidad), 0) AS unidades_vendidas,
                       COALESCE(SUM(dv.importe), 0) AS total_vendido
                FROM detalle_venta dv
                JOIN venta v ON dv.id_venta = v.id_venta
                JOIN lote l ON dv.id_lote = l.id_lote
                JOIN medicamento m ON l.id_medicamento = m.id_medicamento
                WHERE v.fecha_hora >= ? AND v.fecha_hora < ?
                GROUP BY m.id_medicamento, m.codigo, m.nombre
                ORDER BY unidades_vendidas DESC, m.nombre ASC
                LIMIT 10
                """;
        List<String> planMasVendidos = jdbcTemplate.queryForList(sqlMasVendidos, String.class,
                hoy.minusDays(30).atStartOfDay(), hoy.plusDays(1).atStartOfDay());
        String planMasVendidosTexto = String.join("\n", planMasVendidos);
        System.out.println("=== EXPLAIN MAS VENDIDOS ===\n" + planMasVendidosTexto);
    }
}
