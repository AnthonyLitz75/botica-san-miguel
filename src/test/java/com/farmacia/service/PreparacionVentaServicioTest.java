package com.farmacia.service;

import com.farmacia.dto.PreparacionVentaCambioFormulario;
import com.farmacia.model.AsignacionLoteFefo;
import com.farmacia.model.Medicamento;
import com.farmacia.model.Usuario;
import com.farmacia.repository.MedicamentoRepositorio;
import com.farmacia.repository.UsuarioRepositorio;
import com.farmacia.service.politica.DescuentoImporteFijo;
import com.farmacia.service.politica.PoliticaDescuento;
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
        var servicio = new PreparacionVentaServicio(medicamentos, usuarios, prioridad, new DescuentoImporteFijo());

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
    void limpiarVaciaLaListaYLaPilaDeDeshacer() {
        // Spec 003, T305: tras confirmar la venta no queda nada que deshacer.
        var medicamentos = mock(MedicamentoRepositorio.class);
        var usuarios = usuarioRepo();
        var prioridad = mock(LotePrioridadServicio.class);
        when(medicamentos.buscarPorId(5)).thenReturn(Optional.of(medicamento()));
        when(prioridad.planificarSalida(5, 2)).thenReturn(List.of(new AsignacionLoteFefo(
                8, "LT-8", LocalDate.now().plusDays(10), 9, 2)));
        var servicio = servicio(medicamentos, usuarios, prioridad);
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(5, 2));

        servicio.limpiar("vendedor");

        var vista = servicio.obtener("vendedor");
        assertTrue(vista.detalles().isEmpty());
        assertFalse(vista.puedeDeshacer());
        assertTrue(servicio.deshacer("vendedor").detalles().isEmpty());
    }

    @Test
    void cadaInstanciaConservaSuPropioEstadoYLaClaseEsPorSesion() {
        var medicamentos = mock(MedicamentoRepositorio.class);
        var usuarios = usuarioRepo();
        when(medicamentos.buscarPorId(5)).thenReturn(Optional.of(medicamento()));
        var prioridad = mock(LotePrioridadServicio.class);
        when(prioridad.planificarSalida(5, 1)).thenReturn(List.of(new AsignacionLoteFefo(
                8, "LT-8", LocalDate.now().plusDays(10), 4, 1)));
        var primero = new PreparacionVentaServicio(medicamentos, usuarios, prioridad, new DescuentoImporteFijo());
        var segundo = new PreparacionVentaServicio(medicamentos, usuarios, prioridad, new DescuentoImporteFijo());
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
        var servicio = new PreparacionVentaServicio(medicamentos, usuarios, prioridad, new DescuentoImporteFijo());

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

    // ------------------------------------------------------------------ spec 004: descuentos

    /** Preparación con Paracetamol (id 10) a S/ 10.00 y lotes suficientes. */
    private PreparacionVentaServicio servicioConDiezSoles(PoliticaDescuento politica) {
        var medicamentos = mock(MedicamentoRepositorio.class);
        when(medicamentos.buscarPorId(10)).thenReturn(Optional.of(new Medicamento(10, "MED-10", "Paracetamol",
                "500 mg", "Caja", "CAJA", new BigDecimal("10.00"), 5, true)));
        var prioridad = mock(LotePrioridadServicio.class);
        when(prioridad.planificarSalida(eq(10), anyInt())).thenAnswer(invocacion ->
                List.of(new AsignacionLoteFefo(8, "LT-8", LocalDate.now().plusDays(10), 99,
                        invocacion.getArgument(1))));
        return new PreparacionVentaServicio(medicamentos, usuarioRepo(), prioridad, politica);
    }

    @Test
    void ca11_descuentoDeCincoSobreTresPorDiez() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3));

        var vista = servicio.aplicarDescuento("vendedor", 10, new BigDecimal("5.00"));

        var linea = vista.detalles().getFirst();
        assertEquals(new BigDecimal("5.00"), linea.descuento());
        assertEquals(new BigDecimal("25.00"), linea.importe());
        assertEquals(new BigDecimal("30.00"), vista.subtotal());
        assertEquals(new BigDecimal("5.00"), vista.descuentoTotal());
        assertEquals(new BigDecimal("25.00"), vista.total());
    }

    @Test
    void ca11_elDescuentoTambienLlegaEnElAgregar() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());

        var vista = servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3, new BigDecimal("5.00")));

        assertEquals(new BigDecimal("25.00"), vista.total());
    }

    @Test
    void ca12_descuentoMayorQueLaLineaNoCambiaLaVenta() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3));

        var error = assertThrows(IllegalArgumentException.class,
                () -> servicio.aplicarDescuento("vendedor", 10, new BigDecimal("30.01")));

        assertEquals("El descuento supera el importe de la línea", error.getMessage());
        var vista = servicio.obtener("vendedor");
        assertEquals(new BigDecimal("30.00"), vista.total());
        assertEquals(new BigDecimal("0.00"), vista.descuentoTotal());
    }

    @Test
    void ca13_descuentoNegativoSeRechaza() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.aplicarDescuento("vendedor", 10, new BigDecimal("-1.00")));
    }

    @Test
    void ca14_alCambiarLaCantidadElDescuentoSeRevalida() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3, new BigDecimal("25.00")));
        // Sumar 1 unidad conserva el descuento y lo revalida contra el nuevo bruto (40.00)
        assertEquals(new BigDecimal("15.00"),
                servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 1)).total());
        // Un descuento que no cabe en el nuevo bruto (50.00) se rechaza y la línea no cambia
        var error = assertThrows(IllegalArgumentException.class,
                () -> servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 1, new BigDecimal("60.00"))));
        assertEquals("El descuento supera el importe de la línea", error.getMessage());
        assertEquals(4, servicio.obtener("vendedor").detalles().getFirst().cantidad());
    }

    @Test
    void lineaGratisPermitida() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3));

        var vista = servicio.aplicarDescuento("vendedor", 10, new BigDecimal("30.00"));

        assertEquals(new BigDecimal("0.00"), vista.total());
    }

    @Test
    void ca21_deshacerRestauraElDescuentoAnterior() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3));
        servicio.aplicarDescuento("vendedor", 10, new BigDecimal("5.00"));
        servicio.aplicarDescuento("vendedor", 10, new BigDecimal("8.00"));

        assertEquals(new BigDecimal("5.00"), servicio.deshacer("vendedor").descuentoTotal());
        assertEquals(new BigDecimal("0.00"), servicio.deshacer("vendedor").descuentoTotal());
    }

    @Test
    void variasLineasSumanSusDescuentosYRetirarQuitaElSuyo() {
        var medicamentos = mock(MedicamentoRepositorio.class);
        when(medicamentos.buscarPorId(10)).thenReturn(Optional.of(new Medicamento(10, "MED-10", "Paracetamol",
                "500 mg", "Caja", "CAJA", new BigDecimal("10.00"), 5, true)));
        when(medicamentos.buscarPorId(11)).thenReturn(Optional.of(new Medicamento(11, "MED-11", "Ibuprofeno",
                "400 mg", "Caja", "CAJA", new BigDecimal("4.00"), 5, true)));
        var prioridad = mock(LotePrioridadServicio.class);
        when(prioridad.planificarSalida(anyInt(), anyInt())).thenAnswer(invocacion ->
                List.of(new AsignacionLoteFefo(8, "LT-8", LocalDate.now().plusDays(10), 99,
                        invocacion.getArgument(1))));
        var servicio = new PreparacionVentaServicio(medicamentos, usuarioRepo(), prioridad, new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 2, new BigDecimal("3.00")));
        var vista = servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(11, 5, new BigDecimal("1.50")));

        assertEquals(new BigDecimal("40.00"), vista.subtotal());
        assertEquals(new BigDecimal("4.50"), vista.descuentoTotal());
        assertEquals(new BigDecimal("35.50"), vista.total());
        assertEquals(new BigDecimal("1.50"), servicio.retirar("vendedor", 10).descuentoTotal());
    }

    @Test
    void ca43_otraPoliticaCambiaElImporteSinModificarElServicio() {
        // Política de prueba (no expuesta en la interfaz ni en la API): el valor ingresado es un porcentaje.
        PoliticaDescuento porcentaje = (bruto, valor) -> valor == null
                ? BigDecimal.ZERO.setScale(2)
                : bruto.multiply(valor).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        var servicio = servicioConDiezSoles(porcentaje);
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3));

        var vista = servicio.aplicarDescuento("vendedor", 10, new BigDecimal("10"));

        assertEquals(new BigDecimal("3.00"), vista.descuentoTotal());
        assertEquals(new BigDecimal("27.00"), vista.total());
    }

    @Test
    void aplicarDescuentoAUnMedicamentoQueNoEstaSeRechaza() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        assertThrows(IllegalArgumentException.class,
                () -> servicio.aplicarDescuento("vendedor", 10, new BigDecimal("1.00")));
    }

    private PreparacionVentaServicio servicio(MedicamentoRepositorio medicamentos,
            UsuarioRepositorio usuarios, LotePrioridadServicio prioridad) {
        return new PreparacionVentaServicio(medicamentos, usuarios, prioridad, new DescuentoImporteFijo());
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
