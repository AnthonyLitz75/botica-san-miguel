package com.farmacia.service;

import com.farmacia.dto.FiltroAuditoriaFormulario;
import com.farmacia.model.EventoAuditoria;
import com.farmacia.repository.AuditoriaRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bitácora de auditoría (spec 014).
 * <ul>
 *   <li>{@link #registrarCambio}: cambios de datos, <b>dentro</b> de la transacción del negocio
 *       ({@code MANDATORY}): si la operación hace rollback, el evento tampoco queda.</li>
 *   <li>{@link #registrarAcceso}: accesos (login, logout, denegaciones), en una transacción
 *       <b>propia</b> ({@code REQUIRES_NEW}): quedan aunque lo demás falle.</li>
 * </ul>
 * El detalle nunca debe contener claves, hashes ni tokens: quien llama pasa solo descripciones.
 */
@Service
public class AuditoriaServicio {
    public static final String USUARIO_SISTEMA = "sistema";
    public static final String USUARIO_ANONIMO = "anónimo";
    private static final int MAX_USUARIO = 50;
    private static final int MAX_DETALLE = 500;
    private static final int MAX_ID = 40;

    public static final int EVENTOS_POR_PAGINA = 50;
    private static final int MAX_FILAS_CSV = 10_000;
    private static final int DIAS_POR_DEFECTO = 7;

    private final AuditoriaRepositorio repositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final Clock clock;

    public AuditoriaServicio(AuditoriaRepositorio repositorio, UsuarioRepositorio usuarioRepositorio, Clock clock) {
        this.repositorio = repositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.clock = clock;
    }

    /** Resultado de una consulta paginada con el rango de fechas efectivo. */
    public record Consulta(List<EventoAuditoria> eventos, int total, int pagina, int paginas,
            LocalDate desde, LocalDate hasta) {
    }

    /**
     * Consulta de la bitácora (solo ADMINISTRADOR). Sin fechas, los últimos 7 días según el reloj;
     * {@code hasta < desde} se rechaza sin consultar.
     */
    public Consulta consultar(String nombreUsuario, FiltroAuditoriaFormulario filtro, int pagina) {
        validarAdministrador(nombreUsuario);
        var efectivo = filtroEfectivo(filtro);
        int total = repositorio.contar(efectivo);
        int paginas = Math.max(1, (total + EVENTOS_POR_PAGINA - 1) / EVENTOS_POR_PAGINA);
        int actual = Math.min(Math.max(1, pagina), paginas);
        var eventos = repositorio.buscar(efectivo, EVENTOS_POR_PAGINA, (actual - 1) * EVENTOS_POR_PAGINA);
        return new Consulta(eventos, total, actual, paginas, efectivo.desde(), efectivo.hasta());
    }

    /** Exporta el filtro a CSV (RFC 4180) y registra el evento EXPORTAR en la misma transacción. */
    @Transactional(rollbackFor = Exception.class)
    public String exportarCsv(String nombreUsuario, FiltroAuditoriaFormulario filtro) {
        validarAdministrador(nombreUsuario);
        var efectivo = filtroEfectivo(filtro);
        var csv = new CsvEscritor().fila("fecha_hora", "usuario", "accion", "entidad", "id_entidad",
                "resultado", "detalle", "ip_origen");
        var eventos = repositorio.buscar(efectivo, MAX_FILAS_CSV, 0);
        for (var e : eventos) {
            csv.fila(e.fechaHora(), e.nombreUsuario(), e.accion(), e.entidad(), e.idEntidad(), e.resultado(),
                    e.detalle(), e.ipOrigen());
        }
        registrarCambio(AccionAuditoria.EXPORTAR, "AUDITORIA", null,
                "filas: " + eventos.size() + "; desde: " + efectivo.desde() + "; hasta: " + efectivo.hasta());
        return csv.toString();
    }

    private AuditoriaRepositorio.Filtro filtroEfectivo(FiltroAuditoriaFormulario filtro) {
        var f = filtro == null ? FiltroAuditoriaFormulario.vacio() : filtro;
        LocalDate hoy = LocalDate.now(clock);
        LocalDate hasta = f.hasta() == null ? hoy : f.hasta();
        LocalDate desde = f.desde() == null ? hasta.minusDays(DIAS_POR_DEFECTO - 1) : f.desde();
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial");
        }
        return new AuditoriaRepositorio.Filtro(desde, hasta, f.usuario(), f.accion(), f.entidad(), f.resultado());
    }

    private void validarAdministrador(String nombreUsuario) {
        var usuario = usuarioRepositorio.buscarPorNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        if (!usuario.activo() || !usuario.rol().equals("ADMINISTRADOR")) {
            throw new AccessDeniedException("Usuario no autorizado");
        }
    }

    /**
     * Registra un cambio de datos hecho por el usuario autenticado (se toma del contexto de seguridad;
     * "sistema" si no hay sesión, p. ej. en procesos internos).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarCambio(AccionAuditoria accion, String entidad, Object idEntidad, String detalle) {
        repositorio.insertar(LocalDateTime.now(clock), usuarioActual(), accion.name(), entidad,
                recortar(idEntidad == null ? null : String.valueOf(idEntidad), MAX_ID), "EXITO",
                recortar(detalle, MAX_DETALLE), null);
    }

    /** Registra un evento de acceso con el nombre de usuario indicado (el escrito en el login, nunca la clave). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAcceso(String nombreUsuario, AccionAuditoria accion, String resultado, String detalle,
            String ipOrigen) {
        String usuario = nombreUsuario == null || nombreUsuario.isBlank() ? USUARIO_ANONIMO : nombreUsuario.strip();
        repositorio.insertar(LocalDateTime.now(clock), recortar(usuario, MAX_USUARIO), accion.name(),
                accion == AccionAuditoria.ACCESO_DENEGADO ? "RUTA" : "SESION", null, resultado,
                recortar(detalle, MAX_DETALLE), recortar(ipOrigen, 45));
    }

    private static String usuarioActual() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken
                || autenticacion.getName() == null) {
            return USUARIO_SISTEMA;
        }
        return recortar(autenticacion.getName(), MAX_USUARIO);
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
