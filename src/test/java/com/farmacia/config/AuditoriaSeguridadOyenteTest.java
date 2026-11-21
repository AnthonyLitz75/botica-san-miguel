package com.farmacia.config;

import com.farmacia.service.AccionAuditoria;
import com.farmacia.service.AuditoriaServicio;
import com.farmacia.service.UsuarioServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 020: RNF-02 resiliencia ante excepciones en registrarAcceso. */
@ExtendWith(MockitoExtension.class)
class AuditoriaSeguridadOyenteTest {

    @Mock
    private AuditoriaServicio auditoria;

    @Mock
    private UsuarioServicio usuarioServicio;

    @Mock
    private Authentication authentication;

    @Test
    @DisplayName("RNF-02: si registrarAcceso lanza excepcion, no se propaga y la auditoria continua")
    void resilienciaSiRegistrarAccesoLanzaExcepcion() {
        when(authentication.getName()).thenReturn("juan_perez");
        InteractiveAuthenticationSuccessEvent evento = new InteractiveAuthenticationSuccessEvent(authentication, getClass());

        doThrow(new RuntimeException("Simulated DB connection error")).when(usuarioServicio).registrarAcceso("juan_perez");

        AuditoriaSeguridadOyente oyente = new AuditoriaSeguridadOyente(auditoria, usuarioServicio);

        assertDoesNotThrow(() -> oyente.inicioDeSesion(evento));

        verify(auditoria).registrarAcceso(eq("juan_perez"), eq(AccionAuditoria.INICIO_SESION), eq("EXITO"), isNull(), any());
    }
}
