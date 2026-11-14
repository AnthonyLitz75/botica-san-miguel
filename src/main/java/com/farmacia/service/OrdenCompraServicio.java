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
import com.farmacia.model.LineaRecepcionVista;
import com.farmacia.dto.RecepcionOrdenFormulario;
import com.farmacia.dto.DetalleRecepcionFormulario;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.MovimientoInventarioRepositorio;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import java.util.List;
import java.util.Optional;

@Service
public class OrdenCompraServicio {

        private final OrdenCompraRepositorio ordenRepositorio;
        private final DetalleOrdenCompraRepositorio detalleRepositorio;
        private final UsuarioRepositorio usuarioRepositorio;
        private final ProveedorRepositorio proveedorRepositorio;
        private final MedicamentoRepositorio medicamentoRepositorio;
        private final LoteRepositorio loteRepositorio;
        private final MovimientoInventarioRepositorio movimientoRepositorio;
        private final Clock clock;
        private final AuditoriaServicio auditoria;

        public OrdenCompraServicio(
                        OrdenCompraRepositorio ordenRepositorio,
                        DetalleOrdenCompraRepositorio detalleRepositorio,
                        UsuarioRepositorio usuarioRepositorio,
                        ProveedorRepositorio proveedorRepositorio,
                        MedicamentoRepositorio medicamentoRepositorio,
                        LoteRepositorio loteRepositorio,
                        MovimientoInventarioRepositorio movimientoRepositorio,
                        Clock clock,
                        AuditoriaServicio auditoria) {

                this.ordenRepositorio = ordenRepositorio;
                this.detalleRepositorio = detalleRepositorio;
                this.usuarioRepositorio = usuarioRepositorio;
                this.proveedorRepositorio = proveedorRepositorio;
                this.medicamentoRepositorio = medicamentoRepositorio;
                this.loteRepositorio = loteRepositorio;
                this.movimientoRepositorio = movimientoRepositorio;
                this.clock = clock;
                this.auditoria = auditoria;
        }

        /** Primer vencimiento aceptado en una recepción: el lote debe vencer después de hoy. */
        public LocalDate fechaMinimaVencimientoRecepcion() {
                return LocalDate.now(clock).plusDays(1);
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

        @Transactional(rollbackFor = Exception.class)
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

                auditoria.registrarCambio(AccionAuditoria.CREAR, "ORDEN_COMPRA", idOrden,
                                "numero: " + numeroOrden + "; proveedor: " + formulario.idProveedor()
                                                + "; lineas: " + formulario.detalles().size()
                                                + "; total_estimado: " + total);
                return idOrden;
        }

        public List<LineaRecepcionVista> listarLineasRecepcion(Integer idOrden) {
                return detalleRepositorio.listarLineasRecepcion(idOrden);
        }

