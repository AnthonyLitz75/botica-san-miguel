package com.farmacia.service;

import com.farmacia.model.Proveedor;
import com.farmacia.repository.ProveedorRepositorio;
import org.springframework.stereotype.Service;
import com.farmacia.dto.ProveedorFormulario;
import java.util.Optional;

import java.util.List;

@Service
public class ProveedorServicio {

    private final ProveedorRepositorio repositorio;

    public ProveedorServicio(ProveedorRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public List<Proveedor> listarActivos() {
        return repositorio.listarActivos();
    }

    public void guardar(ProveedorFormulario formulario) {
        repositorio.guardar(limpiarDatos(formulario));
    }

    public Optional<Proveedor> buscarPorId(Integer id) {
        return repositorio.buscarPorId(id);
    }

    public boolean actualizar(Integer id, ProveedorFormulario formulario) {
        int filasActualizadas = repositorio.actualizar(
                id,
                limpiarDatos(formulario));

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

    public boolean desactivar(Integer id) {
        int filasActualizadas = repositorio.desactivar(id);
        return filasActualizadas == 1;
    }

    public boolean activar(Integer id) {
        int filasActualizadas = repositorio.activar(id);
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