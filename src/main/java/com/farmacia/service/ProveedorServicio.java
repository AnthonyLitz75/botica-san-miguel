package com.farmacia.service;

import com.farmacia.model.Proveedor;
import com.farmacia.repository.ProveedorRepositorio;
import org.springframework.stereotype.Service;
import com.farmacia.dto.ProveedorFormulario;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProveedorServicio {

    private static final String ENTIDAD = "PROVEEDOR";
    private final ProveedorRepositorio repositorio;
    private final AuditoriaServicio auditoria;

    public ProveedorServicio(ProveedorRepositorio repositorio, AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
    }

    public List<Proveedor> listarActivos() {
        return repositorio.listarActivos();
    }

    @Transactional(rollbackFor = Exception.class)
    public void guardar(ProveedorFormulario formulario) {
        ProveedorFormulario limpio = limpiarDatos(formulario);
        repositorio.guardar(limpio);
        // El repositorio no devuelve el id: el proveedor se identifica por su RUC (único).
        auditoria.registrarCambio(AccionAuditoria.CREAR, ENTIDAD, limpio.ruc(),
                "razon_social: " + limpio.razonSocial());
    }

    public Optional<Proveedor> buscarPorId(Integer id) {
        return repositorio.buscarPorId(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean actualizar(Integer id, ProveedorFormulario formulario) {
        ProveedorFormulario limpio = limpiarDatos(formulario);
        var anterior = repositorio.buscarPorId(id);
        int filasActualizadas = repositorio.actualizar(id, limpio);
        if (filasActualizadas == 1) {
            String detalle = anterior.map(a -> new DiferenciaCampos()
                    .campo("ruc", a.ruc(), limpio.ruc())
                    .campo("razon_social", a.razonSocial(), limpio.razonSocial())
                    .campo("telefono", a.telefono(), limpio.telefono())
                    .campo("correo", a.correo(), limpio.correo())
                    .campo("direccion", a.direccion(), limpio.direccion())
                    .describir()).orElse(null);
            auditoria.registrarCambio(AccionAuditoria.EDITAR, ENTIDAD, id, detalle);
        }
        return filasActualizadas == 1;
    }

    private ProveedorFormulario limpiarDatos(ProveedorFormulario formulario) {
        return new ProveedorFormulario(
                formulario.ruc().strip(),
                formulario.razonSocial().strip(),
                limpiarOpcional(formulario.telefono()),
                limpiarOpcional(formulario.correo()),
                limpiarOpcional(formulario.direccion()));
    }

    private String limpiarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        return valor.strip();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean desactivar(Integer id) {
        int filasActualizadas = repositorio.desactivar(id);
        if (filasActualizadas == 1) {
            auditoria.registrarCambio(AccionAuditoria.DESACTIVAR, ENTIDAD, id, null);
        }
        return filasActualizadas == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean activar(Integer id) {
        int filasActualizadas = repositorio.activar(id);
        if (filasActualizadas == 1) {
            auditoria.registrarCambio(AccionAuditoria.ACTIVAR, ENTIDAD, id, null);
        }
        return filasActualizadas == 1;
    }

    public List<Proveedor> listarPorEstado(boolean activo) {
        return repositorio.listarPorEstado(activo);
    }

    public List<Proveedor> buscarPorEstado(String texto, boolean activo) {
        String textoLimpio = texto == null ? "" : texto.strip();
        return repositorio.buscarPorEstado(textoLimpio, activo);
    }
}