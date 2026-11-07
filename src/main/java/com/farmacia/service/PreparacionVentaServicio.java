package com.farmacia.service;

import com.farmacia.dto.PreparacionVentaCambioFormulario;
import com.farmacia.estructuras.ListaEnlazada;
import com.farmacia.estructuras.PilaDeshacer;
import com.farmacia.model.LineaPreparacionVentaVista;
import com.farmacia.model.PreparacionVentaVista;
import com.farmacia.repository.MedicamentoRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@SessionScope
public class PreparacionVentaServicio {
    private final MedicamentoRepositorio medicamentoRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final LotePrioridadServicio lotePrioridadServicio;
    private final ListaEnlazada<LineaPreparacionVentaVista> detalles = new ListaEnlazada<>();
    private final PilaDeshacer<List<LineaPreparacionVentaVista>> historial = new PilaDeshacer<>();
    private String usuarioPropietario;

    public PreparacionVentaServicio(MedicamentoRepositorio medicamentoRepositorio,
            UsuarioRepositorio usuarioRepositorio, LotePrioridadServicio lotePrioridadServicio) {
        this.medicamentoRepositorio = medicamentoRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.lotePrioridadServicio = lotePrioridadServicio;
    }

    public synchronized PreparacionVentaVista obtener(String nombreUsuario) {
        validarUsuario(nombreUsuario);
        return crearVista();
    }

    public synchronized PreparacionVentaVista agregar(String nombreUsuario,
            PreparacionVentaCambioFormulario formulario) {
        validarUsuario(nombreUsuario);
        if (formulario == null || formulario.idMedicamento() == null || formulario.idMedicamento() <= 0
                || formulario.cantidad() == null || formulario.cantidad() <= 0) {
            throw new IllegalArgumentException("Selecciona un medicamento y una cantidad mayor que cero");
        }
        var medicamento = medicamentoRepositorio.buscarPorId(formulario.idMedicamento())
                .filter(m -> m.activo())
                .orElseThrow(() -> new IllegalArgumentException("Medicamento no encontrado o inactivo"));
        var anterior = detalles.buscar(linea -> linea.idMedicamento().equals(formulario.idMedicamento()))
                .orElse(null);
        int cantidad;
        try {
            cantidad = Math.addExact(formulario.cantidad(), anterior == null ? 0 : anterior.cantidad());
        } catch (ArithmeticException excepcion) {
            throw new IllegalArgumentException("La cantidad supera el límite permitido", excepcion);
        }
        var lotes = lotePrioridadServicio.planificarSalida(formulario.idMedicamento(), cantidad);
        var linea = new LineaPreparacionVentaVista(medicamento.idMedicamento(), medicamento.codigo(),
                medicamento.nombre(), cantidad, medicamento.precioVenta(),
                medicamento.precioVenta().multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP),
                lotes);
        guardarInstantanea();
        if (anterior == null) {
            detalles.agregar(linea);
        } else {
            detalles.reemplazar(actual -> actual.idMedicamento().equals(medicamento.idMedicamento()), linea);
        }
        return crearVista();
    }

    public synchronized PreparacionVentaVista retirar(String nombreUsuario, Integer idMedicamento) {
        validarUsuario(nombreUsuario);
        if (idMedicamento == null
                || detalles.buscar(linea -> linea.idMedicamento().equals(idMedicamento)).isEmpty()) {
            throw new IllegalArgumentException("El medicamento no está en la preparación");
        }
        guardarInstantanea();
        detalles.eliminar(linea -> linea.idMedicamento().equals(idMedicamento));
        return crearVista();
    }

    public synchronized PreparacionVentaVista deshacer(String nombreUsuario) {
        validarUsuario(nombreUsuario);
        historial.desapilar().ifPresent(instantanea -> {
            detalles.limpiar();
            instantanea.forEach(detalles::agregar);
        });
        return crearVista();
    }

    private void validarUsuario(String nombreUsuario) {
        var usuario = usuarioRepositorio.buscarPorNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        if (!usuario.activo() || !(usuario.rol().equals("ADMINISTRADOR") || usuario.rol().equals("VENDEDOR"))) {
            throw new AccessDeniedException("Usuario no autorizado");
        }
        if (usuarioPropietario != null && !usuarioPropietario.equals(nombreUsuario)) {
            detalles.limpiar();
            historial.limpiar();
        }
        usuarioPropietario = nombreUsuario;
    }

    private void guardarInstantanea() {
        historial.apilar(detalles.comoLista());
    }

    private PreparacionVentaVista crearVista() {
        var lineas = detalles.comoLista();
        BigDecimal total = lineas.stream().map(LineaPreparacionVentaVista::importe)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new PreparacionVentaVista(lineas, total, !historial.estaVacia());
    }
}
