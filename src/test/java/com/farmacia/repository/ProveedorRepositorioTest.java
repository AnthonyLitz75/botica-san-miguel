package com.farmacia.repository;

import com.farmacia.dto.ProveedorFormulario;
import com.farmacia.model.Proveedor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Integración de ProveedorRepositorio (Testcontainers + PostgreSQL)")
class ProveedorRepositorioTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private ProveedorRepositorio proveedorRepositorio;

    private static final AtomicLong contadorRuc = new AtomicLong(100_000_000L);

    @BeforeEach
    void setUp() {
        limpiarBaseDeDatos();
    }

    private String generarRucValido() {
        return "20" + String.format("%09d", contadorRuc.incrementAndGet());
    }

    @Test
    @DisplayName("PR-01: guardar persiste entidad completa y buscarPorId recupera todos los campos")
    void guardar_persisteEntidadCompletaYBuscarPorIdRecuperaDatos() {
        String ruc = generarRucValido();
        var form = new ProveedorFormulario(
                ruc,
                "Distribuidora Médica del Norte S.A.C.",
                "014455667",
                "contacto@mednorte.pe",
                "Av. Nicolás de Piérola 450, Lima"
        );

        proveedorRepositorio.guardar(form);

        Integer idGenerado = jdbcTemplate.queryForObject(
                "SELECT id_proveedor FROM proveedor WHERE ruc = ?",
                Integer.class, ruc);
        assertNotNull(idGenerado);

        Optional<Proveedor> resultado = proveedorRepositorio.buscarPorId(idGenerado);
        assertTrue(resultado.isPresent(), "El proveedor debe existir en PostgreSQL");

        Proveedor proveedor = resultado.get();
        assertEquals(idGenerado, proveedor.idProveedor());
        assertEquals(ruc, proveedor.ruc());
        assertEquals("Distribuidora Médica del Norte S.A.C.", proveedor.razonSocial());
        assertEquals("014455667", proveedor.telefono());
        assertEquals("contacto@mednorte.pe", proveedor.correo());
        assertEquals("Av. Nicolás de Piérola 450, Lima", proveedor.direccion());
        assertTrue(proveedor.activo(), "El proveedor nuevo debe crearse con activo = true");
    }

    @Test
    @DisplayName("PR-02: guardar con valores opcionales nulos persiste y recupera NULL tanto en objeto como en PostgreSQL")
    void guardar_conOpcionalesNulos_persisteYRecuperaNull() {
        String ruc = generarRucValido();
        var formSinOpcionales = new ProveedorFormulario(
                ruc,
                "Farmacéutica Esencial S.A.",
                null,
                null,
                null
        );

        proveedorRepositorio.guardar(formSinOpcionales);

        Integer idGenerado = jdbcTemplate.queryForObject(
                "SELECT id_proveedor FROM proveedor WHERE ruc = ?",
                Integer.class, ruc);
        assertNotNull(idGenerado);

        // 1. Verificar en el record devuelto por el repositorio
        Proveedor proveedor = proveedorRepositorio.buscarPorId(idGenerado).orElseThrow();
        assertEquals("Farmacéutica Esencial S.A.", proveedor.razonSocial());
        assertNull(proveedor.telefono(), "El teléfono debe recuperarse como null");
        assertNull(proveedor.correo(), "El correo debe recuperarse como null");
        assertNull(proveedor.direccion(), "La dirección debe recuperarse como null");

        // 2. Verificar directamente contra valores SQL NULL en PostgreSQL
        Boolean columnasNulasEnBd = jdbcTemplate.queryForObject("""
                SELECT telefono IS NULL AND correo IS NULL AND direccion IS NULL
                FROM proveedor WHERE id_proveedor = ?
                """, Boolean.class, idGenerado);
        assertTrue(columnasNulasEnBd, "PostgreSQL debe almacenar valores estrictamente NULL en las tres columnas");
    }

    @Test
    @DisplayName("PR-03: guardar rechaza RUC duplicado lanzando DuplicateKeyException por restricción UNIQUE")
    void guardar_rucDuplicado_lanzaDuplicateKeyException() {
        String ruc = generarRucValido();
        var form1 = new ProveedorFormulario(ruc, "Proveedor Original S.A.", "111", "a@a.com", "Dir 1");
        var formDuplicado = new ProveedorFormulario(ruc, "Proveedor Intruso S.A.", "222", "b@b.com", "Dir 2");

        proveedorRepositorio.guardar(form1);

        assertThrows(DuplicateKeyException.class,
                () -> proveedorRepositorio.guardar(formDuplicado),
                "La base de datos debe impedir RUCs duplicados por la restricción proveedor_ruc_key UNIQUE");
    }

    @Test
    @DisplayName("PR-04: guardar rechaza formato inválido de RUC lanzando DataIntegrityViolationException")
    void guardar_rucFormatoInvalido_lanzaDataIntegrityViolationException() {
        // La restricción DDL es CHECK (ruc ~ '^[0-9]{11}$')
        var formRucConLetras = new ProveedorFormulario("20123ABC789", "Invalido S.A.", "111", "a@a.com", "Dir");
        var formRucCorto = new ProveedorFormulario("2012345678", "Invalido S.A.", "111", "a@a.com", "Dir");

        assertThrows(DataIntegrityViolationException.class,
                () -> proveedorRepositorio.guardar(formRucConLetras),
                "PostgreSQL debe rechazar RUCs con caracteres no numéricos por chk_proveedor_ruc");

        assertThrows(DataIntegrityViolationException.class,
                () -> proveedorRepositorio.guardar(formRucCorto),
                "PostgreSQL debe rechazar RUCs con longitud distinta de 11 dígitos por chk_proveedor_ruc");
    }

    @Test
    @DisplayName("PR-05: buscarPorEstado filtra por coincidencia parcial case-insensitive en razón social y RUC")
    void buscarPorEstado_coincidenciaParcialCaseInsensitiveEnRazonSocialYRuc() {
        String rucCentral = "20999888777";
        String rucSur = "20111222333";

        jdbcTemplate.update("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'DROGUERIA CENTRAL DEL PERU', true),
                   (?, 'LABORATORIOS DEL SUR S.A.', true)
            """, rucCentral, rucSur);

        // Búsqueda en minúsculas contra razón social en mayúsculas
        List<Proveedor> porNombre = proveedorRepositorio.buscarPorEstado("drogueria", true);
        assertEquals(1, porNombre.size());
        assertEquals("DROGUERIA CENTRAL DEL PERU", porNombre.get(0).razonSocial());

        // Búsqueda por subcadena de RUC
        List<Proveedor> porRuc = proveedorRepositorio.buscarPorEstado("999888", true);
        assertEquals(1, porRuc.size());
        assertEquals(rucCentral, porRuc.get(0).ruc());
    }

    @Test
    @DisplayName("PR-06: buscarPorEstado con texto vacío lista todos los proveedores del estado indicado")
    void buscarPorEstado_conTextoVacio_listaTodosDelEstado() {
        jdbcTemplate.update("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'Prov Activo 1', true),
                   (?, 'Prov Activo 2', true),
                   (?, 'Prov Inactivo 1', false)
            """, generarRucValido(), generarRucValido(), generarRucValido());

        List<Proveedor> activos = proveedorRepositorio.buscarPorEstado("", true);
        List<Proveedor> inactivos = proveedorRepositorio.buscarPorEstado("", false);

        assertEquals(2, activos.size(), "Debe retornar exactamente los 2 activos");
        assertEquals(1, inactivos.size(), "Debe retornar exactamente el inactivo");
    }

    @Test
    @DisplayName("PR-07: buscarPorEstado sin coincidencias retorna lista vacía")
    void buscarPorEstado_sinCoincidencias_retornaListaVacia() {
        jdbcTemplate.update("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'Droguería San Pedro', true)
            """, generarRucValido());

        List<Proveedor> resultado = proveedorRepositorio.buscarPorEstado("TERMINO_INEXISTENTE_XYZ", true);
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty(), "La consulta debe retornar una lista vacía cuando no hay coincidencias");
    }

    @Test
    @DisplayName("PR-08: buscarPorEstado ordena por razon_social ASC y desempata por id_proveedor ASC con IDs dinámicos")
    void buscarPorEstado_ordenaPorRazonSocialYDesempataPorIdProveedor() {
        String rucAlfa1 = generarRucValido();
        String rucAlfa2 = generarRucValido();
        String rucBeta = generarRucValido();

        // 1. Insertar capturando los identificadores autogenerados reales (sin asumir secuencias desde 1)
        Integer idAlfa1 = jdbcTemplate.queryForObject("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'Pharma Alfa S.A.C.', true)
            RETURNING id_proveedor
            """, Integer.class, rucAlfa1);

        Integer idAlfa2 = jdbcTemplate.queryForObject("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'Pharma Alfa S.A.C.', true)
            RETURNING id_proveedor
            """, Integer.class, rucAlfa2);

        Integer idBeta = jdbcTemplate.queryForObject("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'Pharma Beta S.A.C.', true)
            RETURNING id_proveedor
            """, Integer.class, rucBeta);

        assertTrue(idAlfa2 > idAlfa1, "Por orden de inserción idAlfa2 debe ser estrictamente mayor que idAlfa1");

        // 2. Ejecutar búsqueda filtrando por el término común 'Pharma'
        List<Proveedor> resultado = proveedorRepositorio.buscarPorEstado("Pharma", true);

        assertEquals(3, resultado.size());
        // Posición 0: 'Pharma Alfa S.A.C.' con menor id_proveedor (idAlfa1)
        assertEquals(idAlfa1, resultado.get(0).idProveedor());
        assertEquals("Pharma Alfa S.A.C.", resultado.get(0).razonSocial());

        // Posición 1: 'Pharma Alfa S.A.C.' con mayor id_proveedor (idAlfa2 por desempate secundario ASC)
        assertEquals(idAlfa2, resultado.get(1).idProveedor());
        assertEquals("Pharma Alfa S.A.C.", resultado.get(1).razonSocial());

        // Posición 2: 'Pharma Beta S.A.C.' alfabéticamente posterior
        assertEquals(idBeta, resultado.get(2).idProveedor());
        assertEquals("Pharma Beta S.A.C.", resultado.get(2).razonSocial());
    }

    @Test
    @DisplayName("PR-09: listarActivos y listarPorEstado filtran fielmente según el estado booleano")
    void listarActivosYListarPorEstado_filtranSegunEstadoBooleano() {
        jdbcTemplate.update("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'Activo A', true),
                   (?, 'Activo B', true),
                   (?, 'Activo C', true),
                   (?, 'Inactivo D', false),
                   (?, 'Inactivo E', false)
            """, generarRucValido(), generarRucValido(), generarRucValido(), generarRucValido(), generarRucValido());

        List<Proveedor> activos = proveedorRepositorio.listarActivos();
        List<Proveedor> inactivos = proveedorRepositorio.listarPorEstado(false);

        assertEquals(3, activos.size(), "listarActivos() debe devolver exactamente los 3 proveedores activos");
        assertTrue(activos.stream().allMatch(Proveedor::activo));

        assertEquals(2, inactivos.size(), "listarPorEstado(false) debe devolver exactamente los 2 proveedores inactivos");
        assertTrue(inactivos.stream().noneMatch(Proveedor::activo));
    }

    @Test
    @DisplayName("PR-10: actualizar modifica todos los campos de proveedor existente y retorna 0 si no existe")
    void actualizar_modificaCamposDeExistenteYRetornaCeroSiNoExiste() {
        String rucInicial = generarRucValido();
        Integer idProv = jdbcTemplate.queryForObject("""
            INSERT INTO proveedor (ruc, razon_social, telefono, correo, direccion, activo)
            VALUES (?, 'Razon Inicial', '111', 'ini@prov.com', 'Dir Ini', true)
            RETURNING id_proveedor
            """, Integer.class, rucInicial);

        String rucModificado = generarRucValido();
        var formModificado = new ProveedorFormulario(
                rucModificado,
                "Razón Social Actualizada S.A.",
                "019998877",
                "actualizado@prov.com",
                "Nueva Dirección 789"
        );

        // 1. Actualización exitosa
        int filasModificadas = proveedorRepositorio.actualizar(idProv, formModificado);
        assertEquals(1, filasModificadas, "Debe reportar 1 fila afectada");

        Proveedor provActualizado = proveedorRepositorio.buscarPorId(idProv).orElseThrow();
        assertEquals(rucModificado, provActualizado.ruc());
        assertEquals("Razón Social Actualizada S.A.", provActualizado.razonSocial());
        assertEquals("019998877", provActualizado.telefono());
        assertEquals("actualizado@prov.com", provActualizado.correo());
        assertEquals("Nueva Dirección 789", provActualizado.direccion());

        // 2. Actualización de ID inexistente
        int filasInexistente = proveedorRepositorio.actualizar(999_999, formModificado);
        assertEquals(0, filasInexistente, "Debe retornar 0 filas afectadas para ID inexistente");
    }

    @Test
    @DisplayName("PR-11: actualizar con RUC de otro proveedor existente lanza DuplicateKeyException")
    void actualizar_conRucDuplicado_lanzaDuplicateKeyException() {
        String rucA = generarRucValido();
        String rucB = generarRucValido();

        jdbcTemplate.update("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'Proveedor A', true)
            """, rucA);

        Integer idB = jdbcTemplate.queryForObject("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'Proveedor B', true)
            RETURNING id_proveedor
            """, Integer.class, rucB);

        // Intentar actualizar Prov B asignándole el RUC ya existente de Prov A
        var formColision = new ProveedorFormulario(rucA, "Proveedor B Renombrado", "123", "b@b.com", "Dir B");

        assertThrows(DuplicateKeyException.class,
                () -> proveedorRepositorio.actualizar(idB, formColision),
                "La base de datos debe impedir actualizar hacia un RUC perteneciente a otro proveedor");
    }

    @Test
    @DisplayName("PR-12: desactivar y activar conmutan el estado lógico y retornan 0 para ID inexistente")
    void desactivarYActivar_conmutanEstadoYRetornanCeroParaInexistente() {
        String ruc = generarRucValido();
        Integer idProv = jdbcTemplate.queryForObject("""
            INSERT INTO proveedor (ruc, razon_social, activo)
            VALUES (?, 'Proveedor Transiciones', true)
            RETURNING id_proveedor
            """, Integer.class, ruc);

        // 1. Desactivar
        int filasDesactivar = proveedorRepositorio.desactivar(idProv);
        assertEquals(1, filasDesactivar);
        assertFalse(proveedorRepositorio.buscarPorId(idProv).orElseThrow().activo(), "El proveedor debe estar inactivo");

        // 2. Activar
        int filasActivar = proveedorRepositorio.activar(idProv);
        assertEquals(1, filasActivar);
        assertTrue(proveedorRepositorio.buscarPorId(idProv).orElseThrow().activo(), "El proveedor debe volver a estar activo");

        // 3. Casos con ID inexistente
        assertEquals(0, proveedorRepositorio.desactivar(999_999), "Desactivar ID inexistente retorna 0");
        assertEquals(0, proveedorRepositorio.activar(999_999), "Activar ID inexistente retorna 0");
    }
}
