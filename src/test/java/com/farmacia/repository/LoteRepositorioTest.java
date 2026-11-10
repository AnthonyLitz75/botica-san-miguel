package com.farmacia.repository;

import com.farmacia.model.AlertaVencimientoVista;
import com.farmacia.model.Lote;
import com.farmacia.model.LoteDisponibleVista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas de Integración de LoteRepositorio (Testcontainers + PostgreSQL)")
class LoteRepositorioTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private LoteRepositorio loteRepositorio;

    @BeforeEach
    void setUp() {
        limpiarBaseDeDatos();
    }

    private Integer insertarMedicamento(String codigo, String nombre, boolean activo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO medicamento (codigo, nombre, concentracion, presentacion, unidad_control, precio_venta, stock_minimo, activo)
                VALUES (?, ?, '500 mg', 'TABLETA', 'CAJA', 10.00, 20, ?)
                RETURNING id_medicamento
                """, Integer.class, codigo, nombre, activo);
    }

    @Test
    @DisplayName("LR-01: registrarOIncrementar inserta un nuevo lote y buscarPorId recupera sus datos completos")
    void registrarOIncrementar_insertaNuevoLoteYBuscarPorIdRecuperaDatos() {
        Integer idMed = insertarMedicamento("MED-L01", "Amoxicilina", true);
        LocalDate vencimiento = LocalDate.of(2027, 8, 31);

        Integer idLote = loteRepositorio.registrarOIncrementar(idMed, "LOT-2027-A", vencimiento, 100);
        assertNotNull(idLote, "El id del lote registrado no debe ser nulo");

        Optional<Lote> optLote = loteRepositorio.buscarPorId(idLote);
        assertTrue(optLote.isPresent());

        Lote lote = optLote.get();
        assertEquals(idLote, lote.idLote());
        assertEquals(idMed, lote.idMedicamento());
        assertEquals("LOT-2027-A", lote.numeroLote());
        assertEquals(vencimiento, lote.fechaVencimiento());
        assertEquals(100, lote.stockActual());
    }

    @Test
    @DisplayName("LR-02: registrarOIncrementar acumula existencias en lote preexistente con misma fecha de vencimiento")
    void registrarOIncrementar_acumulaExistenciasConMismaFecha() {
        Integer idMed = insertarMedicamento("MED-L02", "Paracetamol", true);
        LocalDate vencimiento = LocalDate.of(2027, 12, 31);

        Integer idLote1 = loteRepositorio.registrarOIncrementar(idMed, "LOT-ACUM-1", vencimiento, 50);
        Integer idLote2 = loteRepositorio.registrarOIncrementar(idMed, "LOT-ACUM-1", vencimiento, 30);

        assertEquals(idLote1, idLote2, "Debe retornar el mismo ID de lote para un lote preexistente");

        Optional<Lote> opt = loteRepositorio.buscarPorId(idLote1);
        assertTrue(opt.isPresent());
        assertEquals(80, opt.get().stockActual(), "El stock acumulado debe ser 50 + 30 = 80");
    }

    @Test
    @DisplayName("LR-03: registrarOIncrementar lanza IllegalArgumentException si el número de lote existe con diferente fecha")
    void registrarOIncrementar_lanzaExcepcionConDiferenteFechaVencimiento() {
        Integer idMed = insertarMedicamento("MED-L03", "Ibuprofeno", true);
        LocalDate fecha1 = LocalDate.of(2027, 5, 20);
        LocalDate fecha2 = LocalDate.of(2027, 11, 20);

        loteRepositorio.registrarOIncrementar(idMed, "LOT-CONF-1", fecha1, 40);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> loteRepositorio.registrarOIncrementar(idMed, "LOT-CONF-1", fecha2, 20),
                "Debe lanzar IllegalArgumentException al intentar alterar la fecha de un lote existente"
        );

        assertEquals("El número de lote ya existe con otra fecha de vencimiento", ex.getMessage());
    }

    @Test
    @DisplayName("LR-04: buscarPorId con ID inexistente retorna Optional vacío")
    void buscarPorId_conIdInexistente_retornaOptionalVacio() {
        Optional<Lote> opt = loteRepositorio.buscarPorId(999_999);
        assertTrue(opt.isEmpty());
    }

    @Test
    @DisplayName("LR-05: listarPorMedicamento retorna todos los lotes ordenados por fecha de vencimiento e id")
    void listarPorMedicamento_retornaLotesOrdenadosPorVencimiento() {
        Integer idMed1 = insertarMedicamento("MED-L05-A", "Azitromicina", true);
        Integer idMed2 = insertarMedicamento("MED-L05-B", "Ceftriaxona", true);

        // Lotes para med1 con vencimientos desordenados
        LocalDate vencLejano = LocalDate.of(2028, 1, 1);
        LocalDate vencCercano = LocalDate.of(2026, 12, 1);
        loteRepositorio.registrarOIncrementar(idMed1, "LOT-LEJ", vencLejano, 20);
        loteRepositorio.registrarOIncrementar(idMed1, "LOT-CER", vencCercano, 15);

        // Lote para med2 (no debe aparecer)
        loteRepositorio.registrarOIncrementar(idMed2, "LOT-MED2", LocalDate.of(2027, 1, 1), 50);

        List<Lote> lotes = loteRepositorio.listarPorMedicamento(idMed1);
        assertEquals(2, lotes.size());
        assertEquals("LOT-CER", lotes.get(0).numeroLote(), "El lote más próximo a vencer debe ir primero");
        assertEquals("LOT-LEJ", lotes.get(1).numeroLote());

        // Medicamento sin lotes retorna lista vacía
        assertTrue(loteRepositorio.listarPorMedicamento(999_999).isEmpty());
    }

    @Test
    @DisplayName("LR-06: descontarStock reduce el stock atómicamente cuando las existencias son suficientes")
    void descontarStock_descuentaCorrectamenteCuandoHayExistencias() {
        Integer idMed = insertarMedicamento("MED-L06", "Diclofenaco", true);
        Integer idLote = loteRepositorio.registrarOIncrementar(idMed, "LOT-DESC-1", LocalDate.of(2027, 4, 1), 60);

        int filasActualizadas = loteRepositorio.descontarStock(idLote, 25);
        assertEquals(1, filasActualizadas);

        Lote lote = loteRepositorio.buscarPorId(idLote).orElseThrow();
        assertEquals(35, lote.stockActual(), "El stock debe decrementar de 60 a 35");
    }

    @Test
    @DisplayName("LR-07: descontarStock retorna 0 y no altera existencias si el stock es insuficiente")
    void descontarStock_retornaCeroCuandoStockEsInsuficiente() {
        Integer idMed = insertarMedicamento("MED-L07", "Ketorolaco", true);
        Integer idLote = loteRepositorio.registrarOIncrementar(idMed, "LOT-INSUF", LocalDate.of(2027, 4, 1), 10);

        int filas = loteRepositorio.descontarStock(idLote, 15);
        assertEquals(0, filas, "No debe descontar si la cantidad requerida supera el stock disponible");

        Lote lote = loteRepositorio.buscarPorId(idLote).orElseThrow();
        assertEquals(10, lote.stockActual(), "El stock debe permanecer inalterado en 10");

        // Lote inexistente retorna 0
        assertEquals(0, loteRepositorio.descontarStock(999_999, 5));
    }

    @Test
    @DisplayName("LR-08: descontarStock lanza IllegalArgumentException si la cantidad es nula o menor o igual a cero")
    void descontarStock_validaCantidadMayorQueCero() {
        assertThrows(IllegalArgumentException.class, () -> loteRepositorio.descontarStock(1, null));
        assertThrows(IllegalArgumentException.class, () -> loteRepositorio.descontarStock(1, 0));
        assertThrows(IllegalArgumentException.class, () -> loteRepositorio.descontarStock(1, -10));
    }

    @Test
    @DisplayName("LR-09: listarDisponiblesConMedicamento respeta orden FEFO, excluye stock 0 y medicamentos inactivos")
    void listarDisponiblesConMedicamento_aplicaFEFOYFiltrosDeEstado() {
        // Medicamento A: Activo
        Integer medA = insertarMedicamento("MED-A", "Amoxicilina", true);
        loteRepositorio.registrarOIncrementar(medA, "LOT-A-LEJANO", LocalDate.of(2028, 6, 1), 10);
        loteRepositorio.registrarOIncrementar(medA, "LOT-A-CERCANO", LocalDate.of(2027, 1, 1), 20);

        // Medicamento B: Activo con lote de stock 0
        Integer medB = insertarMedicamento("MED-B", "Betametasona", true);
        Integer loteAgotado = loteRepositorio.registrarOIncrementar(medB, "LOT-B-AGOT", LocalDate.of(2026, 11, 1), 5);
        loteRepositorio.descontarStock(loteAgotado, 5); // queda con stock 0

        // Medicamento C: Inactivo con lote vigente
        Integer medC = insertarMedicamento("MED-C", "Clonazepam", false);
        loteRepositorio.registrarOIncrementar(medC, "LOT-C-INACT", LocalDate.of(2027, 3, 1), 15);

        List<LoteDisponibleVista> disponibles = loteRepositorio.listarDisponiblesConMedicamento();

        // Debe retornar únicamente los dos lotes disponibles de Amoxicilina
        assertEquals(2, disponibles.size());
        assertEquals("LOT-A-CERCANO", disponibles.get(0).numeroLote(), "Orden FEFO: el lote de menor fecha debe ir primero");
        assertEquals("LOT-A-LEJANO", disponibles.get(1).numeroLote());
    }

    @Test
    @DisplayName("LR-10: listarDisponiblesPorMedicamento filtra estrictamente por el medicamento indicado")
    void listarDisponiblesPorMedicamento_filtraPorIdMedicamento() {
        Integer med1 = insertarMedicamento("MED-1", "Dexametasona", true);
        Integer med2 = insertarMedicamento("MED-2", "Enalapril", true);

        loteRepositorio.registrarOIncrementar(med1, "LOT-DEX-1", LocalDate.of(2027, 1, 1), 30);
        loteRepositorio.registrarOIncrementar(med2, "LOT-ENA-1", LocalDate.of(2027, 1, 1), 40);

        List<LoteDisponibleVista> lotesMed1 = loteRepositorio.listarDisponiblesPorMedicamento(med1);
        assertEquals(1, lotesMed1.size());
        assertEquals("LOT-DEX-1", lotesMed1.get(0).numeroLote());
        assertEquals(30, lotesMed1.get(0).stockActual());

        // Para ID sin existencias retorna lista vacía
        assertTrue(loteRepositorio.listarDisponiblesPorMedicamento(999_999).isEmpty());
    }

    @Test
    @DisplayName("LR-11: listarVencidos reporta lotes con fecha anterior a la fecha de corte y stock positivo")
    void listarVencidos_reportaLotesVencidosConStockPositivo() {
        LocalDate hoy = LocalDate.now();
        Integer med = insertarMedicamento("MED-VENC", "Furosemida", true);

        // Lote vencido con stock
        loteRepositorio.registrarOIncrementar(med, "LOT-V1", hoy.minusDays(10), 15);

        // Lote vencido pero con stock 0 (no debe aparecer)
        Integer loteVencidoAgotado = loteRepositorio.registrarOIncrementar(med, "LOT-V2-AGOT", hoy.minusDays(20), 5);
        loteRepositorio.descontarStock(loteVencidoAgotado, 5);

        // Lote vigente (no debe aparecer)
        loteRepositorio.registrarOIncrementar(med, "LOT-VIG", hoy.plusDays(30), 50);

        List<AlertaVencimientoVista> vencidos = loteRepositorio.listarVencidos(hoy);
        assertEquals(1, vencidos.size());
        assertEquals("LOT-V1", vencidos.get(0).numeroLote());
        assertEquals("VENCIDO", vencidos.get(0).tipoAlerta());
        assertTrue(vencidos.get(0).diasParaVencer() < 0, "Los días para vencer de un lote vencido deben ser negativos");
    }

    @Test
    @DisplayName("LR-12: listarPorVencer reporta lotes dentro del intervalo [desde, hasta] con stock positivo")
    void listarPorVencer_reportaLotesEnHorizonteDeFechas() {
        LocalDate hoy = LocalDate.now();
        LocalDate hasta = hoy.plusDays(90);
        Integer med = insertarMedicamento("MED-PV", "Glibenclamida", true);

        // Lote por vencer dentro del rango (en 45 días)
        loteRepositorio.registrarOIncrementar(med, "LOT-PV-45", hoy.plusDays(45), 25);

        // Lote en el límite exacto (hoy + 90 días)
        loteRepositorio.registrarOIncrementar(med, "LOT-PV-90", hasta, 30);

        // Lote lejano (en 120 días, fuera de rango)
        loteRepositorio.registrarOIncrementar(med, "LOT-LEJANO", hoy.plusDays(120), 60);

        List<AlertaVencimientoVista> porVencer = loteRepositorio.listarPorVencer(hoy, hasta);
        assertEquals(2, porVencer.size());
        assertEquals("LOT-PV-45", porVencer.get(0).numeroLote());
        assertEquals("LOT-PV-90", porVencer.get(1).numeroLote());
        assertEquals("POR_VENCER", porVencer.get(0).tipoAlerta());
        assertEquals(45L, porVencer.get(0).diasParaVencer());
    }
}
