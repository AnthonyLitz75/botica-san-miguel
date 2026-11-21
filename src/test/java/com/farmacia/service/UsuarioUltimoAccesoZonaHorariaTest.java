package com.farmacia.service;

import com.farmacia.model.UsuarioListadoVista;
import com.farmacia.repository.AbstractRepositorioIntegrationTest;
import com.farmacia.repository.UsuarioRepositorio;
import com.farmacia.util.FormatoUltimoAcceso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UsuarioUltimoAccesoZonaHorariaTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private UsuarioRepositorio repositorio;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditoriaServicio auditoria;

    @Test
    @DisplayName("Guarda acceso con Clock en America/Lima con JVM en UTC y muestra 'Hoy HH:mm' con hora de Lima")
    void guardarAccesoConClockLimaConJvmEnUtcMuestraHoyConHoraLima() {
        TimeZone zonaOriginal = TimeZone.getDefault();
        try {
            // Forzar zona por defecto de la JVM en UTC
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            // Instante fijo: 2026-11-21 15:45:00 UTC -> en America/Lima (UTC-5) son las 10:45:00 del 2026-11-21
            Instant instanteUtc = Instant.parse("2026-11-21T15:45:00Z");
            Clock clockLima = Clock.fixed(instanteUtc, FormatoUltimoAcceso.ZONA_LIMA);

            // Crear UsuarioServicio configurado con el Clock fijo en America/Lima
            UsuarioServicio servicio = new UsuarioServicio(repositorio, passwordEncoder, auditoria, null, clockLima);

            String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String adminUser = "admin_zh_" + sufijo;
            String normalUser = "user_zh_" + sufijo;

            repositorio.insertar("Admin ZH", adminUser, "{bcrypt}h1", "ADMINISTRADOR");
            repositorio.insertar("Normal ZH", normalUser, "{bcrypt}h2", "VENDEDOR");

            // Guardar acceso para normalUser (calcula LocalDateTime en America/Lima vía Clock)
            servicio.registrarAcceso(normalUser);

            // Consultar listado a través de UsuarioServicio
            List<UsuarioListadoVista> listado = servicio.listarParaListado(adminUser);
            UsuarioListadoVista vista = listado.stream()
                    .filter(u -> u.nombreUsuario().equals(normalUser))
                    .findFirst()
                    .orElseThrow();

            assertNotNull(vista.ultimoAcceso(), "El último acceso debe haber sido registrado");

            // Formatear usando FormatoUltimoAcceso con el mismo Clock fijo en Lima
            String formateado = FormatoUltimoAcceso.formatear(vista.ultimoAcceso(), clockLima);

            // Debe mostrar "Hoy 10:45", correspondiente a las 10:45 de Lima (y NO 15:45 de UTC)
            assertEquals("Hoy 10:45", formateado,
                    "Debe formatear como 'Hoy 10:45' usando la hora de America/Lima a pesar de que la JVM esté en UTC");

        } finally {
            TimeZone.setDefault(zonaOriginal);
        }
    }
}
