package com.farmacia.service;

import com.farmacia.model.Medicamento;
import com.farmacia.repository.MedicamentoRepositorio;
import java.util.List;
import org.springframework.stereotype.Service;
import com.farmacia.dto.MedicamentoFormulario;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicamentoServicio {

    private static final String ENTIDAD = "MEDICAMENTO";
    private final MedicamentoRepositorio repositorio;
    private final AuditoriaServicio auditoria;

    public MedicamentoServicio(MedicamentoRepositorio repositorio, AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
    }

    public List<Medicamento> listarActivos() {
        return repositorio.listarActivos();
    }

    public List<Medicamento> buscarActivos(String texto) {
        String textoLimpio = texto == null ? "" : texto.strip();
        return repositorio.buscarActivos(textoLimpio);
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer guardar(MedicamentoFormulario formulario) {
        MedicamentoFormulario limpio = limpiarDatos(formulario);
        Integer id = repositorio.guardar(limpio);
        auditoria.registrarCambio(AccionAuditoria.CREAR, ENTIDAD, id,
                "codigo: " + limpio.codigo() + "; nombre: " + limpio.nombre()
                        + "; precio_venta: " + limpio.precioVenta());
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean actualizar(Integer id, MedicamentoFormulario formulario) {
        MedicamentoFormulario limpio = limpiarDatos(formulario);
        var anterior = repositorio.buscarPorId(id);
        int filasActualizadas = repositorio.actualizar(id, limpio);
        if (filasActualizadas == 1) {
            String detalle = anterior.map(a -> new DiferenciaCampos()
                    .campo("codigo", a.codigo(), limpio.codigo())
                    .campo("nombre", a.nombre(), limpio.nombre())
                    .campo("concentracion", a.concentracion(), limpio.concentracion())
                    .campo("presentacion", a.presentacion(), limpio.presentacion())
                    .campo("unidad_control", a.unidadControl(), limpio.unidadControl())
                    .campo("precio_venta", a.precioVenta(), limpio.precioVenta())
                    .campo("stock_minimo", a.stockMinimo(), limpio.stockMinimo())
                    .campo("id_categoria", a.idCategoria(), limpio.idCategoria())
                    .describir()).orElse(null);
            auditoria.registrarCambio(AccionAuditoria.EDITAR, ENTIDAD, id, detalle);
        }
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
                formulario.stockMinimo(),
                formulario.idCategoria());
    }

    public Optional<Medicamento> buscarPorId(Integer id) {
        return repositorio.buscarPorId(id);
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

    public List<Medicamento> buscarPorEstado(String texto, boolean activo) {
        String textoLimpio = texto == null ? "" : texto.strip();
        return repositorio.buscarPorEstado(textoLimpio, activo);
    }

    public List<Medicamento> buscarActivosPorCategorias(String texto, List<Integer> idsCategorias) {
        String textoLimpio = texto == null ? "" : texto.strip();
        return repositorio.buscarPorEstadoYCategorias(textoLimpio, true, idsCategorias);
    }
}
