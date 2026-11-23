package com.farmacia.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farmacia.dto.FiltroReporteFormulario;
import com.farmacia.model.Usuario;
import com.farmacia.repository.ClienteRepositorio;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.MedicamentoRepositorio;
import com.farmacia.repository.MovimientoInventarioRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import com.farmacia.repository.VentaRepositorio;
import com.farmacia.service.AlertaInventarioServicio;
import com.farmacia.service.AuditoriaServicio;
import com.farmacia.service.LotePrioridadServicio;
import com.farmacia.service.VentaServicio;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La hora de la aplicación debe ser la de Lima aunque el JVM o el contenedor estén en UTC.
 * Cada prueba simula un contenedor en UTC cambiando la zona por defecto del JVM y la restaura al final.
 */
class ZonaHorariaAplicacionTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 2026-11-24 03:00 UTC = 2026-11-23 22:00 en Lima: ya es "mañana" en UTC pero sigue siendo hoy en Lima. */
    private static final Instant NOCHE_EN_LIMA = Instant.parse("2026-11-24T03:00:00Z");
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 11, 23);

    private TimeZone zonaOriginal;

    @BeforeEach
    void simularContenedorEnUtc() {
        zonaOriginal = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restaurarZona() {
        TimeZone.setDefault(zonaOriginal);
    }

    /** Reloj fijo en la noche de Lima con la zona que usa el bean Clock de la aplicación. */
    private Clock relojDeLaAplicacionA(Instant instante) {
        return Clock.fixed(instante, new SeguridadConfiguracion().clock().getZone());
    }

    @Test
    @DisplayName("(a) El bean Clock de la aplicación usa America/Lima aunque el JVM esté en UTC")
    void beanClockUsaZonaLima() {
        assertThat(new SeguridadConfiguracion().clock().getZone()).isEqualTo(LIMA);
    }

    @Test
    @DisplayName("(b) A las 22:00 de Lima el filtro por defecto de reportes termina hoy (Lima), no mañana")
    void filtroDeReportesUsaHoyDeLima() {
        FiltroReporteFormulario filtro = FiltroReporteFormulario.porDefecto(relojDeLaAplicacionA(NOCHE_EN_LIMA));

        assertThat(filtro.hasta()).isEqualTo(HOY_EN_LIMA);
        assertThat(filtro.desde()).isEqualTo(HOY_EN_LIMA.minusDays(30));
    }

    @Test
    @DisplayName("(b) A las 22:00 de Lima las alertas de vencimiento se calculan con la fecha de Lima")
    void alertasUsanHoyDeLima() {
        LoteRepositorio lotes = mock(LoteRepositorio.class);
        AlertaInventarioServicio servicio = new AlertaInventarioServicio(
                mock(MedicamentoRepositorio.class), lotes, relojDeLaAplicacionA(NOCHE_EN_LIMA), 90);

        servicio.listarVencidos();
        servicio.listarPorVencer();

        verify(lotes).listarVencidos(HOY_EN_LIMA);
        verify(lotes).listarPorVencer(HOY_EN_LIMA, HOY_EN_LIMA.plusDays(90));
    }

    @Test
    @DisplayName("(b) A las 22:00 de Lima \"Mis ventas\" muestra por defecto el día de hoy en Lima")
    void misVentasUsaHoyDeLima() {
        UsuarioRepositorio usuarios = mock(UsuarioRepositorio.class);
        VentaRepositorio ventas = mock(VentaRepositorio.class);
        when(usuarios.buscarPorNombreUsuario("vendedor"))
                .thenReturn(Optional.of(new Usuario(2, "Vendedor", "vendedor", "hash", "VENDEDOR", true)));
        when(ventas.contar(any())).thenReturn(0);
        when(ventas.listar(any(), anyInt(), anyInt())).thenReturn(List.of());
        VentaServicio servicio = new VentaServicio(usuarios, mock(ClienteRepositorio.class),
                mock(LotePrioridadServicio.class), mock(LoteRepositorio.class), ventas,
                mock(MovimientoInventarioRepositorio.class), relojDeLaAplicacionA(NOCHE_EN_LIMA),
                mock(AuditoriaServicio.class));

        VentaServicio.MisVentas misVentas = servicio.listarMisVentas("vendedor", null, null);

        assertThat(misVentas.desde()).isEqualTo(HOY_EN_LIMA);
        assertThat(misVentas.hasta()).isEqualTo(HOY_EN_LIMA);
    }
}
