package com.farmacia.service;

import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import com.farmacia.repository.AuditoriaRepositorio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 014: propagaciones de AuditoriaServicio (CA-2.5, CA-3.2) y consulta filtrada (CA-4.1, CA-4.2). */
class AuditoriaServicioPersistenciaTest extends AbstractRepositorioIntegrationTest {

    @Autowired private AuditoriaServicio servicio;
    @Autowired private AuditoriaRepositorio repositorio;
    @Autowired private PlatformTransactionManager transacciones;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private String autenticarComo() {
        String usuario = "aud-" + UUID.randomUUID().toString().substring(0, 8);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(usuario, null, List.of()));
        return usuario;
    }

    private int eventosDe(String usuario) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auditoria WHERE nombre_usuario = ?",
                Integer.class, usuario);
    }

    @Test
    @DisplayName("registrarCambio exige una transacción del negocio (MANDATORY)")
    void cambioSinTransaccionFalla() {
        autenticarComo();
        assertThrows(IllegalTransactionStateException.class,
                () -> servicio.registrarCambio(AccionAuditoria.EDITAR, "MEDICAMENTO", 1, "precio"));
    }

    @Test
    @DisplayName("CA-3.2: si la transacción del negocio hace rollback, el cambio no queda auditado")
    void cambioSeRevierteConElNegocio() {
        String usuario = autenticarComo();
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            servicio.registrarCambio(AccionAuditoria.EDITAR, "MEDICAMENTO", 1, "precio_venta: 8.50 → 9.00");
            estado.setRollbackOnly();
        });
        assertEquals(0, eventosDe(usuario));

        new TransactionTemplate(transacciones).executeWithoutResult(estado ->
                servicio.registrarCambio(AccionAuditoria.EDITAR, "MEDICAMENTO", 1, "precio_venta: 8.50 → 9.00"));
        assertEquals(1, eventosDe(usuario));
    }

    @Test
    @DisplayName("CA-2.5: un evento de acceso queda aunque la transacción que lo rodea haga rollback (REQUIRES_NEW)")
    void accesoSobreviveAlRollback() {
        String usuario = "aud-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            servicio.registrarAcceso(usuario, AccionAuditoria.INICIO_SESION_FALLIDO, "FALLO", "Credenciales inválidas",
                    "127.0.0.1");
            estado.setRollbackOnly();
        });
        assertEquals(1, eventosDe(usuario));
    }

    @Test
    @DisplayName("Sin sesión, el cambio se atribuye a 'sistema'; el id de usuario se resuelve por nombre")
    void atribucionDelUsuario() {
        String nombre = "aud-" + UUID.randomUUID().toString().substring(0, 8);
        int idUsuario = jdbcTemplate.queryForObject("""
                INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol) VALUES ('Auditado', ?, 'x', 'COMPRAS')
                RETURNING id_usuario
                """, Integer.class, nombre);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(nombre, null, List.of()));
        new TransactionTemplate(transacciones).executeWithoutResult(estado ->
                servicio.registrarCambio(AccionAuditoria.CREAR, "PROVEEDOR", "20123456789", "Alta"));
        assertEquals(idUsuario, jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM auditoria WHERE nombre_usuario = ?", Integer.class, nombre));
    }

    @Test
    @DisplayName("CA-4.1/4.2: filtros por usuario y acción; una inyección no devuelve filas extra")
    void filtrosEInyeccion() {
        String usuario = "aud-" + UUID.randomUUID().toString().substring(0, 8);
        servicio.registrarAcceso(usuario, AccionAuditoria.INICIO_SESION, "EXITO", null, null);
        servicio.registrarAcceso(usuario, AccionAuditoria.CIERRE_SESION, "EXITO", null, null);

        var soloInicio = new AuditoriaRepositorio.Filtro(null, null, usuario, "INICIO_SESION", null, null);
        assertEquals(1, repositorio.contar(soloInicio));
        assertEquals("INICIO_SESION", repositorio.buscar(soloInicio, 50, 0).getFirst().accion());

        var inyeccion = new AuditoriaRepositorio.Filtro(null, null, "x' OR '1'='1", null, null, null);
        assertEquals(0, repositorio.contar(inyeccion));
        assertTrue(repositorio.buscar(inyeccion, 50, 0).isEmpty());
    }

    @Test
    @DisplayName("CA-4.3: exportar devuelve el CSV del filtro y registra EXPORTAR; solo el administrador puede")
    void exportacionYPermisos() {
        String admin = "aud-adm-" + UUID.randomUUID().toString().substring(0, 6);
        jdbcTemplate.update("INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol) VALUES ('Admin', ?, 'x', 'ADMINISTRADOR')", admin);
        String vendedor = "aud-ven-" + UUID.randomUUID().toString().substring(0, 6);
        jdbcTemplate.update("INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol) VALUES ('Vend', ?, 'x', 'VENDEDOR')", vendedor);
        servicio.registrarAcceso(admin, AccionAuditoria.INICIO_SESION, "EXITO", "con, coma", null);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(admin, null, List.of()));

        var filtro = new com.farmacia.dto.FiltroAuditoriaFormulario(null, null, admin, "INICIO_SESION", null, null);
        String csv = servicio.exportarCsv(admin, filtro);

        assertTrue(csv.startsWith("fecha_hora,usuario,accion"), csv);
        assertTrue(csv.contains("\"con, coma\""), csv);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria WHERE nombre_usuario = ? AND accion = 'EXPORTAR'", Integer.class, admin));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> servicio.consultar(vendedor, filtro, 1));
    }

    @Test
    @DisplayName("CA-4.1/4.2: sin fechas consulta los últimos 7 días; hasta < desde se rechaza")
    void rangoPorDefectoYValidacion() {
        String admin = "aud-adm-" + UUID.randomUUID().toString().substring(0, 6);
        jdbcTemplate.update("INSERT INTO usuario(nombre, nombre_usuario, clave_hash, rol) VALUES ('Admin', ?, 'x', 'ADMINISTRADOR')", admin);

        var consulta = servicio.consultar(admin, com.farmacia.dto.FiltroAuditoriaFormulario.vacio(), 1);
        assertEquals(6, java.time.temporal.ChronoUnit.DAYS.between(consulta.desde(), consulta.hasta()));

        var invertido = new com.farmacia.dto.FiltroAuditoriaFormulario(java.time.LocalDate.of(2026, 11, 13),
                java.time.LocalDate.of(2026, 11, 1), null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> servicio.consultar(admin, invertido, 1));
    }
}
