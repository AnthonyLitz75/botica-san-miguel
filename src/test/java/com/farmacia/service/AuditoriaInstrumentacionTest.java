package com.farmacia.service;

import com.farmacia.dto.ClienteFormulario;
import com.farmacia.dto.MedicamentoFormulario;
import com.farmacia.dto.ProveedorFormulario;
import com.farmacia.model.Cliente;
import com.farmacia.model.Medicamento;
import com.farmacia.model.Proveedor;
import com.farmacia.repository.ClienteRepositorio;
import com.farmacia.repository.MedicamentoRepositorio;
import com.farmacia.repository.ProveedorRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 014, CA-3.1 y CA-3.3: cada operación de catálogo registra su evento con el detalle correcto. */
class AuditoriaInstrumentacionTest {
    private final AuditoriaServicio auditoria = mock(AuditoriaServicio.class);

    @Test
    @DisplayName("Medicamento: la edición registra solo los campos que cambiaron (antes → después)")
    void edicionDeMedicamentoDescribeLosCambios() {
        var repositorio = mock(MedicamentoRepositorio.class);
        when(repositorio.buscarPorId(4)).thenReturn(Optional.of(new Medicamento(4, "MED-004", "Paracetamol",
                "500 mg", "Caja", "CAJA", new BigDecimal("8.50"), 10, true)));
        when(repositorio.actualizar(eq(4), any())).thenReturn(1);
        var servicio = new MedicamentoServicio(repositorio, auditoria);

        servicio.actualizar(4, new MedicamentoFormulario("MED-004", "Paracetamol", "500 mg", "Caja", "CAJA",
                new BigDecimal("9.00"), 12));

        verify(auditoria).registrarCambio(AccionAuditoria.EDITAR, "MEDICAMENTO", 4,
                "precio_venta: 8.50 → 9.00; stock_minimo: 10 → 12");
    }

    @Test
    @DisplayName("Medicamento: alta, desactivación y activación quedan registradas; una edición sin efecto no")
    void otrasOperacionesDeMedicamento() {
        var repositorio = mock(MedicamentoRepositorio.class);
        when(repositorio.guardar(any())).thenReturn(7);
        when(repositorio.desactivar(7)).thenReturn(1);
        when(repositorio.activar(7)).thenReturn(1);
        when(repositorio.actualizar(eq(99), any())).thenReturn(0);
        var servicio = new MedicamentoServicio(repositorio, auditoria);
        var formulario = new MedicamentoFormulario(" MED-007 ", "Ibuprofeno", null, "Caja", "CAJA",
                new BigDecimal("4.00"), 5);

        servicio.guardar(formulario);
        servicio.desactivar(7);
        servicio.activar(7);
        servicio.actualizar(99, formulario);

        verify(auditoria).registrarCambio(AccionAuditoria.CREAR, "MEDICAMENTO", 7,
                "codigo: MED-007; nombre: Ibuprofeno; precio_venta: 4.00");
        verify(auditoria).registrarCambio(AccionAuditoria.DESACTIVAR, "MEDICAMENTO", 7, null);
        verify(auditoria).registrarCambio(AccionAuditoria.ACTIVAR, "MEDICAMENTO", 7, null);
        verify(auditoria, never()).registrarCambio(eq(AccionAuditoria.EDITAR), anyString(), any(), any());
    }

    @Test
    @DisplayName("Proveedor: alta identificada por RUC y edición con campos cambiados")
    void proveedor() {
        var repositorio = mock(ProveedorRepositorio.class);
        when(repositorio.buscarPorId(3)).thenReturn(Optional.of(new Proveedor(3, "20111111111", "Andina SAC",
                "999", "a@x.example", null, true)));
        when(repositorio.actualizar(eq(3), any())).thenReturn(1);
        var servicio = new ProveedorServicio(repositorio, auditoria);

        servicio.guardar(new ProveedorFormulario("20222222222", "Norte SAC", null, null, null));
        servicio.actualizar(3, new ProveedorFormulario("20111111111", "Andina SAC", "999", "compras@x.example", ""));

        verify(auditoria).registrarCambio(AccionAuditoria.CREAR, "PROVEEDOR", "20222222222", "razon_social: Norte SAC");
        verify(auditoria).registrarCambio(AccionAuditoria.EDITAR, "PROVEEDOR", 3,
                "correo: a@x.example → compras@x.example");
    }

    @Test
    @DisplayName("Cliente: la bitácora no guarda documento, nombre ni contacto, solo qué campos cambiaron")
    void clienteSinDatosPersonales() {
        var repositorio = mock(ClienteRepositorio.class);
        when(repositorio.buscarPorId(5)).thenReturn(Optional.of(
                new Cliente(5, "DNI", "12345678", "Ana Pérez", "987654321", "ana@x.example")));
        when(repositorio.actualizar(eq(5), any())).thenReturn(1);
        when(repositorio.guardar(any())).thenReturn(6);
        var servicio = new ClienteServicio(repositorio, auditoria);

        servicio.guardar(new ClienteFormulario("DNI", "87654321", "Luis Rojas", null, null));
        servicio.actualizar(5, new ClienteFormulario("DNI", "12345678", "Ana María Pérez", "987654321",
                "ana.maria@x.example"));

        var detalles = ArgumentCaptor.forClass(String.class);
        verify(auditoria).registrarCambio(eq(AccionAuditoria.CREAR), eq("CLIENTE"), eq(6), detalles.capture());
        verify(auditoria).registrarCambio(eq(AccionAuditoria.EDITAR), eq("CLIENTE"), eq(5), detalles.capture());
        assertEquals("tipo_documento: DNI", detalles.getAllValues().get(0));
        assertEquals("Campos modificados: nombre_completo; correo", detalles.getAllValues().get(1));
        for (String detalle : detalles.getAllValues()) {
            assertFalse(detalle.contains("87654321") || detalle.contains("Pérez") || detalle.contains("@"), detalle);
        }
    }

    @Test
    @DisplayName("DiferenciaCampos trata vacío y nulo como iguales y compara importes por valor")
    void diferenciaCampos() {
        var sinCambios = new DiferenciaCampos()
                .campo("direccion", "", null)
                .campo("precio_venta", new BigDecimal("9.0"), new BigDecimal("9.00"));
        assertFalse(sinCambios.hayCambios());
        assertEquals("Sin cambios en los datos", sinCambios.describir());
        assertTrue(new DiferenciaCampos().campo("telefono", null, "999").describir().contains("— → 999"));
    }
}
