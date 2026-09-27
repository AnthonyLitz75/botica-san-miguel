package com.farmacia.service;

import com.farmacia.model.OrdenCompra;
import com.farmacia.model.DetalleOrdenCompra;
import com.farmacia.repository.OrdenCompraRepositorio;
import com.farmacia.repository.DetalleOrdenCompraRepositorio;
import org.springframework.stereotype.Service;
import com.farmacia.repository.UsuarioRepositorio;
import com.farmacia.repository.ProveedorRepositorio;
import com.farmacia.repository.MedicamentoRepositorio;
import org.springframework.security.access.AccessDeniedException;
import com.farmacia.dto.DetalleOrdenCompraFormulario;
import java.math.BigDecimal;
import java.util.HashSet;
import com.farmacia.dto.OrdenCompraFormulario;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import com.farmacia.model.DetalleOrdenCompraVista;

import java.util.List;
import java.util.Optional;

@Service
public class OrdenCompraServicio {

    private final OrdenCompraRepositorio ordenRepositorio;
    private final DetalleOrdenCompraRepositorio detalleRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final ProveedorRepositorio proveedorRepositorio;
    private final MedicamentoRepositorio medicamentoRepositorio;

    public OrdenCompraServicio(
            OrdenCompraRepositorio ordenRepositorio,
            DetalleOrdenCompraRepositorio detalleRepositorio,
            UsuarioRepositorio usuarioRepositorio,
            ProveedorRepositorio proveedorRepositorio,
            MedicamentoRepositorio medicamentoRepositorio) {

        this.ordenRepositorio = ordenRepositorio;
        this.detalleRepositorio = detalleRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.proveedorRepositorio = proveedorRepositorio;
        this.medicamentoRepositorio = medicamentoRepositorio;
    }

    public List<OrdenCompra> listar() {
        return ordenRepositorio.listar();
    }

    public Optional<OrdenCompra> buscarPorId(Integer idOrden) {
        return ordenRepositorio.buscarPorId(idOrden);
    }

    public List<DetalleOrdenCompra> listarDetalles(Integer idOrden) {
        return detalleRepositorio.listarPorOrden(idOrden);
    }

    private Integer validarUsuarioYProveedor(
            String nombreUsuario,
            Integer idProveedor) {

        var usuario = usuarioRepositorio
                .buscarPorNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));

        boolean tienePermiso = usuario.rol().equals("ADMINISTRADOR")
                || usuario.rol().equals("COMPRAS");

        if (!usuario.activo() || !tienePermiso) {
            throw new AccessDeniedException("Usuario no autorizado");
        }

        if (idProveedor == null || idProveedor <= 0) {
            throw new IllegalArgumentException("Selecciona un proveedor");
        }

        var proveedor = proveedorRepositorio.buscarPorId(idProveedor)
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado"));

        if (!proveedor.activo()) {
            throw new IllegalArgumentException(
                    "El proveedor seleccionado esta inactivo");
        }

        return usuario.idUsuario();
    }

    private BigDecimal validarDetallesYCalcularTotal(
            List<DetalleOrdenCompraFormulario> detalles) {

        if (detalles == null || detalles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Agrega al menos un medicamento");
        }

        var medicamentosIncluidos = new HashSet<Integer>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal limite = new BigDecimal("9999999999.99");

        for (var detalle : detalles) {
            if (detalle == null || detalle.idMedicamento() == null
                    || detalle.idMedicamento() <= 0) {
                throw new IllegalArgumentException(
                        "Selecciona un medicamento valido");
            }

            if (!medicamentosIncluidos.add(detalle.idMedicamento())) {
                throw new IllegalArgumentException(
                        "No puedes repetir un medicamento en la orden");
            }

            var medicamento = medicamentoRepositorio
                    .buscarPorId(detalle.idMedicamento())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Medicamento no encontrado"));

            if (!medicamento.activo()) {
                throw new IllegalArgumentException(
                        "La orden contiene un medicamento inactivo");
            }

            if (detalle.cantidadSolicitada() == null
                    || detalle.cantidadSolicitada() <= 0) {
                throw new IllegalArgumentException(
                        "La cantidad debe ser mayor que cero");
            }

            BigDecimal costo = detalle.costoUnitario();
            if (costo == null || costo.signum() < 0
                    || costo.compareTo(limite) > 0
                    || costo.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException(
                        "El costo unitario no es valido");
            }

            total = total.add(costo.multiply(
                    BigDecimal.valueOf(detalle.cantidadSolicitada())));

            if (total.compareTo(limite) > 0) {
                throw new IllegalArgumentException(
                        "El total supera el importe permitido");
            }
        }

        return total.setScale(2);
    }

    @Transactional
    public Integer guardar(
            String nombreUsuario,
            OrdenCompraFormulario formulario) {

        if (formulario == null) {
            throw new IllegalArgumentException(
                    "Los datos de la orden son obligatorios");
        }

        String observaciones = formulario.observaciones();

        if (observaciones != null && observaciones.length() > 500) {
            throw new IllegalArgumentException(
                    "Las observaciones admiten hasta 500 caracteres");
        }

        observaciones = observaciones == null || observaciones.isBlank()
                ? null
                : observaciones.strip();

        Integer idUsuario = validarUsuarioYProveedor(
                nombreUsuario, formulario.idProveedor());

        BigDecimal total = validarDetallesYCalcularTotal(
                formulario.detalles());

        String numeroOrden = "OC-" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 27);

        Integer idOrden = ordenRepositorio.guardar(
                numeroOrden,
                idUsuario,
                formulario.idProveedor(),
                total,
                observaciones);

        for (var detalle : formulario.detalles()) {
            detalleRepositorio.guardar(
                    idOrden,
                    detalle.idMedicamento(),
                    detalle.cantidadSolicitada(),
                    detalle.costoUnitario());
        }

        return idOrden;
    }

    public List<DetalleOrdenCompraVista> listarDetallesVista(
            Integer idOrden) {
        return detalleRepositorio.listarVistaPorOrden(idOrden);
    }
}