package com.farmacia.service;

import com.farmacia.dto.PreparacionVentaCambioFormulario;
import com.farmacia.model.AsignacionLoteFefo;
import com.farmacia.model.Medicamento;
import com.farmacia.model.Usuario;
import com.farmacia.repository.MedicamentoRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.annotation.SessionScope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreparacionVentaServicioTest {
    @Test
    void agregarYRetirarSeDeshacenEnOrdenLifo() {
        var medicamentos = mock(MedicamentoRepositorio.class);
        var usuarios = mock(UsuarioRepositorio.class);
        var prioridad = mock(LotePrioridadServicio.class);
        when(usuarios.buscarPorNombreUsuario("vendedor")).thenReturn(Optional.of(usuario("VENDEDOR")));
        when(medicamentos.buscarPorId(5)).thenReturn(Optional.of(medicamento()));
        when(prioridad.planificarSalida(eq(5), anyInt())).thenAnswer(invocacion ->
                List.of(new AsignacionLoteFefo(8, "LT-8", LocalDate.now().plusDays(10), 9,
                        invocacion.getArgument(1))));
        var servicio = new PreparacionVentaServicio(medicamentos, usuarios, prioridad);

        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(5, 2));
        var vacia = servicio.retirar("vendedor", 5);
        assertTrue(vacia.detalles().isEmpty());
        var restaurada = servicio.deshacer("vendedor");
        assertEquals(2, restaurada.detalles().getFirst().cantidad());
        var previa = servicio.deshacer("vendedor");
        assertTrue(previa.detalles().isEmpty());
        assertFalse(previa.puedeDeshacer());
    }

    @Test
    void noGuardaCambioCuandoElStockFefoEsInsuficiente() {
        var medicamentos = mock(MedicamentoRepositorio.class);
        var usuarios = usuarioRepo();
        var prioridad = mock(LotePrioridadServicio.class);
        when(medicamentos.buscarPorId(5)).thenReturn(Optional.of(medicamento()));
        when(prioridad.planificarSalida(5, 4)).thenThrow(new IllegalArgumentException("Stock insuficiente"));
        var servicio = servicio(medicamentos, usuarios, prioridad);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(5, 4)));
        var vista = servicio.obtener("vendedor");
        assertTrue(vista.detalles().isEmpty());
        assertFalse(vista.puedeDeshacer());
    }

    @Test
    void cadaInstanciaConservaSuPropioEstadoYLaClaseEsPorSesion() {
        var medicamentos = mock(MedicamentoRepositorio.class);
        var usuarios = usuarioRepo();
        when(medicamentos.buscarPorId(5)).thenReturn(Optional.of(medicamento()));
        var prioridad = mock(LotePrioridadServicio.class);
        when(prioridad.planificarSalida(5, 1)).thenReturn(List.of(new AsignacionLoteFefo(
                8, "LT-8", LocalDate.now().plusDays(10), 4, 1)));
        var primero = new PreparacionVentaServicio(medicamentos, usuarios, prioridad);
        var segundo = new PreparacionVentaServicio(medicamentos, usuarios, prioridad);
        primero.agregar("vendedor", new PreparacionVentaCambioFormulario(5, 1));

        assertEquals(1, primero.obtener("vendedor").detalles().size());
        assertTrue(segundo.obtener("vendedor").detalles().isEmpty());
        assertNotNull(PreparacionVentaServicio.class.getAnnotation(SessionScope.class));
    }

    @Test
    void listaEnlazadaMantieneOrdenDelCarritoYLaPilaRestauraLaInstantanea() {
        var medicamentos = mock(MedicamentoRepositorio.class);
        var usuarios = usuarioRepo();
        var prioridad = mock(LotePrioridadServicio.class);
        when(medicamentos.buscarPorId(5)).thenReturn(Optional.of(medicamento()));
        var segundoMedicamento = new Medicamento(6, "MED-6", "Ibuprofeno", "200 mg", "Caja", "CAJA",
                new BigDecimal("3.00"), 5, true);
        when(medicamentos.buscarPorId(6)).thenReturn(Optional.of(segundoMedicamento));
        when(prioridad.planificarSalida(eq(5), anyInt())).thenAnswer(invocacion ->
                List.of(new AsignacionLoteFefo(8, "LT-8", LocalDate.now().plusDays(10), 9,
                        invocacion.getArgument(1))));
        when(prioridad.planificarSalida(eq(6), anyInt())).thenAnswer(invocacion ->
                List.of(new AsignacionLoteFefo(9, "LT-9", LocalDate.now().plusDays(12), 4,
                        invocacion.getArgument(1))));
        var servicio = new PreparacionVentaServicio(medicamentos, usuarios, prioridad);

        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(5, 2));
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(6, 1));
        var trasActualizarPrimero = servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(5, 3));
        assertEquals(List.of(5, 6), trasActualizarPrimero.detalles().stream()
                .map(linea -> linea.idMedicamento()).toList());
        assertEquals(List.of(5, 1), trasActualizarPrimero.detalles().stream()
                .map(linea -> linea.cantidad()).toList());

        servicio.retirar("vendedor", 5);
        var restaurada = servicio.deshacer("vendedor");
        assertEquals(List.of(5, 6), restaurada.detalles().stream()
                .map(linea -> linea.idMedicamento()).toList());
        assertEquals(List.of(5, 1), restaurada.detalles().stream()
                .map(linea -> linea.cantidad()).toList());
    }

    @Test
    void rechazaActoresQueNoPuedenVender() {
        var usuarios = mock(UsuarioRepositorio.class);
        when(usuarios.buscarPorNombreUsuario("almacen")).thenReturn(Optional.of(usuario("ALMACENERO")));
        var servicio = servicio(mock(MedicamentoRepositorio.class), usuarios, mock(LotePrioridadServicio.class));
        assertThrows(AccessDeniedException.class, () -> servicio.obtener("almacen"));
    }

    private PreparacionVentaServicio servicio(MedicamentoRepositorio medicamentos,
            UsuarioRepositorio usuarios, LotePrioridadServicio prioridad) {
        return new PreparacionVentaServicio(medicamentos, usuarios, prioridad);
    }

    private UsuarioRepositorio usuarioRepo() {
        var usuarios = mock(UsuarioRepositorio.class);
        when(usuarios.buscarPorNombreUsuario("vendedor")).thenReturn(Optional.of(usuario("VENDEDOR")));
        return usuarios;
    }

    private Usuario usuario(String rol) {
        return new Usuario(3, "Vendedor", "vendedor", "hash", rol, true);
    }

    private Medicamento medicamento() {
        return new Medicamento(5, "MED-5", "Paracetamol", "500 mg", "Caja", "CAJA",
                new BigDecimal("2.50"), 5, true);
    }

}
