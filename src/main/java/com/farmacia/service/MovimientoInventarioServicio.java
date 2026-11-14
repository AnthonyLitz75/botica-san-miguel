package com.farmacia.service;

import com.farmacia.dto.ExistenciaInicialFormulario;
import com.farmacia.dto.FiltroMovimientosFormulario;
import com.farmacia.dto.SalidaInventarioFormulario;
import com.farmacia.model.DetalleMovimientoInventario;
import com.farmacia.model.MovimientoInventario;
import com.farmacia.model.Usuario;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.MedicamentoRepositorio;
import com.farmacia.repository.MovimientoInventarioRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class MovimientoInventarioServicio {

        public static final int MOVIMIENTOS_POR_PAGINA = 20;

        public record Kardex(
                List<MovimientoInventario> filas,
                int total,
                int pagina,
                int paginas) {}

        private final MovimientoInventarioRepositorio repositorio;
        private final LoteRepositorio loteRepositorio;
        private final UsuarioRepositorio usuarioRepositorio;
        private final MedicamentoRepositorio medicamentoRepositorio;
        private final Clock clock;
        private final AuditoriaServicio auditoria;

        public MovimientoInventarioServicio(
                        MovimientoInventarioRepositorio repositorio,
                        LoteRepositorio loteRepositorio,
                        UsuarioRepositorio usuarioRepositorio,
                        AuditoriaServicio auditoria) {
                this(repositorio, loteRepositorio, usuarioRepositorio, null, Clock.systemDefaultZone(), auditoria);
        }

        @org.springframework.beans.factory.annotation.Autowired
        public MovimientoInventarioServicio(
                        MovimientoInventarioRepositorio repositorio,
                        LoteRepositorio loteRepositorio,
                        UsuarioRepositorio usuarioRepositorio,
                        MedicamentoRepositorio medicamentoRepositorio,
                        Clock clock,
                        AuditoriaServicio auditoria) {

                this.repositorio = repositorio;
                this.loteRepositorio = loteRepositorio;
                this.usuarioRepositorio = usuarioRepositorio;
                this.medicamentoRepositorio = medicamentoRepositorio;
                this.clock = clock != null ? clock : Clock.systemDefaultZone();
                this.auditoria = auditoria;
        }

        public List<MovimientoInventario> listar() {
                return repositorio.listar();
        }

        public List<MovimientoInventario> listar(FiltroMovimientosFormulario filtro) {
                return repositorio.listar(filtro);
        }

        public Kardex listarKardex(FiltroMovimientosFormulario filtro, int pagina) {
                if (filtro != null && filtro.desde() != null && filtro.hasta() != null
                                && filtro.hasta().isBefore(filtro.desde())) {
                        throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial");
                }
                int total = repositorio.contar(filtro);
                int paginas = Math.max(1, (total + MOVIMIENTOS_POR_PAGINA - 1) / MOVIMIENTOS_POR_PAGINA);
                int actual = Math.min(Math.max(1, pagina), paginas);
                int offset = (actual - 1) * MOVIMIENTOS_POR_PAGINA;
                List<MovimientoInventario> filas = repositorio.listar(filtro, MOVIMIENTOS_POR_PAGINA, offset);
                return new Kardex(filas, total, actual, paginas);
        }

        public List<Usuario> listarUsuariosActivos() {
                return usuarioRepositorio.listarActivos();
        }

        public List<DetalleMovimientoInventario> listarDetalles(Integer idMovimiento) {
                return repositorio.listarDetalles(idMovimiento);
        }

        public Optional<MovimientoInventario> buscarPorId(Integer idMovimiento) {
                return repositorio.buscarPorId(idMovimiento);
        }

        @Transactional(rollbackFor = Exception.class)
        public Integer registrarExistenciaInicial(String nombreUsuario, ExistenciaInicialFormulario formulario) {
                if (formulario == null) {
                        throw new IllegalArgumentException("El formulario es obligatorio");
                }
                if (formulario.idMedicamento() == null) {
                        throw new IllegalArgumentException("Selecciona un medicamento");
                }
                if (formulario.numeroLote() == null || formulario.numeroLote().isBlank()
                                || formulario.numeroLote().strip().length() > 50) {
                        throw new IllegalArgumentException("El número de lote es obligatorio y no puede superar los 50 caracteres");
                }
                if (formulario.fechaVencimiento() == null) {
                        throw new IllegalArgumentException("La fecha de vencimiento es obligatoria");
                }
                if (!formulario.fechaVencimiento().isAfter(LocalDate.now(clock))) {
                        throw new IllegalArgumentException("La fecha de vencimiento debe ser posterior a hoy");
                }
                if (formulario.cantidad() == null || formulario.cantidad() <= 0) {
                        throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
                }
                if (formulario.motivo() == null || formulario.motivo().isBlank()
                                || formulario.motivo().strip().length() > 250) {
                        throw new IllegalArgumentException("El motivo es obligatorio y no puede superar los 250 caracteres");
                }

                Usuario usuario = usuarioRepositorio.buscarPorNombreUsuario(nombreUsuario)
                                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
                if (!usuario.activo()
                                || !(usuario.rol().equals("ADMINISTRADOR")
                                                || usuario.rol().equals("ALMACENERO"))) {
                        throw new AccessDeniedException("Usuario no autorizado");
                }

                if (medicamentoRepositorio != null) {
                        var med = medicamentoRepositorio.buscarPorId(formulario.idMedicamento())
                                        .orElseThrow(() -> new IllegalArgumentException("El medicamento seleccionado no existe"));
                        if (!med.activo()) {
                                throw new IllegalArgumentException("No se pueden registrar existencias de un medicamento inactivo");
                        }
                }

                String numeroLote = formulario.numeroLote().strip();
                var loteExistenteOpt = loteRepositorio.buscarPorMedicamentoYNumero(formulario.idMedicamento(), numeroLote);
                if (loteExistenteOpt.isPresent()) {
                        var loteExistente = loteExistenteOpt.get();
                        if (!loteExistente.fechaVencimiento().equals(formulario.fechaVencimiento())) {
                                throw new IllegalArgumentException("El número de lote ya existe con otra fecha de vencimiento");
                        }
                        if (!Boolean.TRUE.equals(formulario.confirmarAcumulacion())) {
                                throw new IllegalArgumentException("El lote ya existe. Marca 'Acumular al lote existente' para sumar las existencias");
                        }
                }

                Integer idLote = loteRepositorio.registrarOIncrementar(
                                formulario.idMedicamento(),
                                numeroLote,
                                formulario.fechaVencimiento(),
                                formulario.cantidad());

                String motivo = formulario.motivo().strip();
                Integer idMovimiento = repositorio.registrarEntradaInicial(usuario.idUsuario(), motivo);
                repositorio.registrarDetalle(idMovimiento, idLote, formulario.cantidad());

                auditoria.registrarCambio(AccionAuditoria.EXISTENCIA_INICIAL, "LOTE", idLote,
                                "movimiento: " + idMovimiento + "; cantidad: " + formulario.cantidad() + "; motivo: " + motivo);
                return idMovimiento;
        }

        @Transactional
        public Integer registrarSalida(
                        Integer idLote,
                        String nombreUsuario,
                        SalidaInventarioFormulario formulario) {

                if (formulario.cantidad() == null || formulario.cantidad() <= 0) {
                        throw new IllegalArgumentException(
                                        "La cantidad debe ser mayor que cero");
                }

                if (formulario.motivo() == null || formulario.motivo().isBlank()
                                || formulario.motivo().length() > 250) {
                        throw new IllegalArgumentException(
                                        "El motivo es obligatorio y admite hasta 250 caracteres");
                }

                Usuario usuario = usuarioRepositorio
                                .buscarPorNombreUsuario(nombreUsuario)
                                .orElseThrow(() -> new AccessDeniedException(
                                                "Usuario no autorizado"));

                if (!usuario.activo()
                                || !(usuario.rol().equals("ADMINISTRADOR")
                                                || usuario.rol().equals("ALMACENERO"))) {
                        throw new AccessDeniedException("Usuario no autorizado");
                }

                loteRepositorio.buscarPorId(idLote)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Lote no encontrado"));

                int filasActualizadas = loteRepositorio.descontarStock(
                                idLote, formulario.cantidad());

                if (filasActualizadas != 1) {
                        throw new IllegalArgumentException(
                                        "No hay existencias suficientes para registrar la salida");
                }

                Integer idMovimiento = repositorio.registrarSalida(
                                usuario.idUsuario(), formulario.motivo().strip());

                repositorio.registrarDetalle(
                                idMovimiento, idLote, formulario.cantidad());

                auditoria.registrarCambio(AccionAuditoria.SALIDA, "LOTE", idLote,
                                "movimiento: " + idMovimiento + "; cantidad: " + formulario.cantidad()
                                                + "; motivo: " + formulario.motivo().strip());
                return idMovimiento;
        }
}