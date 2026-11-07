package com.farmacia.service;

import com.farmacia.dto.ClienteFormulario;
import com.farmacia.model.Cliente;
import com.farmacia.repository.ClienteRepositorio;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ClienteServicio {
    private final ClienteRepositorio repositorio;

    public ClienteServicio(ClienteRepositorio repositorio) {
        this.repositorio = repositorio;
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

    public Integer guardar(ClienteFormulario formulario) {
        ClienteFormulario limpio = limpiarDatos(formulario);
        validarDocumento(limpio);
        if (repositorio.existeDocumento(limpio.tipoDocumento(), limpio.numeroDocumento(), null)) {
            throw new DuplicateKeyException("Ya existe un cliente con este documento");
        }
        return repositorio.guardar(limpio);
    }

    public boolean actualizar(Integer id, ClienteFormulario formulario) {
        if (repositorio.buscarPorId(id).isEmpty()) {
            return false;
        }
        ClienteFormulario limpio = limpiarDatos(formulario);
        validarDocumento(limpio);
        if (repositorio.existeDocumento(limpio.tipoDocumento(), limpio.numeroDocumento(), id)) {
            throw new DuplicateKeyException("Ya existe un cliente con este documento");
        }
        return repositorio.actualizar(id, limpio) == 1;
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
