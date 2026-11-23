package com.farmacia.database;

import com.farmacia.model.UsuarioListadoVista;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import com.farmacia.repository.UsuarioRepositorio;
import com.farmacia.service.UsuarioServicio;
import com.farmacia.util.FormatoUltimoAcceso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioUltimoAccesoDemoPostgresTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private UsuarioRepositorio repositorio;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Con sesion PostgreSQL en UTC, semillas con NOW() AT TIME ZONE 'America/Lima' muestran Hoy y Ayer")
    void semillasConPostgresUtcMuestranHoyYAyerConClockLima() {
        try {
            // 1. Forzar la sesion de PostgreSQL en UTC
            jdbcTemplate.execute("SET TIME ZONE 'UTC'");

            String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String adminUser = "admin_d_" + sufijo;
            String almacenUser = "almacen_d_" + sufijo;
            String comprasUser = "compras_d_" + sufijo;
            String vendedorUser = "vendedor_d_" + sufijo;

            // 2. Insertar usando la misma logica de R__datos_demo.sql con zona America/Lima en PostgreSQL
            // Para garantizar que Almacen siempre caiga en el dia previo a Admin sin importar si el test corre
            // cerca de medianoche (entre 00:00 y 02:00), se desfasa 1 dia completo respecto al instante de Admin (-26 horas).
            jdbcTemplate.update("""
                    INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo, ultimo_acceso) VALUES
                    ('Admin Demo Test', ?, '{bcrypt}$2a$10$demoHash', 'ADMINISTRADOR', true, (NOW() AT TIME ZONE 'America/Lima') - INTERVAL '2 hours'),
                    ('Almacen Demo Test', ?, '{bcrypt}$2a$10$demoHash', 'ALMACENERO', true, (NOW() AT TIME ZONE 'America/Lima') - INTERVAL '26 hours'),
                    ('Compras Demo Test', ?, '{bcrypt}$2a$10$demoHash', 'COMPRAS', true, (NOW() AT TIME ZONE 'America/Lima') - INTERVAL '10 days'),
                    ('Vendedor Demo Test', ?, '{bcrypt}$2a$10$demoHash', 'VENDEDOR', true, NULL)
                    """, adminUser, almacenUser, comprasUser, vendedorUser);

            // 3. Obtener la fecha/hora almacenada para fijar el Clock de forma segura ante el borde de medianoche
            LocalDateTime fechaAdmin = jdbcTemplate.queryForObject(
                    "SELECT ultimo_acceso FROM usuario WHERE nombre_usuario = ?",
                    LocalDateTime.class,
                    adminUser);
            LocalDateTime fechaAlmacen = jdbcTemplate.queryForObject(
                    "SELECT ultimo_acceso FROM usuario WHERE nombre_usuario = ?",
                    LocalDateTime.class,
                    almacenUser);

            assertNotNull(fechaAdmin);
            assertNotNull(fechaAlmacen);

            // Fijar el Clock de prueba a las 15:00 del mismo dia de la fecha calculada para admin_demo
            Clock clockLima = Clock.fixed(
                    fechaAdmin.toLocalDate().atTime(15, 0).atZone(FormatoUltimoAcceso.ZONA_LIMA).toInstant(),
                    FormatoUltimoAcceso.ZONA_LIMA);

            UsuarioServicio servicio = new UsuarioServicio(repositorio, passwordEncoder, null, null, clockLima);
            List<UsuarioListadoVista> listado = servicio.listarParaListado(adminUser);

            UsuarioListadoVista vistaAdmin = listado.stream()
                    .filter(u -> u.nombreUsuario().equals(adminUser))
                    .findFirst()
                    .orElseThrow();
            UsuarioListadoVista vistaAlmacen = listado.stream()
                    .filter(u -> u.nombreUsuario().equals(almacenUser))
                    .findFirst()
                    .orElseThrow();
            UsuarioListadoVista vistaVendedor = listado.stream()
                    .filter(u -> u.nombreUsuario().equals(vendedorUser))
                    .findFirst()
                    .orElseThrow();

            String fmtAdmin = FormatoUltimoAcceso.formatear(vistaAdmin.ultimoAcceso(), clockLima);
            String fmtAlmacen = FormatoUltimoAcceso.formatear(vistaAlmacen.ultimoAcceso(), clockLima);
            String fmtVendedor = FormatoUltimoAcceso.formatear(vistaVendedor.ultimoAcceso(), clockLima);

            assertTrue(fmtAdmin.startsWith("Hoy "), "admin_demo debe mostrar 'Hoy HH:mm', pero fue: " + fmtAdmin);
            assertTrue(fmtAlmacen.startsWith("Ayer "), "almacen_demo debe mostrar 'Ayer HH:mm', pero fue: " + fmtAlmacen);
            assertTrue(fmtVendedor.equals("Nunca"), "vendedor_demo debe mostrar 'Nunca'");
        } finally {
            jdbcTemplate.execute("RESET TIME ZONE");
        }
    }
}
