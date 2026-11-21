package com.farmacia.config;

import com.farmacia.service.AccionAuditoria;
import com.farmacia.service.AuditoriaServicio;
import com.farmacia.service.UsuarioServicio;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Patrón Observer: escucha los eventos que publica Spring Security y los registra en la bitácora
 * (spec 014, US-2) sin acoplar la seguridad a la auditoría. Solo usa el nombre de usuario; nunca lee
 * las credenciales del evento.
 * Actualiza también el último acceso del usuario de forma resiliente (spec 020, US-1 / RNF-02).
 */
@Component
public class AuditoriaSeguridadOyente {
    private static final Logger log = LoggerFactory.getLogger(AuditoriaSeguridadOyente.class);

    private final AuditoriaServicio auditoria;
    private final UsuarioServicio usuarioServicio;

    @Autowired
    public AuditoriaSeguridadOyente(AuditoriaServicio auditoria, UsuarioServicio usuarioServicio) {
        this.auditoria = auditoria;
        this.usuarioServicio = usuarioServicio;
    }

    public AuditoriaSeguridadOyente(AuditoriaServicio auditoria) {
        this(auditoria, null);
    }

    /**
     * Inicio de sesión por formulario. Se usa el evento "interactivo" para no registrar cada petición
     * de la API (HTTP Basic sin estado autentica en cada llamada).
     */
    @EventListener
    public void inicioDeSesion(InteractiveAuthenticationSuccessEvent evento) {
        String nombreUsuario = evento.getAuthentication().getName();
        if (usuarioServicio != null) {
            try {
                usuarioServicio.registrarAcceso(nombreUsuario);
            } catch (Exception excepcion) {
                // RNF-02: una falla al actualizar el último acceso no interrumpe el login ni la auditoría
                log.warn("No se pudo actualizar el último acceso para el usuario {}: {}",
                        nombreUsuario, excepcion.getMessage());
            }
        }
        auditoria.registrarAcceso(nombreUsuario, AccionAuditoria.INICIO_SESION,
                "EXITO", null, ip(evento.getAuthentication()));
    }

    /** Intento fallido (formulario o API): se guarda el nombre escrito y el tipo de fallo, nunca la clave. */
    @EventListener
    public void inicioDeSesionFallido(AbstractAuthenticationFailureEvent evento) {
        auditoria.registrarAcceso(evento.getAuthentication().getName(), AccionAuditoria.INICIO_SESION_FALLIDO,
                "FALLO", evento.getException().getClass().getSimpleName(), ip(evento.getAuthentication()));
    }

    @EventListener
    public void cierreDeSesion(LogoutSuccessEvent evento) {
        auditoria.registrarAcceso(evento.getAuthentication().getName(), AccionAuditoria.CIERRE_SESION,
                "EXITO", null, ip(evento.getAuthentication()));
    }

    /**
     * Acceso denegado por las reglas de rutas. Las visitas sin sesión no se registran: no son un intento
     * sin permiso, solo se redirigen al login.
     */
    @EventListener
    public void accesoDenegado(AuthorizationDeniedEvent<?> evento) {
        Authentication autenticacion = evento.getAuthentication().get();
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken) {
            return;
        }
        String detalle = null;
        String ip = ip(autenticacion);
        if (evento.getSource() instanceof HttpServletRequest peticion) {
            detalle = peticion.getMethod() + " " + peticion.getRequestURI();
            ip = ip == null ? peticion.getRemoteAddr() : ip;
        }
        auditoria.registrarAcceso(autenticacion.getName(), AccionAuditoria.ACCESO_DENEGADO, "DENEGADO", detalle, ip);
    }

    private static String ip(Authentication autenticacion) {
        if (autenticacion != null && autenticacion.getDetails() instanceof WebAuthenticationDetails detalles) {
            return detalles.getRemoteAddress();
        }
        return null;
    }
}
