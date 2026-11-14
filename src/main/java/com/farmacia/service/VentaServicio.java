package com.farmacia.service;

import com.farmacia.dto.FiltroVentasFormulario;
import com.farmacia.model.AsignacionLoteFefo;
import com.farmacia.model.ComprobanteVista;
import com.farmacia.model.DetalleVenta;
import com.farmacia.model.LineaPreparacionVentaVista;
import com.farmacia.model.Usuario;
import com.farmacia.model.VentaConfirmadaVista;
import com.farmacia.model.VentaResumenVista;
import com.farmacia.repository.ClienteRepositorio;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.MovimientoInventarioRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import com.farmacia.repository.VentaRepositorio;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Confirmación de ventas (RF-12) y comprobante interno (RF-13).
 * No depende de la sesión HTTP: recibe las líneas de la preparación y el
 * controlador la vacía
 * solo después de que la transacción se confirma.
 */
@Service
public class VentaServicio {
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    public static final int VENTAS_POR_PAGINA = 20;

    /**
     * Resultado paginado del historial de ventas con totales y filtros efectivos.
     */
    public record Historial(
            List<VentaResumenVista> filas,
            BigDecimal totalFiltrado,
            int total,
            int pagina,
            int paginas,
            LocalDate desde,
            LocalDate hasta) {
    }

    /**
     * Resultado de "Mis ventas" para el vendedor (spec 005, US-4).
     */
    public record MisVentas(
            List<VentaResumenVista> filas,
            LocalDate desde,
            LocalDate hasta) {
    }

    private final UsuarioRepositorio usuarioRepositorio;
    private final ClienteRepositorio clienteRepositorio;
    private final LotePrioridadServicio lotePrioridadServicio;
    private final LoteRepositorio loteRepositorio;
    private final VentaRepositorio ventaRepositorio;
    private final MovimientoInventarioRepositorio movimientoRepositorio;
    private final Clock clock;
    private final AuditoriaServicio auditoria;

