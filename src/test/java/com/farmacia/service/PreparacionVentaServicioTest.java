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

    @Test
    void modificarCantidadActualizaLotesYPermiteDeshacer() {
        var medicamentos = mock(MedicamentoRepositorio.class);
        var usuarios = usuarioRepo();
        var prioridad = mock(LotePrioridadServicio.class);
        when(medicamentos.buscarPorId(5)).thenReturn(Optional.of(medicamento()));
        when(prioridad.planificarSalida(eq(5), anyInt())).thenAnswer(inv ->
                List.of(new AsignacionLoteFefo(8, "LT-8", LocalDate.now().plusDays(10), 9, inv.getArgument(1))));
        var servicio = servicio(medicamentos, usuarios, prioridad);

        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(5, 2));
        var modificada = servicio.modificarCantidad("vendedor", 5, 4);
        assertEquals(4, modificada.detalles().getFirst().cantidad());

        var deshecha = servicio.deshacer("vendedor");
        assertEquals(2, deshecha.detalles().getFirst().cantidad());

        // Modificar a 0 retira la línea
        var retirada = servicio.modificarCantidad("vendedor", 5, 0);
        assertTrue(retirada.detalles().isEmpty());
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

    // ------------------------------------------------------------------ FASE A: Proporcionalidad de Descuentos al Modificar Cantidad (H-05)

    @Test
    void ejemplo1_reduccionCantidadConDescuentoFijo() {
        // Situación inicial: P.U. S/ 10.00, Cantidad 2, Subtotal S/ 20.00, Descuento S/ 15.00, Total S/ 5.00
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 2, new BigDecimal("15.00")));

        // El usuario reduce la cantidad a 1
        var vista = servicio.modificarCantidad("vendedor", 10, 1);

        // Resultado esperado: Nuevo subtotal S/ 10.00, Nuevo descuento S/ 7.50, Nuevo total S/ 2.50
        assertEquals(new BigDecimal("10.00"), vista.subtotal());
        assertEquals(new BigDecimal("7.50"), vista.descuentoTotal());
        assertEquals(new BigDecimal("2.50"), vista.total());

        var linea = vista.detalles().getFirst();
        assertEquals(1, linea.cantidad());
        assertEquals(new BigDecimal("7.50"), linea.descuento());
        assertEquals(new BigDecimal("2.50"), linea.importe());
    }

    @Test
    void ejemplo2_reduccionCantidadConDescuentoPequeno() {
        // Situación inicial: P.U. S/ 8.50, Cantidad 3, Subtotal S/ 25.50, Descuento S/ 3.00
        var medicamentos = mock(MedicamentoRepositorio.class);
        when(medicamentos.buscarPorId(20)).thenReturn(Optional.of(new Medicamento(20, "MED-20", "Amoxicilina",
                "500 mg", "Caja", "CAJA", new BigDecimal("8.50"), 10, true)));
        var prioridad = mock(LotePrioridadServicio.class);
        when(prioridad.planificarSalida(eq(20), anyInt())).thenAnswer(inv ->
                List.of(new AsignacionLoteFefo(1, "LT-1", LocalDate.now().plusDays(20), 50, inv.getArgument(1))));
        var servicio = new PreparacionVentaServicio(medicamentos, usuarioRepo(), prioridad, new DescuentoImporteFijo());

        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(20, 3, new BigDecimal("3.00")));

        // El usuario reduce la cantidad a 2
        var vista = servicio.modificarCantidad("vendedor", 20, 2);

        // Resultado esperado: Nuevo subtotal S/ 17.00, Nuevo descuento S/ 2.00, Nuevo total S/ 15.00
        assertEquals(new BigDecimal("17.00"), vista.subtotal());
        assertEquals(new BigDecimal("2.00"), vista.descuentoTotal());
        assertEquals(new BigDecimal("15.00"), vista.total());

        var linea = vista.detalles().getFirst();
        assertEquals(2, linea.cantidad());
        assertEquals(new BigDecimal("2.00"), linea.descuento());
        assertEquals(new BigDecimal("15.00"), linea.importe());
    }

    @Test
    void ejemplo3_aumentoCantidadConservaDescuentoAbsoluto() {
        // Situación inicial: P.U. S/ 10.00, Cantidad 1, Subtotal S/ 10.00, Descuento S/ 5.00
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 1, new BigDecimal("5.00")));

        // El usuario aumenta la cantidad a 2
        var vista = servicio.modificarCantidad("vendedor", 10, 2);

        // Resultado esperado: Nuevo subtotal S/ 20.00, Nuevo descuento S/ 5.00, Nuevo total S/ 15.00
        assertEquals(new BigDecimal("20.00"), vista.subtotal());
        assertEquals(new BigDecimal("5.00"), vista.descuentoTotal());
        assertEquals(new BigDecimal("15.00"), vista.total());

        var linea = vista.detalles().getFirst();
        assertEquals(2, linea.cantidad());
        assertEquals(new BigDecimal("5.00"), linea.descuento());
        assertEquals(new BigDecimal("15.00"), linea.importe());
    }

    @Test
    void modificarCantidad_descuentoIgualAlSubtotal_alReducirSigueSiendoGratis() {
        // 3 unidades a S/ 10.00 = S/ 30.00 con descuento S/ 30.00 (100% de descuento)
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3, new BigDecimal("30.00")));

        // Reducir a 1 unidad
        var vista = servicio.modificarCantidad("vendedor", 10, 1);

        assertEquals(new BigDecimal("10.00"), vista.subtotal());
        assertEquals(new BigDecimal("10.00"), vista.descuentoTotal());
        assertEquals(new BigDecimal("0.00"), vista.total());
    }

    @Test
    void modificarCantidad_lineaSinDescuento_conservaDescuentoCero() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3));

        var vista = servicio.modificarCantidad("vendedor", 10, 2);

        assertEquals(new BigDecimal("20.00"), vista.subtotal());
        assertEquals(new BigDecimal("0.00"), vista.descuentoTotal());
        assertEquals(new BigDecimal("20.00"), vista.total());
    }

    @Test
    void modificarCantidad_descuentoConRedondeoMedio() {
        // 3 unidades a S/ 10.00 = S/ 30.00 con descuento S/ 10.00
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3, new BigDecimal("10.00")));

        // Reduce a 2 unidades (subtotal 20.00):
        // 10.00 * (20.00 / 30.00) = 6.6666... -> redondeado HALF_UP = S/ 6.67
        var vista = servicio.modificarCantidad("vendedor", 10, 2);

        assertEquals(new BigDecimal("20.00"), vista.subtotal());
        assertEquals(new BigDecimal("6.67"), vista.descuentoTotal());
        assertEquals(new BigDecimal("13.33"), vista.total());
    }

    @Test
    void modificarCantidad_reducirHastaMinimoPermitidoUnaUnidad() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 5, new BigDecimal("25.00")));

        var vista = servicio.modificarCantidad("vendedor", 10, 1);

        assertEquals(new BigDecimal("10.00"), vista.subtotal());
        assertEquals(new BigDecimal("5.00"), vista.descuentoTotal());
        assertEquals(new BigDecimal("5.00"), vista.total());
    }

    @Test
    void modificarCantidad_deshacerRecuperaCantidadYDescuentoOriginales() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 2, new BigDecimal("15.00")));

        // Modifica a 1 unidad
        servicio.modificarCantidad("vendedor", 10, 1);

        // Deshacer recupera la situación inicial (2 unidades, S/ 15.00 de descuento)
        var restaurada = servicio.deshacer("vendedor");
        assertEquals(2, restaurada.detalles().getFirst().cantidad());
        assertEquals(new BigDecimal("20.00"), restaurada.subtotal());
        assertEquals(new BigDecimal("15.00"), restaurada.descuentoTotal());
        assertEquals(new BigDecimal("5.00"), restaurada.total());
    }

    @Test
    void modificarCantidad_deshacerMultiplesModificacionesConsecutivas() {
        var servicio = servicioConDiezSoles(new DescuentoImporteFijo());
        servicio.agregar("vendedor", new PreparacionVentaCambioFormulario(10, 3, new BigDecimal("9.00")));

        // Paso 1: reduce a 2 (descuento = 9 * 20/30 = 6.00)
        servicio.modificarCantidad("vendedor", 10, 2);
        // Paso 2: reduce a 1 (descuento = 6 * 10/20 = 3.00)
        servicio.modificarCantidad("vendedor", 10, 1);

        // Deshacer 1 -> vuelve a 2 unidades y S/ 6.00
        var deshecho1 = servicio.deshacer("vendedor");
        assertEquals(2, deshecho1.detalles().getFirst().cantidad());
        assertEquals(new BigDecimal("6.00"), deshecho1.descuentoTotal());
        assertEquals(new BigDecimal("14.00"), deshecho1.total());

        // Deshacer 2 -> vuelve a 3 unidades y S/ 9.00
        var deshecho2 = servicio.deshacer("vendedor");
        assertEquals(3, deshecho2.detalles().getFirst().cantidad());
        assertEquals(new BigDecimal("9.00"), deshecho2.descuentoTotal());
        assertEquals(new BigDecimal("21.00"), deshecho2.total());
    }

}
