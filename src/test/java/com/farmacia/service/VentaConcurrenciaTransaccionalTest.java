package com.farmacia.service;

import com.farmacia.model.LineaPreparacionVentaVista;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 003, CA-2.4: dos confirmaciones simultáneas de la última unidad, con dos hilos reales. */
class VentaConcurrenciaTransaccionalTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private VentaServicio servicio;

    private int idMedicamento;
    private int idLote;

    @BeforeEach
    void preparar() {
        limpiarBaseDeDatos();
        for (String usuario : List.of("vendedor_a", "vendedor_b")) {
            jdbcTemplate.update("""
                    INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol)
                    VALUES ('Vendedor concurrente', ?, 'x', 'VENDEDOR')
                    """, usuario);
        }
        idMedicamento = jdbcTemplate.queryForObject("""
                INSERT INTO medicamento(codigo, nombre, presentacion, unidad_control, precio_venta)
                VALUES ('CONC-VENTA', 'Ibuprofeno', 'Caja', 'CAJA', 13.50) RETURNING id_medicamento
                """, Integer.class);
        idLote = jdbcTemplate.queryForObject("""
                INSERT INTO lote(id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
                VALUES (?, 'ULTIMA', ?, 1) RETURNING id_lote
                """, Integer.class, idMedicamento, LocalDate.now().plusMonths(6));
    }

    @RepeatedTest(3)
    @DisplayName("Solo una venta de la última unidad se confirma; el stock nunca queda negativo")
    void soloUnaVentaDeLaUltimaUnidad() throws Exception {
        var linea = new LineaPreparacionVentaVista(idMedicamento, "CONC-VENTA", "Ibuprofeno", 1,
                new BigDecimal("13.50"), new BigDecimal("13.50"), List.of());
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Future<?> a = hilos.submit(() -> {
                salida.await();
                return servicio.confirmar("vendedor_a", null, List.of(linea));
            });
            Future<?> b = hilos.submit(() -> {
                salida.await();
                return servicio.confirmar("vendedor_b", null, List.of(linea));
            });
            salida.countDown();

            int exitos = 0;
            int rechazos = 0;
            for (Future<?> resultado : List.of(a, b)) {
                try {
                    resultado.get(30, TimeUnit.SECONDS);
                    exitos++;
                } catch (java.util.concurrent.ExecutionException error) {
                    assertInstanceOf(StockInsuficienteException.class, error.getCause());
                    rechazos++;
                }
            }

            assertEquals(1, exitos);
            assertEquals(1, rechazos);
            int stock = jdbcTemplate.queryForObject(
                    "SELECT stock_actual FROM lote WHERE id_lote = ?", Integer.class, idLote);
            assertEquals(0, stock);
            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM venta", Integer.class));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM movimiento_inventario WHERE id_venta IS NOT NULL", Integer.class));
        } finally {
            hilos.shutdownNow();
            assertTrue(hilos.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
