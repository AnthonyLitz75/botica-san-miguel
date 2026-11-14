package com.farmacia.config;

import com.farmacia.service.AccionAuditoria;
import com.farmacia.service.AuditoriaServicio;
import jakarta.servlet.http.HttpServletRequest;
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
 */
@Component
public class AuditoriaSeguridadOyente {
    private final AuditoriaServicio auditoria;

    public AuditoriaSeguridadOyente(AuditoriaServicio auditoria) {
        this.auditoria = auditoria;
    }

    /**
     * Inicio de sesión por formulario. Se usa el evento "interactivo" para no registrar cada petición
     * de la API (HTTP Basic sin estado autentica en cada llamada).
     */
    @EventListener
    public void inicioDeSesion(InteractiveAuthenticationSuccessEvent evento) {
        auditoria.registrarAcceso(evento.getAuthentication().getName(), AccionAuditoria.INICIO_SESION,
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
