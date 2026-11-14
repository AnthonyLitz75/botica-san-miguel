package com.farmacia.service;

import com.farmacia.dto.UsuarioClaveFormulario;
import com.farmacia.dto.UsuarioFormulario;
import com.farmacia.model.Usuario;
import com.farmacia.repository.UsuarioRepositorio;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioServicio {

    private static final Set<String> ROLES_VALIDOS = Set.of(
            "ADMINISTRADOR",
            "VENDEDOR",
            "ALMACENERO",
            "COMPRAS");

    private final UsuarioRepositorio repositorio;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaServicio auditoria;
    private final SessionRegistry sessionRegistry;

    public UsuarioServicio(
            UsuarioRepositorio repositorio,
            PasswordEncoder passwordEncoder,
            AuditoriaServicio auditoria) {
        this(repositorio, passwordEncoder, auditoria, null);
    }

    @Autowired
    public UsuarioServicio(
            UsuarioRepositorio repositorio,
            PasswordEncoder passwordEncoder,
            AuditoriaServicio auditoria,
            @Autowired(required = false) SessionRegistry sessionRegistry) {
        this.repositorio = repositorio;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.sessionRegistry = sessionRegistry;
    }

    public List<Usuario> listar(String usuarioAutenticado) {
        validarAdministrador(usuarioAutenticado);
        return repositorio.listar();
    }

    public Optional<Usuario> buscarPorId(Integer idUsuario, String usuarioAutenticado) {
        validarAdministrador(usuarioAutenticado);
        return repositorio.buscarPorId(idUsuario);
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer crear(String usuarioAutenticado, UsuarioFormulario formulario) {
        validarAdministrador(usuarioAutenticado);
        validarNombre(formulario.nombre());
        validarNombreUsuario(formulario.nombreUsuario());
        validarRol(formulario.rol());
        validarClave(formulario.clave());

        if (repositorio.buscarPorNombreUsuario(formulario.nombreUsuario()).isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un usuario con el nombre de usuario: " + formulario.nombreUsuario());
        }

        String claveHash = passwordEncoder.encode(formulario.clave());
        Integer idGenerado = repositorio.insertar(
                formulario.nombre(),
                formulario.nombreUsuario(),
                claveHash,
                formulario.rol());

        auditoria.registrarCambio(
                AccionAuditoria.CREAR,
                "USUARIO",
                idGenerado,
                "usuario: " + formulario.nombreUsuario() + "; rol: " + formulario.rol());

        return idGenerado;
    }

    @Transactional(rollbackFor = Exception.class)
    public void actualizar(String usuarioAutenticado, Integer idUsuario, UsuarioFormulario formulario) {
        validarAdministrador(usuarioAutenticado);
        validarNombre(formulario.nombre());
        validarRol(formulario.rol());

        Usuario existente = repositorio.buscarPorId(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + idUsuario));

        boolean cambiaRol = !existente.rol().equals(formulario.rol());
        if (existente.rol().equals("ADMINISTRADOR") && cambiaRol) {
            if (existente.activo() && repositorio.contarAdministradoresActivos() <= 1) {
                throw new IllegalStateException("No se puede cambiar el rol del único administrador activo");
            }
        }

        repositorio.actualizar(idUsuario, formulario.nombre(), formulario.rol());

        if (cambiaRol) {
            auditoria.registrarCambio(
                    AccionAuditoria.CAMBIAR_ROL,
                    "USUARIO",
                    idUsuario,
                    "rol anterior: " + existente.rol() + " -> nuevo: " + formulario.rol());
            expirarSesionesDeUsuario(existente.nombreUsuario());
        } else {
            auditoria.registrarCambio(
                    AccionAuditoria.EDITAR,
                    "USUARIO",
                    idUsuario,
                    "nombre modificado: " + formulario.nombre());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cambiarClave(String usuarioAutenticado, Integer idUsuario, UsuarioClaveFormulario formulario) {
        validarAdministrador(usuarioAutenticado);
        validarClave(formulario.nuevaClave());

        Usuario existente = repositorio.buscarPorId(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + idUsuario));

        String nuevaClaveHash = passwordEncoder.encode(formulario.nuevaClave());
        repositorio.actualizarClave(idUsuario, nuevaClaveHash);

        auditoria.registrarCambio(
                AccionAuditoria.CAMBIAR_CLAVE,
                "USUARIO",
                idUsuario,
                "contraseña restablecida para usuario: " + existente.nombreUsuario());

        expirarSesionesDeUsuario(existente.nombreUsuario());
    }

    @Transactional(rollbackFor = Exception.class)
    public void desactivar(String usuarioAutenticado, Integer idUsuario) {
        validarAdministrador(usuarioAutenticado);

        Usuario existente = repositorio.buscarPorId(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + idUsuario));

        if (existente.nombreUsuario().equalsIgnoreCase(usuarioAutenticado)) {
            throw new IllegalArgumentException("No puedes desactivar tu propia cuenta");
        }

        if (existente.rol().equals("ADMINISTRADOR") && repositorio.contarAdministradoresActivos() <= 1) {
            throw new IllegalStateException("No se puede desactivar al único administrador activo");
        }

        repositorio.cambiarEstado(idUsuario, false);

        auditoria.registrarCambio(
                AccionAuditoria.DESACTIVAR,
                "USUARIO",
                idUsuario,
                "cuenta desactivada para usuario: " + existente.nombreUsuario());

        expirarSesionesDeUsuario(existente.nombreUsuario());
    }

    @Transactional(rollbackFor = Exception.class)
    public void activar(String usuarioAutenticado, Integer idUsuario) {
        validarAdministrador(usuarioAutenticado);

        Usuario existente = repositorio.buscarPorId(idUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + idUsuario));

        repositorio.cambiarEstado(idUsuario, true);

        auditoria.registrarCambio(
                AccionAuditoria.ACTIVAR,
                "USUARIO",
                idUsuario,
                "cuenta activada para usuario: " + existente.nombreUsuario());
    }

    private void expirarSesionesDeUsuario(String nombreUsuario) {
        if (sessionRegistry == null) {
            return;
        }
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                if (userDetails.getUsername().equalsIgnoreCase(nombreUsuario)) {
                    for (var sesion : sessionRegistry.getAllSessions(principal, false)) {
                        sesion.expireNow();
                    }
                }
            } else if (principal.toString().equalsIgnoreCase(nombreUsuario)) {
                for (var sesion : sessionRegistry.getAllSessions(principal, false)) {
                    sesion.expireNow();
                }
            }
        }
    }

    private void validarAdministrador(String usuarioAutenticado) {
        if (usuarioAutenticado == null || usuarioAutenticado.isBlank()) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        Usuario usuario = repositorio.buscarPorNombreUsuario(usuarioAutenticado)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        if (!usuario.activo() || !usuario.rol().equals("ADMINISTRADOR")) {
            throw new AccessDeniedException("Solo el administrador puede gestionar usuarios");
        }
    }

    private void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.length() > 100) {
            throw new IllegalArgumentException("El nombre es obligatorio y debe tener como máximo 100 caracteres");
        }
    }

    private void validarNombreUsuario(String nombreUsuario) {
        if (nombreUsuario == null || !nombreUsuario.matches("^[a-zA-Z0-9._-]{4,50}$")) {
            throw new IllegalArgumentException(
                    "El nombre de usuario debe tener entre 4 y 50 caracteres (alfanuméricos, '.', '_' o '-')");
        }
    }

    private void validarRol(String rol) {
        if (rol == null || !ROLES_VALIDOS.contains(rol)) {
            throw new IllegalArgumentException("El rol debe ser ADMINISTRADOR, VENDEDOR, ALMACENERO o COMPRAS");
        }
    }

    private void validarClave(String clave) {
        if (clave == null || clave.length() < 10
                || clave.chars().noneMatch(Character::isLetter)
                || clave.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos 10 caracteres y contener al menos una letra y un número");
        }
    }
}
