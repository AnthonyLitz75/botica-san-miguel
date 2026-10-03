package com.farmacia.service;

import com.farmacia.model.MovimientoInventario;
import com.farmacia.repository.MovimientoInventarioRepositorio;
import org.springframework.stereotype.Service;
import com.farmacia.model.DetalleMovimientoInventario;
import java.util.Optional;
import com.farmacia.repository.LoteRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import com.farmacia.dto.SalidaInventarioFormulario;
import com.farmacia.model.Usuario;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MovimientoInventarioServicio {

        private final MovimientoInventarioRepositorio repositorio;
        private final LoteRepositorio loteRepositorio;
        private final UsuarioRepositorio usuarioRepositorio;

        public MovimientoInventarioServicio(
                        MovimientoInventarioRepositorio repositorio,
                        LoteRepositorio loteRepositorio,
                        UsuarioRepositorio usuarioRepositorio) {

                this.repositorio = repositorio;
                this.loteRepositorio = loteRepositorio;
                this.usuarioRepositorio = usuarioRepositorio;
        }

        public List<MovimientoInventario> listar() {
                return repositorio.listar();
        }

        public List<DetalleMovimientoInventario> listarDetalles(Integer idMovimiento) {
                return repositorio.listarDetalles(idMovimiento);
        }

        public Optional<MovimientoInventario> buscarPorId(Integer idMovimiento) {
                return repositorio.buscarPorId(idMovimiento);
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

                return idMovimiento;
        }
}