    public VentaServicio(UsuarioRepositorio usuarioRepositorio, ClienteRepositorio clienteRepositorio,
            LotePrioridadServicio lotePrioridadServicio, LoteRepositorio loteRepositorio,
            VentaRepositorio ventaRepositorio, MovimientoInventarioRepositorio movimientoRepositorio,
            Clock clock, AuditoriaServicio auditoria) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.clienteRepositorio = clienteRepositorio;
        this.lotePrioridadServicio = lotePrioridadServicio;
        this.loteRepositorio = loteRepositorio;
        this.ventaRepositorio = ventaRepositorio;
        this.movimientoRepositorio = movimientoRepositorio;
        this.clock = clock;
        this.auditoria = auditoria;
    }

    /**
     * Convierte la preparación en una venta en una sola transacción: vuelve a
     * planificar FEFO contra
     * la base (la base manda, no la sesión), reparte el descuento de cada línea
     * entre sus lotes
     * ({@link RepartoDescuento}), descuenta cada lote con un UPDATE condicional en
     * orden
     * ascendente de id_lote (evita interbloqueos) y guarda venta, detalles y su
     * movimiento de salida.
     * Cualquier fallo revierte todo.
     */
    @Transactional(rollbackFor = Exception.class)
    public VentaConfirmadaVista confirmar(String nombreUsuario, Integer idCliente,
            List<LineaPreparacionVentaVista> lineas) {
        Usuario vendedor = validarVendedor(nombreUsuario);
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("La venta no tiene productos");
        }
        if (idCliente != null && clienteRepositorio.buscarPorId(idCliente).isEmpty()) {
            throw new IllegalArgumentException("El cliente seleccionado no existe");
        }

        // 1. Re-planificar FEFO con el stock actual de la base y repartir el descuento
        // de cada línea.
        List<Tramo> tramos = new ArrayList<>();
        BigDecimal subtotal = CERO;
        BigDecimal descuentoTotal = CERO;
        for (var linea : lineas) {
            if (linea == null || linea.cantidad() == null || linea.cantidad() <= 0
                    || linea.precioUnitario() == null || linea.precioUnitario().signum() < 0) {
                throw new IllegalArgumentException("La preparación tiene una línea inválida");
            }
            BigDecimal precio = linea.precioUnitario().setScale(2, RoundingMode.HALF_UP);
            BigDecimal bruto = precio.multiply(BigDecimal.valueOf(linea.cantidad()));
            BigDecimal descuentoLinea = linea.descuento().setScale(2, RoundingMode.HALF_UP);
            if (descuentoLinea.signum() < 0 || descuentoLinea.compareTo(bruto) > 0) {
                throw new IllegalArgumentException("El descuento supera el importe de la línea");
            }
            List<AsignacionLoteFefo> asignaciones;
            try {
                asignaciones = lotePrioridadServicio.planificarSalida(linea.idMedicamento(), linea.cantidad());
            } catch (IllegalArgumentException sinStock) {
                throw new StockInsuficienteException(
                        "No hay existencias vigentes suficientes de " + linea.nombre()
                                + "; vuelve a preparar la venta");
            }
            List<BigDecimal> descuentos = RepartoDescuento.repartir(descuentoLinea,
                    asignaciones.stream().map(AsignacionLoteFefo::cantidadAsignada).toList());
            for (int i = 0; i < asignaciones.size(); i++) {
                var asignacion = asignaciones.get(i);
                var tramo = new Tramo(asignacion.idLote(), asignacion.cantidadAsignada(), precio, descuentos.get(i));
                if (tramo.descuento().compareTo(tramo.bruto()) > 0) {
                    throw new IllegalArgumentException("El descuento no se puede repartir entre los lotes de "
                            + linea.nombre() + "; ajústalo");
                }
                tramos.add(tramo);
            }
            subtotal = subtotal.add(bruto);
            descuentoTotal = descuentoTotal.add(descuentoLinea);
        }

        // 2. Descontar en orden ascendente de id_lote; el lote debe seguir vigente y
        // con stock.
        LocalDate hoy = LocalDate.now(clock);
        tramos.sort(Comparator.comparing(Tramo::idLote));
        for (var tramo : tramos) {
            if (loteRepositorio.descontarStockVigente(tramo.idLote(), tramo.cantidad(), hoy) != 1) {
                throw new StockInsuficienteException("El stock cambió; vuelve a preparar la venta");
            }
        }

        // 3. Venta, detalles (precio congelado) y salida de inventario ligada a la
        // venta.
        String numero = ventaRepositorio.siguienteNumeroComprobante();
        BigDecimal total = subtotal.subtract(descuentoTotal);
        Integer idVenta = ventaRepositorio.insertar(vendedor.idUsuario(), idCliente, subtotal,
                descuentoTotal, total, numero);
        for (var tramo : tramos) {
            ventaRepositorio.insertarDetalle(idVenta, new DetalleVenta(tramo.idLote(), tramo.cantidad(),
                    tramo.precio(), tramo.descuento(), tramo.bruto().subtract(tramo.descuento())));
        }
        Integer idMovimiento = movimientoRepositorio.registrarSalidaPorVenta(
                vendedor.idUsuario(), idVenta, "Venta " + numero);
        for (var tramo : tramos) {
            movimientoRepositorio.registrarDetalle(idMovimiento, tramo.idLote(), tramo.cantidad());
        }
        auditoria.registrarCambio(AccionAuditoria.CONFIRMAR_VENTA, "VENTA", idVenta,
                "comprobante: " + numero + "; lineas: " + lineas.size() + "; total: " + total);
        return new VentaConfirmadaVista(idVenta, numero);
    }

    /**
     * Comprobante de una venta. El vendedor solo ve las ventas que registró; el
     * administrador, todas.
     * Vacío si la venta no existe.
     */
    public Optional<ComprobanteVista> obtenerComprobante(Integer idVenta, String nombreUsuario) {
        Usuario usuario = validarVendedor(nombreUsuario);
        var comprobante = ventaRepositorio.buscarComprobante(idVenta);
        if (comprobante.isPresent() && usuario.rol().equals("VENDEDOR")
                && !comprobante.get().idVendedor().equals(usuario.idUsuario())) {
            throw new AccessDeniedException("No puedes ver la venta de otro vendedor");
        }
        return comprobante;
    }

    private Usuario validarVendedor(String nombreUsuario) {
        var usuario = usuarioRepositorio.buscarPorNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        if (!usuario.activo()
                || !(usuario.rol().equals("ADMINISTRADOR") || usuario.rol().equals("VENDEDOR"))) {
            throw new AccessDeniedException("Usuario no autorizado");
        }
        return usuario;
    }

    /**
     * Consulta el historial general de ventas (solo ADMINISTRADOR; spec 005,
     * RF-001, US-1).
     * Por defecto consulta los últimos 30 días según el reloj; si hasta < desde se
     * rechaza sin consultar (CA-1.4).
     */
    public Historial listarHistorial(String nombreUsuario, FiltroVentasFormulario filtro, int pagina) {
        validarAdministrador(nombreUsuario);
        var efectivo = filtroEfectivo(filtro);
        int total = ventaRepositorio.contar(efectivo);
        int paginas = Math.max(1, (total + VENTAS_POR_PAGINA - 1) / VENTAS_POR_PAGINA);
        int actual = Math.min(Math.max(1, pagina), paginas);
        int offset = (actual - 1) * VENTAS_POR_PAGINA;
        var filas = ventaRepositorio.listar(efectivo, VENTAS_POR_PAGINA, offset);
        BigDecimal totalFiltrado = ventaRepositorio.sumarTotal(efectivo);
        return new Historial(filas, totalFiltrado, total, actual, paginas, efectivo.desde(), efectivo.hasta());
    }

    public Historial listarHistorial(String nombreUsuario, FiltroVentasFormulario filtro) {
        int pagina = (filtro != null && filtro.pagina() != null && filtro.pagina() > 0) ? filtro.pagina() : 1;
        return listarHistorial(nombreUsuario, filtro, pagina);
    }

    public List<Usuario> listarVendedores() {
        return usuarioRepositorio.listarActivos();
    }

    /**
     * Consulta las ventas propias del vendedor autenticado (spec 005, US-4, RF-005).
     * Rango máximo de 30 días, por defecto hoy según Clock.
     */
    public MisVentas listarMisVentas(String nombreUsuario, LocalDate desde, LocalDate hasta) {
        var usuario = validarSoloVendedor(nombreUsuario);
        LocalDate hoy = LocalDate.now(clock);

        LocalDate desdeEfectivo = desde;
        LocalDate hastaEfectivo = hasta;

        if (desdeEfectivo == null && hastaEfectivo == null) {
            desdeEfectivo = hoy;
            hastaEfectivo = hoy;
        } else if (desdeEfectivo == null) {
            desdeEfectivo = hastaEfectivo;
        } else if (hastaEfectivo == null) {
            hastaEfectivo = hoy;
        }

        if (hastaEfectivo.isBefore(desdeEfectivo)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial");
        }

        if (desdeEfectivo.isBefore(hoy.minusDays(30)) || java.time.temporal.ChronoUnit.DAYS.between(desdeEfectivo, hastaEfectivo) > 30) {
            throw new IllegalArgumentException("El rango de fechas no puede superar los 30 días hacia atrás");
        }

        var filtro = new FiltroVentasFormulario(desdeEfectivo, hastaEfectivo, usuario.idUsuario(), null, null);
        var filas = ventaRepositorio.listar(filtro, 1000, 0);
        return new MisVentas(filas, desdeEfectivo, hastaEfectivo);
    }

    private Usuario validarSoloVendedor(String nombreUsuario) {
        var usuario = usuarioRepositorio.buscarPorNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        if (!usuario.activo() || !usuario.rol().equals("VENDEDOR")) {
            throw new AccessDeniedException("Usuario no autorizado");
        }
        return usuario;
    }

    private FiltroVentasFormulario filtroEfectivo(FiltroVentasFormulario filtro) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate desde = filtro != null ? filtro.desde() : null;
        LocalDate hasta = filtro != null ? filtro.hasta() : null;

        if (desde == null && hasta == null) {
            hasta = hoy;
            desde = hoy.minusDays(30);
        } else if (desde == null) {
            desde = hasta.minusDays(30);
        } else if (hasta == null) {
            hasta = hoy;
        }

        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial");
        }

        Integer idVendedor = filtro != null ? filtro.idVendedor() : null;
        String comprobante = filtro != null ? filtro.comprobante() : null;
        Integer pagina = filtro != null ? filtro.pagina() : null;

        return new FiltroVentasFormulario(desde, hasta, idVendedor, comprobante, pagina);
    }

    private Usuario validarAdministrador(String nombreUsuario) {
        var usuario = usuarioRepositorio.buscarPorNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        if (!usuario.activo() || !usuario.rol().equals("ADMINISTRADOR")) {
            throw new AccessDeniedException("Usuario no autorizado");
        }
        return usuario;
    }

    private record Tramo(Integer idLote, Integer cantidad, BigDecimal precio, BigDecimal descuento) {
        BigDecimal bruto() {
            return precio.multiply(BigDecimal.valueOf(cantidad));
        }
    }
}
