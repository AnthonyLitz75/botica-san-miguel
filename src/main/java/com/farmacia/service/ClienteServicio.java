package com.farmacia.service;

import com.farmacia.dto.ClienteFormulario;
import com.farmacia.model.Cliente;
import com.farmacia.repository.ClienteRepositorio;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteServicio {
    private static final String ENTIDAD = "CLIENTE";
    private final ClienteRepositorio repositorio;
    private final AuditoriaServicio auditoria;

    public ClienteServicio(ClienteRepositorio repositorio, AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
    }

    public List<Cliente> listar(String texto) {
        return repositorio.listar(texto == null ? "" : texto.strip());
    }

    public Optional<Cliente> buscarPorId(Integer id) {
        return repositorio.buscarPorId(id);
    }

    public Optional<Cliente> buscarPorDocumento(String tipoDocumento, String numeroDocumento) {
        ClienteFormulario limpio = limpiarDatos(new ClienteFormulario(
                tipoDocumento, numeroDocumento, "Consulta", null, null));
        validarDocumento(limpio);
        return repositorio.buscarPorDocumento(limpio.tipoDocumento(), limpio.numeroDocumento());
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer guardar(ClienteFormulario formulario) {
        ClienteFormulario limpio = limpiarDatos(formulario);
        validarDocumento(limpio);
        if (repositorio.existeDocumento(limpio.tipoDocumento(), limpio.numeroDocumento(), null)) {
            throw new DuplicateKeyException("Ya existe un cliente con este documento");
        }
        Integer id = repositorio.guardar(limpio);
        // Datos personales: la bitácora no guarda documento, nombre ni contacto del cliente.
        auditoria.registrarCambio(AccionAuditoria.CREAR, ENTIDAD, id, "tipo_documento: " + limpio.tipoDocumento());
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean actualizar(Integer id, ClienteFormulario formulario) {
        var anterior = repositorio.buscarPorId(id);
        if (anterior.isEmpty()) {
            return false;
        }
        ClienteFormulario limpio = limpiarDatos(formulario);
        validarDocumento(limpio);
        if (repositorio.existeDocumento(limpio.tipoDocumento(), limpio.numeroDocumento(), id)) {
            throw new DuplicateKeyException("Ya existe un cliente con este documento");
        }
        boolean actualizado = repositorio.actualizar(id, limpio) == 1;
        if (actualizado) {
            var a = anterior.get();
            // Datos personales: solo se registran los nombres de los campos modificados.
            String detalle = new DiferenciaCampos()
                    .campoSinValores("tipo_documento", a.tipoDocumento(), limpio.tipoDocumento())
                    .campoSinValores("numero_documento", a.numeroDocumento(), limpio.numeroDocumento())
                    .campoSinValores("nombre_completo", a.nombreCompleto(), limpio.nombreCompleto())
                    .campoSinValores("telefono", a.telefono(), limpio.telefono())
                    .campoSinValores("correo", a.correo(), limpio.correo())
                    .describir();
            auditoria.registrarCambio(AccionAuditoria.EDITAR, ENTIDAD, id, "Campos modificados: " + detalle);
        }
        return actualizado;
    }

    private ClienteFormulario limpiarDatos(ClienteFormulario formulario) {
        if (formulario == null) {
            throw new IllegalArgumentException("Los datos del cliente son obligatorios");
        }
        return new ClienteFormulario(limpiarObligatorio(formulario.tipoDocumento()).toUpperCase(),
                limpiarObligatorio(formulario.numeroDocumento()),
                limpiarObligatorio(formulario.nombreCompleto()),
                limpiarOpcional(formulario.telefono()), limpiarOpcional(formulario.correo()));
    }

    private String limpiarObligatorio(String valor) {
        return valor == null ? "" : valor.strip();
    }

    private String limpiarOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private void validarDocumento(ClienteFormulario formulario) {
        boolean documentoValido = switch (formulario.tipoDocumento()) {
            case "DNI" -> formulario.numeroDocumento().matches("[0-9]{8}");
            case "RUC" -> formulario.numeroDocumento().matches("[0-9]{11}");
            default -> false;
        };
        if (!documentoValido) {
            throw new IllegalArgumentException("El DNI debe tener 8 dígitos y el RUC 11 dígitos");
        }
    }
}