        @Transactional(rollbackFor = Exception.class)
        public void registrarRecepcion(Integer idOrden, String nombreUsuario,
                        RecepcionOrdenFormulario formulario) {
                if (formulario == null || formulario.detalles() == null || formulario.detalles().isEmpty())
                        throw new IllegalArgumentException("Agrega al menos un producto recibido");
                var usuario = validarUsuario(nombreUsuario, "ALMACENERO");
                var orden = ordenRepositorio.buscarPorIdParaActualizar(idOrden)
                        .orElseThrow(() -> new IllegalArgumentException("Orden de compra no encontrada"));
                if (!orden.estado().equals("PENDIENTE") && !orden.estado().equals("PARCIALMENTE_RECIBIDA"))
                        throw new IllegalArgumentException("La orden no admite más recepciones");

                Map<Integer, LineaRecepcionVista> lineas = detalleRepositorio.listarLineasRecepcion(idOrden)
                        .stream().collect(Collectors.toMap(LineaRecepcionVista::idDetalleOrden, Function.identity()));
                var ids = new HashSet<Integer>();
                int pendienteTotal = 0;
                for (var linea : lineas.values()) {
                        if (linea.cantidadRecibida() < 0 || linea.cantidadPendiente() < 0)
                                throw new IllegalStateException("La orden tiene cantidades recibidas inconsistentes");
                        pendienteTotal = Math.addExact(pendienteTotal, linea.cantidadPendiente());
                }
                for (DetalleRecepcionFormulario detalle : formulario.detalles()) {
                        if (detalle == null || detalle.idDetalleOrden() == null || detalle.cantidad() == null
                                || detalle.cantidad() <= 0 || detalle.numeroLote() == null
                                || detalle.numeroLote().isBlank() || detalle.numeroLote().length() > 50
                                || detalle.fechaVencimiento() == null
                                || !detalle.fechaVencimiento().isAfter(LocalDate.now(clock)))
                                throw new IllegalArgumentException("Revisa lote, vencimiento y cantidad recibida");
                        if (!ids.add(detalle.idDetalleOrden()))
                                throw new IllegalArgumentException("No repitas una línea de la orden");
                        var linea = lineas.get(detalle.idDetalleOrden());
                        if (linea == null) throw new IllegalArgumentException("La línea no pertenece a esta orden");
                        if (detalle.cantidad() > linea.cantidadPendiente())
                                throw new IllegalArgumentException("La cantidad supera lo pendiente para " + linea.nombre());
                        pendienteTotal -= detalle.cantidad();
                }
                String motivo = formulario.motivo() == null || formulario.motivo().isBlank()
                        ? "Recepción de compra" : formulario.motivo().strip();
                if (motivo.length() > 250) throw new IllegalArgumentException("El motivo admite hasta 250 caracteres");
                int idMovimiento = movimientoRepositorio.registrarEntrada(usuario.idUsuario(), idOrden, motivo);
                for (var detalle : formulario.detalles()) {
                        var linea = lineas.get(detalle.idDetalleOrden());
                        int idLote = loteRepositorio.registrarOIncrementar(linea.idMedicamento(),
                                detalle.numeroLote().strip(), detalle.fechaVencimiento(), detalle.cantidad());
                        movimientoRepositorio.registrarDetalleEntrada(idMovimiento, idLote,
                                detalle.idDetalleOrden(), detalle.cantidad());
                }
                String estado = pendienteTotal == 0 ? "RECIBIDA" : "PARCIALMENTE_RECIBIDA";
                if (ordenRepositorio.actualizarEstado(idOrden, estado) != 1)
                        throw new IllegalStateException("No se pudo actualizar el estado de la orden");
                int unidades = formulario.detalles().stream().mapToInt(DetalleRecepcionFormulario::cantidad).sum();
                auditoria.registrarCambio(AccionAuditoria.RECEPCION, "ORDEN_COMPRA", idOrden,
                                "movimiento: " + idMovimiento + "; unidades: " + unidades + "; estado: " + estado);
        }

        @Transactional(rollbackFor = Exception.class)
        public void cancelarOrden(Integer idOrden, String nombreUsuario) {
                validarUsuario(nombreUsuario, "COMPRAS");
                var orden = ordenRepositorio.buscarPorIdParaActualizar(idOrden)
                        .orElseThrow(() -> new IllegalArgumentException("Orden de compra no encontrada"));
                if (!orden.estado().equals("PENDIENTE"))
                        throw new IllegalArgumentException("Solo se pueden cancelar órdenes pendientes sin recepciones");
                boolean tieneRecepciones = ordenRepositorio.tieneRecepciones(idOrden)
                        || detalleRepositorio.listarLineasRecepcion(idOrden).stream()
                                .anyMatch(linea -> linea.cantidadRecibida() > 0);
                if (tieneRecepciones) throw new IllegalArgumentException("La orden ya tiene recepciones registradas");
                if (ordenRepositorio.actualizarEstado(idOrden, "CANCELADA") != 1)
                        throw new IllegalStateException("No se pudo cancelar la orden");
                auditoria.registrarCambio(AccionAuditoria.CANCELAR, "ORDEN_COMPRA", idOrden,
                                "estado: PENDIENTE → CANCELADA");
        }

        private com.farmacia.model.Usuario validarUsuario(String nombreUsuario, String rolRequerido) {
                var usuario = usuarioRepositorio.buscarPorNombreUsuario(nombreUsuario)
                        .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
                boolean permitido = usuario.rol().equals("ADMINISTRADOR") || usuario.rol().equals(rolRequerido);
                if (!usuario.activo() || !permitido) throw new AccessDeniedException("Usuario no autorizado");
                return usuario;
        }

        public List<DetalleOrdenCompraVista> listarDetallesVista(
                        Integer idOrden) {
                return detalleRepositorio.listarVistaPorOrden(idOrden);
        }
}
