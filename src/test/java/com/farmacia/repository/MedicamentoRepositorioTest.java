package com.farmacia.repository;

import com.farmacia.dto.MedicamentoFormulario;
import com.farmacia.model.Medicamento;
import com.farmacia.model.ResumenStockInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas de Integración de MedicamentoRepositorio (Testcontainers + PostgreSQL)")
class MedicamentoRepositorioTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private MedicamentoRepositorio medicamentoRepositorio;

    private static final AtomicLong contadorCodigo = new AtomicLong(100L);

    @BeforeEach
    void setUp() {
        limpiarBaseDeDatos();
    }

    private String generarCodigoUnico() {
        return "MED-" + contadorCodigo.incrementAndGet();
    }

    private MedicamentoFormulario crearFormulario(String codigo, String nombre, String concentracion,
                                                  String presentacion, String unidadControl,
                                                  BigDecimal precioVenta, Integer stockMinimo) {
        return new MedicamentoFormulario(
                codigo,
                nombre,
                concentracion,
                presentacion,
                unidadControl,
                precioVenta,
                stockMinimo
        );
    }

    private Integer insertarLoteAuxiliar(Integer idMedicamento, String numeroLote,
                                         LocalDate fechaVencimiento, Integer stockActual) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO lote (id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
                VALUES (?, ?, ?, ?)
                RETURNING id_lote
                """, Integer.class, idMedicamento, numeroLote, fechaVencimiento, stockActual);
    }

    private Integer insertarCategoriaAuxiliar(String nombre, Integer idPadre) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO categoria (nombre, id_padre, activo)
                VALUES (?, ?, TRUE)
                RETURNING id_categoria
                """, Integer.class, nombre, idPadre);
    }

    @Test
    @DisplayName("MR-01: guardar persiste entidad completa y buscarPorId recupera todos los campos")
    void guardar_persisteEntidadCompletaYBuscarPorIdRecuperaDatos() {
        String codigo = generarCodigoUnico();
        MedicamentoFormulario form = crearFormulario(
                codigo,
                "Amoxicilina con Ácido Clavulánico",
                "500 mg / 125 mg",
                "TABLETA",
                "CAJA",
                new BigDecimal("28.50"),
                15
        );

        Integer idGenerado = medicamentoRepositorio.guardar(form);
        assertNotNull(idGenerado, "El ID generado no debe ser nulo");

        Optional<Medicamento> optMedicamento = medicamentoRepositorio.buscarPorId(idGenerado);
        assertTrue(optMedicamento.isPresent(), "El medicamento debe existir en PostgreSQL");

        Medicamento medicamento = optMedicamento.get();
        assertEquals(idGenerado, medicamento.idMedicamento());
        assertEquals(codigo, medicamento.codigo());
        assertEquals("Amoxicilina con Ácido Clavulánico", medicamento.nombre());
        assertEquals("500 mg / 125 mg", medicamento.concentracion());
        assertEquals("TABLETA", medicamento.presentacion());
        assertEquals("CAJA", medicamento.unidadControl());
        assertEquals(new BigDecimal("28.50"), medicamento.precioVenta());
        assertEquals(15, medicamento.stockMinimo());
        assertTrue(medicamento.activo(), "El medicamento nuevo debe crearse con activo = true");
        assertEquals(0, medicamento.stockActual(), "El stock inicial sin lotes debe ser 0");
    }

    @Test
    @DisplayName("MR-02: guardar con concentración nula persiste NULL y buscarPorId lo recupera correctamente")
    void guardar_conConcentracionNula_persisteYRecuperaNull() {
        String codigo = generarCodigoUnico();
        MedicamentoFormulario form = crearFormulario(
                codigo,
                "Alcohol Yodado",
                null,
                "FRASCO",
                "FRASCO",
                new BigDecimal("6.00"),
                20
        );

        Integer idGenerado = medicamentoRepositorio.guardar(form);
        Optional<Medicamento> opt = medicamentoRepositorio.buscarPorId(idGenerado);

        assertTrue(opt.isPresent());
        assertNull(opt.get().concentracion(), "La concentración debe persistirse y recuperarse como null");
    }

    @Test
    @DisplayName("MR-03: buscarPorId con identificador inexistente retorna Optional vacío")
    void buscarPorId_conIdInexistente_retornaOptionalVacio() {
        Optional<Medicamento> opt = medicamentoRepositorio.buscarPorId(999_999);
        assertTrue(opt.isEmpty(), "Debe retornar Optional.empty() para IDs inexistentes");
    }

    @Test
    @DisplayName("MR-04: buscarPorId calcula stock_actual sumando únicamente lotes vigentes")
    void buscarPorId_calculaStockActualSumandoSoloLotesVigentes() {
        String codigo = generarCodigoUnico();
        Integer idMed = medicamentoRepositorio.guardar(crearFormulario(
                codigo, "Paracetamol", "500 mg", "TABLETA", "CAJA", new BigDecimal("5.00"), 10
        ));

        LocalDate hoy = LocalDate.now();
        insertarLoteAuxiliar(idMed, "L-VIG-1", hoy.plusMonths(6), 40);
        insertarLoteAuxiliar(idMed, "L-VIG-2", hoy.plusMonths(12), 25);
        insertarLoteAuxiliar(idMed, "L-VENC-1", hoy.minusDays(1), 50); // Lote vencido: no debe sumarse

        Optional<Medicamento> opt = medicamentoRepositorio.buscarPorId(idMed);
        assertTrue(opt.isPresent());
        assertEquals(65, opt.get().stockActual(), "El stock actual debe ser 40 + 25 = 65 (excluyendo los 50 vencidos)");
    }

    @Test
    @DisplayName("MR-05: actualizar modifica todos los campos y retorna 1 fila afectada")
    void actualizar_modificaCamposExitosamente() {
        String codigoInicial = generarCodigoUnico();
        Integer idMed = medicamentoRepositorio.guardar(crearFormulario(
                codigoInicial, "Ibuprofeno", "200 mg", "TABLETA", "CAJA", new BigDecimal("8.00"), 10
        ));

        String codigoModificado = generarCodigoUnico();
        MedicamentoFormulario formActualizado = crearFormulario(
                codigoModificado, "Ibuprofeno Forte", "400 mg", "CAPSULA", "CAJA", new BigDecimal("14.00"), 25
        );

        int filasActualizadas = medicamentoRepositorio.actualizar(idMed, formActualizado);
        assertEquals(1, filasActualizadas, "Debe reportar 1 fila actualizada");

        Medicamento modificado = medicamentoRepositorio.buscarPorId(idMed).orElseThrow();
        assertEquals(codigoModificado, modificado.codigo());
        assertEquals("Ibuprofeno Forte", modificado.nombre());
        assertEquals("400 mg", modificado.concentracion());
        assertEquals("CAPSULA", modificado.presentacion());
        assertEquals(new BigDecimal("14.00"), modificado.precioVenta());
        assertEquals(25, modificado.stockMinimo());
    }

    @Test
    @DisplayName("MR-06: actualizar con identificador inexistente retorna 0 filas afectadas")
    void actualizar_conIdInexistente_retornaCero() {
        MedicamentoFormulario form = crearFormulario(
                generarCodigoUnico(), "Fantasma", "100 mg", "TABLETA", "CAJA", new BigDecimal("1.00"), 1
        );

        int filas = medicamentoRepositorio.actualizar(999_999, form);
        assertEquals(0, filas, "Debe retornar 0 filas para actualización inexistente");
    }

    @Test
    @DisplayName("MR-07: desactivar y activar actualizan el estado booleano correctamente")
    void desactivarYActivar_modificanEstadoBooleano() {
        Integer idMed = medicamentoRepositorio.guardar(crearFormulario(
                generarCodigoUnico(), "Omeprazol", "20 mg", "CAPSULA", "CAJA", new BigDecimal("12.00"), 10
        ));

        // Desactivar
        int filasDesactivadas = medicamentoRepositorio.desactivar(idMed);
        assertEquals(1, filasDesactivadas);
        assertFalse(medicamentoRepositorio.buscarPorId(idMed).orElseThrow().activo());

        // Activar nuevamente
        int filasActivadas = medicamentoRepositorio.activar(idMed);
        assertEquals(1, filasActivadas);
        assertTrue(medicamentoRepositorio.buscarPorId(idMed).orElseThrow().activo());

        // ID inexistente
        assertEquals(0, medicamentoRepositorio.desactivar(999_999));
        assertEquals(0, medicamentoRepositorio.activar(999_999));
    }

    @Test
    @DisplayName("MR-08: listarActivos y buscarActivos filtran por estado activo y realizan coincidencia de texto")
    void listarActivosYBuscarActivos_filtranYOrdenanCorrectamente() {
        String cod1 = "MED-01-A";
        String cod2 = "MED-02-B";
        String cod3 = "MED-03-C";

        Integer id1 = medicamentoRepositorio.guardar(crearFormulario(cod1, "Paracetamol 500", "500 mg", "TABLETA", "CAJA", new BigDecimal("5.00"), 10));
        Integer id2 = medicamentoRepositorio.guardar(crearFormulario(cod2, "Amoxicilina 500", "500 mg", "CAPSULA", "CAJA", new BigDecimal("15.00"), 10));
        Integer id3 = medicamentoRepositorio.guardar(crearFormulario(cod3, "Paracetamol Jarabe", "120 mg", "JARABE", "FRASCO", new BigDecimal("8.00"), 5));

        // Desactivar medicamento 3
        medicamentoRepositorio.desactivar(id3);

        // 1. listarActivos() debe traer solo 1 y 2
        List<Medicamento> activos = medicamentoRepositorio.listarActivos();
        assertEquals(2, activos.size());
        assertEquals(cod1, activos.get(0).codigo());
        assertEquals(cod2, activos.get(1).codigo());

        // 2. buscarActivos con "parac" debe retornar solo el activo 1 (omitiendo el jarabe inactivo 3)
        List<Medicamento> busqueda = medicamentoRepositorio.buscarActivos("parac");
        assertEquals(1, busqueda.size());
        assertEquals(cod1, busqueda.get(0).codigo());

        // 3. buscarActivos con coincidencia en código "02-B"
        List<Medicamento> busquedaPorCodigo = medicamentoRepositorio.buscarActivos("02-b");
        assertEquals(1, busquedaPorCodigo.size());
        assertEquals(cod2, busquedaPorCodigo.get(0).codigo());
    }

    @Test
    @DisplayName("MR-09: buscarPorEstado permite consultar medicamentos inactivos y activos por texto")
    void buscarPorEstado_retornaMedicamentosSegunEstadoYTexto() {
        String codInactivo = generarCodigoUnico();
        Integer idInactivo = medicamentoRepositorio.guardar(crearFormulario(
                codInactivo, "Aspirina Efervescente", "500 mg", "TABLETA", "CAJA", new BigDecimal("7.00"), 10
        ));
        medicamentoRepositorio.desactivar(idInactivo);

        // Búsqueda en inactivos
        List<Medicamento> inactivos = medicamentoRepositorio.buscarPorEstado("aspirina", false);
        assertEquals(1, inactivos.size());
        assertEquals(codInactivo, inactivos.get(0).codigo());
        assertFalse(inactivos.get(0).activo());

        // Búsqueda en activos para el mismo texto no debe encontrarlo
        List<Medicamento> activos = medicamentoRepositorio.buscarPorEstado("aspirina", true);
        assertTrue(activos.isEmpty());
    }

    @Test
    @DisplayName("MR-10: listarResumenStock calcula existencias vigentes y cantidades pendientes de recepción")
    void listarResumenStock_calculaExistenciasYPendientesDeRecepcion() {
        LocalDate hoy = LocalDate.now();

        // 1. Crear medicamentos
        Integer med1 = medicamentoRepositorio.guardar(crearFormulario(
                generarCodigoUnico(), "Ciprofloxacino", "500 mg", "TABLETA", "CAJA", new BigDecimal("20.00"), 50
        ));
        Integer med2 = medicamentoRepositorio.guardar(crearFormulario(
                generarCodigoUnico(), "Loratadina", "10 mg", "TABLETA", "CAJA", new BigDecimal("10.00"), 30
        ));
        Integer medInactivo = medicamentoRepositorio.guardar(crearFormulario(
                generarCodigoUnico(), "Clorfenamina", "4 mg", "TABLETA", "CAJA", new BigDecimal("3.00"), 10
        ));
        medicamentoRepositorio.desactivar(medInactivo);

        // 2. Lotes para med1: 20 vigentes, 15 vencidos
        insertarLoteAuxiliar(med1, "LOT-MED1-VIG", hoy.plusMonths(4), 20);
        insertarLoteAuxiliar(med1, "LOT-MED1-VENC", hoy.minusDays(2), 15);

        // 3. Proveedor, Usuario y Orden de Compra para probar pendientes de recepción
        Integer idProv = jdbcTemplate.queryForObject("""
                INSERT INTO proveedor (ruc, razon_social, telefono, correo, direccion, activo)
                VALUES ('20111222333', 'Farmacéutica Central S.A.', '012345678', 'ventas@farmacentral.pe', 'Av. Lima 100', TRUE)
                RETURNING id_proveedor
                """, Integer.class);

        Integer idUser = jdbcTemplate.queryForObject("""
                INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
                VALUES ('Usuario Test', 'usertest_mr', 'hash123', 'COMPRAS', TRUE)
                RETURNING id_usuario
                """, Integer.class);

        // Orden de compra pendiente: 25 unidades de med1
        Integer idOrden1 = jdbcTemplate.queryForObject("""
                INSERT INTO orden_compra (numero_orden, id_proveedor, id_usuario, estado, observaciones)
                VALUES ('OC-MR-001', ?, ?, 'PENDIENTE', 'Orden prueba stock')
                RETURNING id_orden
                """, Integer.class, idProv, idUser);

        jdbcTemplate.update("""
                INSERT INTO detalle_orden_compra (id_orden, id_medicamento, cantidad_solicitada, costo_unitario, importe_estimado)
                VALUES (?, ?, 25, 15.00, 375.00)
                """, idOrden1, med1);

        // 4. Invocar listarResumenStock
        List<ResumenStockInventario> resumenes = medicamentoRepositorio.listarResumenStock(hoy);

        // Debe contener med1 y med2 (medInactivo debe estar excluido)
        assertEquals(2, resumenes.size());

        ResumenStockInventario resMed1 = resumenes.stream()
                .filter(r -> r.idMedicamento().equals(med1))
                .findFirst().orElseThrow();

        assertEquals(20, resMed1.stockDisponible(), "El stock disponible debe ser 20 (omitiendo los 15 vencidos)");
        assertEquals(50, resMed1.stockMinimo());
        assertEquals(25, resMed1.pendienteRecibir(), "El pendiente de recibir debe ser 25 de la orden pendiente");

        ResumenStockInventario resMed2 = resumenes.stream()
                .filter(r -> r.idMedicamento().equals(med2))
                .findFirst().orElseThrow();

        assertEquals(0, resMed2.stockDisponible(), "Sin lotes, stock disponible debe ser 0");
        assertEquals(30, resMed2.stockMinimo());
        assertEquals(0, resMed2.pendienteRecibir(), "Sin órdenes, pendiente de recibir debe ser 0");
    }

    @Test
    @DisplayName("MR-11: guardar y buscarPorId gestionan id_categoria opcional (CA-3.3)")
    void guardarYBuscarPorId_conYSinCategoria() {
        Integer idCat = insertarCategoriaAuxiliar("Analgésicos Test", null);

        // Con categoría
        MedicamentoFormulario formConCat = new MedicamentoFormulario(
                generarCodigoUnico(), "Paracetamol Cat", "500 mg", "TABLETA", "CAJA",
                new BigDecimal("5.00"), 10, idCat
        );
        Integer idConCat = medicamentoRepositorio.guardar(formConCat);
        Medicamento medConCat = medicamentoRepositorio.buscarPorId(idConCat).orElseThrow();
        assertEquals(idCat, medConCat.idCategoria(), "Debe persistir y recuperar la categoría asignada");

        // Sin categoría (null)
        MedicamentoFormulario formSinCat = new MedicamentoFormulario(
                generarCodigoUnico(), "Paracetamol Sin Cat", "500 mg", "TABLETA", "CAJA",
                new BigDecimal("5.00"), 10, null
        );
        Integer idSinCat = medicamentoRepositorio.guardar(formSinCat);
        Medicamento medSinCat = medicamentoRepositorio.buscarPorId(idSinCat).orElseThrow();
        assertNull(medSinCat.idCategoria(), "Debe permitir categoría nula sin romper el modelo");
    }

    @Test
    @DisplayName("MR-12: sumarStockDisponiblePorCategorias suma solo lotes vigentes de medicamentos activos en la rama (CA-3.1)")
    void sumarStockDisponiblePorCategorias_sumaLotesVigentesDeMedicamentosActivos() {
        Integer raiz = insertarCategoriaAuxiliar("Analgésicos", null);
        Integer subcat = insertarCategoriaAuxiliar("Antiinflamatorios", raiz);
        Integer otra = insertarCategoriaAuxiliar("Antibióticos", null);

        LocalDate hoy = LocalDate.now();

        // Med 1 en raíz: 20 vigentes + 10 vencidos
        Integer med1 = medicamentoRepositorio.guardar(new MedicamentoFormulario(
                generarCodigoUnico(), "Aspirina", "500 mg", "TABLETA", "CAJA", new BigDecimal("5.00"), 10, raiz
        ));
        insertarLoteAuxiliar(med1, "L-VIG-A", hoy.plusMonths(3), 20);
        insertarLoteAuxiliar(med1, "L-VENC-A", hoy.minusDays(1), 10);

        // Med 2 en subcategoría: 15 vigentes
        Integer med2 = medicamentoRepositorio.guardar(new MedicamentoFormulario(
                generarCodigoUnico(), "Ibuprofeno", "400 mg", "TABLETA", "CAJA", new BigDecimal("8.00"), 10, subcat
        ));
        insertarLoteAuxiliar(med2, "L-VIG-B", hoy.plusMonths(6), 15);

        // Med 3 en otra categoría (no debe sumarse): 50 vigentes
        Integer med3 = medicamentoRepositorio.guardar(new MedicamentoFormulario(
                generarCodigoUnico(), "Amoxicilina", "500 mg", "CAPSULA", "CAJA", new BigDecimal("15.00"), 10, otra
        ));
        insertarLoteAuxiliar(med3, "L-VIG-C", hoy.plusMonths(6), 50);

        // Med 4 inactivo en subcategoría (no debe sumarse): 100 vigentes
        Integer med4 = medicamentoRepositorio.guardar(new MedicamentoFormulario(
                generarCodigoUnico(), "Ketoprofeno Inactivo", "100 mg", "TABLETA", "CAJA", new BigDecimal("12.00"), 10, subcat
        ));
        insertarLoteAuxiliar(med4, "L-VIG-D", hoy.plusMonths(6), 100);
        medicamentoRepositorio.desactivar(med4);

        int totalRama = medicamentoRepositorio.sumarStockDisponiblePorCategorias(List.of(raiz, subcat), hoy);
        assertEquals(35, totalRama, "Debe ser 20 (med1) + 15 (med2) = 35");

        // Lista vacía retorna 0
        assertEquals(0, medicamentoRepositorio.sumarStockDisponiblePorCategorias(List.of(), hoy));
    }

    @Test
    @DisplayName("MR-13: buscarPorEstadoYCategorias filtra medicamentos por categoría y texto (CA-3.2)")
    void buscarPorEstadoYCategorias_filtraPorCategoriasYTexto() {
        Integer raiz = insertarCategoriaAuxiliar("Cardiología", null);
        Integer subcat = insertarCategoriaAuxiliar("Antihipertensivos", raiz);
        Integer otra = insertarCategoriaAuxiliar("Gastroenterología", null);

        Integer m1 = medicamentoRepositorio.guardar(new MedicamentoFormulario(
                "MED-CARD-1", "Losartán 50mg", "50 mg", "TABLETA", "CAJA", new BigDecimal("10.00"), 10, raiz
        ));
        Integer m2 = medicamentoRepositorio.guardar(new MedicamentoFormulario(
                "MED-CARD-2", "Enalapril 20mg", "20 mg", "TABLETA", "CAJA", new BigDecimal("12.00"), 10, subcat
        ));
        Integer m3 = medicamentoRepositorio.guardar(new MedicamentoFormulario(
                "MED-GAST-1", "Omeprazol 20mg", "20 mg", "CAPSULA", "CAJA", new BigDecimal("15.00"), 10, otra
        ));

        // Buscar en la rama {raiz, subcat}
        List<Medicamento> rama = medicamentoRepositorio.buscarPorEstadoYCategorias("", true, List.of(raiz, subcat));
        assertEquals(2, rama.size());
        assertTrue(rama.stream().anyMatch(m -> m.idMedicamento().equals(m1)));
        assertTrue(rama.stream().anyMatch(m -> m.idMedicamento().equals(m2)));

        // Con filtro de texto
        List<Medicamento> filtroTexto = medicamentoRepositorio.buscarPorEstadoYCategorias("losart", true, List.of(raiz, subcat));
        assertEquals(1, filtroTexto.size());
        assertEquals("MED-CARD-1", filtroTexto.get(0).codigo());

        // Categoría sin coincidencias
        List<Medicamento> vacio = medicamentoRepositorio.buscarPorEstadoYCategorias("omeprazol", true, List.of(raiz, subcat));
        assertTrue(vacio.isEmpty());
    }

    @Test
    @DisplayName("MR-14: contarAlertasPorCategorias detecta medicamentos bajo stock mínimo en la rama")
    void contarAlertasPorCategorias_detectaBajoStock() {
        Integer raiz = insertarCategoriaAuxiliar("Respiratorio", null);
        LocalDate hoy = LocalDate.now();

        // Med 1: stockMinimo 20, stockActual 5 -> En alerta
        Integer m1 = medicamentoRepositorio.guardar(new MedicamentoFormulario(
                generarCodigoUnico(), "Salbutamol Inhalador", "100 mcg", "INHALADOR", "FRASCO", new BigDecimal("25.00"), 20, raiz
        ));
        insertarLoteAuxiliar(m1, "L-RESP-1", hoy.plusMonths(5), 5);

        // Med 2: stockMinimo 10, stockActual 30 -> Normal
        Integer m2 = medicamentoRepositorio.guardar(new MedicamentoFormulario(
                generarCodigoUnico(), "Bromhexina", "8 mg", "JARABE", "FRASCO", new BigDecimal("15.00"), 10, raiz
        ));
        insertarLoteAuxiliar(m2, "L-RESP-2", hoy.plusMonths(5), 30);

        int alertas = medicamentoRepositorio.contarAlertasPorCategorias(List.of(raiz), hoy);
        assertEquals(1, alertas, "Debe haber 1 medicamento bajo stock mínimo");
    }
}
