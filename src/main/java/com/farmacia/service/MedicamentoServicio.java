package com.farmacia.service;

import com.farmacia.model.Medicamento;
import com.farmacia.repository.MedicamentoRepositorio;
import java.util.List;
import org.springframework.stereotype.Service;
import com.farmacia.dto.MedicamentoFormulario;
import java.util.Optional;

@Service
public class MedicamentoServicio {

    private final MedicamentoRepositorio repositorio;

    public MedicamentoServicio(MedicamentoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public List<Medicamento> listarActivos() {
        return repositorio.listarActivos();
    }

    public List<Medicamento> buscarActivos(String texto) {
        String textoLimpio = texto == null ? "" : texto.strip();
        return repositorio.buscarActivos(textoLimpio);
    }

    public Integer guardar(MedicamentoFormulario formulario) {
        return repositorio.guardar(limpiarDatos(formulario));
    }

    public boolean actualizar(Integer id, MedicamentoFormulario formulario) {
        int filasActualizadas = repositorio.actualizar(
                id,
                limpiarDatos(formulario));

        return filasActualizadas == 1;
    }

    private MedicamentoFormulario limpiarDatos(
            MedicamentoFormulario formulario) {
        return new MedicamentoFormulario(
                formulario.codigo().strip(),
                formulario.nombre().strip(),
                formulario.concentracion() == null
                        ? null
                        : formulario.concentracion().strip(),
                formulario.presentacion().strip(),
                formulario.unidadControl().strip(),
                formulario.precioVenta(),
                formulario.stockMinimo());
    }

    public Optional<Medicamento> buscarPorId(Integer id) {
        return repositorio.buscarPorId(id);
    }

    public boolean desactivar(Integer id) {
        int filasActualizadas = repositorio.desactivar(id);
        return filasActualizadas == 1;
    }

    public boolean activar(Integer id) {
        int filasActualizadas = repositorio.activar(id);
        return filasActualizadas == 1;
    }

    public List<Medicamento> buscarPorEstado(String texto, boolean activo) {
        String textoLimpio = texto == null ? "" : texto.strip();
        return repositorio.buscarPorEstado(textoLimpio, activo);
    }

    
}
