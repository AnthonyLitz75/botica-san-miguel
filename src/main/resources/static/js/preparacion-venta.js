/**
 * preparacion-venta.js — Orquestador principal del Punto de Venta (Caja POS)
 * Botica San Miguel (AyE)
 * Integra módulos desacoplados: pos-api, pos-render-canasta, pos-buscador-med, pos-buscador-cli, pos-atajos.
 */

import {
  formatearMoneda,
  formatearFecha,
  normalizarTexto,
  obtenerCsrf,
  SesionExpiradaError,
  MENSAJE_SESION_EXPIRADA,
} from "./core/api-cliente.js";

import {
  cargar,
  agregar,
  retirar,
  vaciarPreparacion,
  modificarCantidad,
  deshacer,
  aplicarDescuento,
  confirmar,
  peticion,
} from "./modulos/pos-api.js";

import {
  pintarEstado,
  obtenerEstadoActual,
  mostrarAlerta,
  limpiarAlerta,
  mostrarAvisoCaja,
  toggleBotonesAccion,
} from "./modulos/pos-render-canasta.js";

import {
  actualizarSugerencias,
  inicializarBuscadorMedicamentos,
} from "./modulos/pos-buscador-med.js";

import {
  seleccionarCliente,
  actualizarSugerenciasCliente,
  inicializarBuscadorClientes,
} from "./modulos/pos-buscador-cli.js";

import {
  configurarAtajosTeclado,
  configurarSelectorPago,
  configurarBotonCancelar,
} from "./modulos/pos-atajos.js";

import {
  inicializarModalDescuento,
  abrirModalDescuento,
  cerrarModalDescuento,
} from "./modulos/pos-modal-descuento.js";

// ============================================================================
// Catálogos reactivos inyectados por la vista Thymeleaf
// ============================================================================
function obtenerCatalogoMedicamentos() {
  return Array.isArray(window.CATALOGO_MEDICAMENTOS) ? window.CATALOGO_MEDICAMENTOS : [];
}

function obtenerCatalogoClientes() {
  return Array.isArray(window.CATALOGO_CLIENTES) ? window.CATALOGO_CLIENTES : [];
}

// ============================================================================
// Flujos de Negocio de Mostrador
// ============================================================================

/**
 * Agrega un medicamento a la canasta directamente desde el buscador predictivo.
 * @param {number|string} idMedicamento 
 * @param {number} cantidad 
 */
export async function agregarProductoDirecto(idMedicamento, cantidad = 1) {
  limpiarAlerta();
  mostrarAvisoCaja("");
  toggleBotonesAccion(true);
  try {
    const nuevoEstado = await agregar(idMedicamento, cantidad);
    pintarEstado(nuevoEstado, obtenerCatalogoMedicamentos());
    const inputBuscar = document.getElementById("buscar-caja");
    if (inputBuscar) {
      inputBuscar.value = "";
      actualizarSugerencias("", obtenerCatalogoMedicamentos(), agregarProductoDirecto);
      inputBuscar.focus();
    }
  } catch (error) {
    mostrarAvisoCaja(error.message);
    mostrarAlerta(error.message);
  } finally {
    toggleBotonesAccion(false);
  }
}

/**
 * Maneja el formulario tradicional de agregar producto (compatibilidad de formulario).
 * @param {Event} [evento] 
 */
export async function manejarAgregar(evento) {
  if (evento) evento.preventDefault();
  limpiarAlerta();

  const selectMedicamento = document.getElementById("select-medicamento");
  const inputCantidad = document.getElementById("input-cantidad");
  if (!selectMedicamento || !inputCantidad) return;

  const idMedicamento = selectMedicamento.value;
  const cantidad = inputCantidad.value;

  if (!idMedicamento) {
    mostrarAlerta("Por favor selecciona un medicamento de la lista.");
    return;
  }

  const cantNum = Number.parseInt(cantidad, 10);
  if (Number.isNaN(cantNum) || cantNum < 1) {
    mostrarAlerta("La cantidad debe ser un número entero mayor o igual a 1.");
    return;
  }

  toggleBotonesAccion(true);
  try {
    const nuevoEstado = await agregar(idMedicamento, cantNum);
    pintarEstado(nuevoEstado, obtenerCatalogoMedicamentos());
    inputCantidad.value = "1";
  } catch (error) {
    mostrarAlerta(error.message);
  } finally {
    toggleBotonesAccion(false);
  }
}

/**
 * Retira una línea de la canasta por su identificador.
 * @param {number|string} idMedicamento 
 */
export async function manejarRetirar(idMedicamento) {
  limpiarAlerta();
  toggleBotonesAccion(true);
  try {
    const nuevoEstado = await retirar(idMedicamento);
    pintarEstado(nuevoEstado, obtenerCatalogoMedicamentos());
  } catch (error) {
    mostrarAlerta(error.message);
  } finally {
    toggleBotonesAccion(false);
  }
}

/**
 * Modifica la cantidad de una línea mediante el stepper interactivo (+ / -).
 * @param {number|string} idMedicamento 
 * @param {number} cantidad 
 */
export async function manejarModificarCantidad(idMedicamento, cantidad) {
  limpiarAlerta();
  toggleBotonesAccion(true);
  try {
    const nuevoEstado = await modificarCantidad(idMedicamento, cantidad);
    pintarEstado(nuevoEstado, obtenerCatalogoMedicamentos());
  } catch (error) {
    mostrarAlerta(error.message);
  } finally {
    toggleBotonesAccion(false);
  }
}

/**
 * Deshace el último cambio en la canasta (Pila LIFO en memoria).
 */
export async function manejarDeshacer() {
  limpiarAlerta();
  toggleBotonesAccion(true);
  try {
    const nuevoEstado = await deshacer();
    pintarEstado(nuevoEstado, obtenerCatalogoMedicamentos());
  } catch (error) {
    mostrarAlerta(error.message);
  } finally {
    toggleBotonesAccion(false);
  }
}

/**
 * Maneja el cambio de valor en un input de descuento tradicional.
 * @param {HTMLInputElement} campo 
 */
export async function manejarDescuento(campo) {
  limpiarAlerta();
  const texto = String(campo.value || "").trim();
  const valor = texto === "" ? 0 : Number(texto);
  if (Number.isNaN(valor) || valor < 0) {
    mostrarAlerta("El descuento debe ser un importe mayor o igual a 0.");
    pintarEstado(obtenerEstadoActual(), obtenerCatalogoMedicamentos());
    return;
  }
  toggleBotonesAccion(true);
  try {
    pintarEstado(await aplicarDescuento(campo.dataset.id, valor.toFixed(2)), obtenerCatalogoMedicamentos());
  } catch (error) {
    mostrarAlerta(error.message);
    pintarEstado(obtenerEstadoActual(), obtenerCatalogoMedicamentos());
  } finally {
    toggleBotonesAccion(false);
  }
}

/**
 * Aplica un descuento directamente con actualización visual inmediata.
 * @param {number|string} idMedicamento 
 * @param {string|number} valor 
 */
export async function ejecutarDescuentoDirecto(idMedicamento, valor) {
  limpiarAlerta();
  mostrarAvisoCaja("");
  toggleBotonesAccion(true);
  try {
    const nuevoEstado = await aplicarDescuento(idMedicamento, valor);
    pintarEstado(nuevoEstado, obtenerCatalogoMedicamentos());
  } catch (error) {
    mostrarAvisoCaja(error.message);
    mostrarAlerta(error.message);
    pintarEstado(obtenerEstadoActual(), obtenerCatalogoMedicamentos());
  } finally {
    toggleBotonesAccion(false);
  }
}

/**
 * Confirma la venta, guarda la transacción y redirige al comprobante de pago.
 */
export async function manejarConfirmar() {
  limpiarAlerta();
  const selectCliente = document.getElementById("select-cliente");
  const idCliente = selectCliente ? selectCliente.value : "";
  toggleBotonesAccion(true);
  try {
    const resultado = await confirmar(idCliente);
    window.location.assign(`/ventas/${encodeURIComponent(resultado.idVenta)}`);
    return;
  } catch (error) {
    mostrarAlerta(error.message);
    try {
      pintarEstado(await cargar(), obtenerCatalogoMedicamentos());
    } catch (e) {
      // Conservar alerta original
    }
  }
  toggleBotonesAccion(false);
}

// ============================================================================
// Configuración de Eventos y Delegación
// ============================================================================
export function configurarEventos() {
  const formAgregar = document.getElementById("formulario-agregar");
  const botonAgregar = document.getElementById("boton-agregar");
  const botonDeshacer = document.getElementById("boton-deshacer");
  const botonConfirmar = document.getElementById("boton-confirmar");
  const cuerpoLineas = document.getElementById("cuerpo-lineas");
  const selectMedicamento = document.getElementById("select-medicamento");
  const inputCantidad = document.getElementById("input-cantidad");

  if (formAgregar) formAgregar.addEventListener("submit", (e) => { void manejarAgregar(e); });
  if (botonAgregar) botonAgregar.addEventListener("click", (e) => { void manejarAgregar(e); });
  if (botonDeshacer) botonDeshacer.addEventListener("click", () => { void manejarDeshacer(); });
  if (botonConfirmar) botonConfirmar.addEventListener("click", () => { void manejarConfirmar(); });

  // Delegación de eventos en tabla de canasta
  if (cuerpoLineas) {
    cuerpoLineas.addEventListener("change", (evento) => {
      const campo = evento.target.closest(".input-descuento");
      if (campo && campo.dataset.id) void manejarDescuento(campo);
    });

    cuerpoLineas.addEventListener("click", (evento) => {
      // Clic en editar descuento: abre modal flotante desacoplado (reemplaza prompt)
      const btnEditDesc = evento.target.closest(".btn-editar-descuento");
      if (btnEditDesc && btnEditDesc.dataset.id) {
        const id = btnEditDesc.dataset.id;
        const actualDesc = Number.parseFloat(btnEditDesc.dataset.descuento || "0") || 0;
        const subtotal = Number.parseFloat(btnEditDesc.dataset.subtotal || "0") || 0;
        const precioUnitario = Number.parseFloat(btnEditDesc.dataset.precioUnitario || "0") || 0;
        const cantidad = Number.parseInt(btnEditDesc.dataset.cantidad || "1", 10) || 1;
        const nombre = btnEditDesc.dataset.nombre || "este medicamento";

        abrirModalDescuento({
          idMedicamento: id,
          nombre,
          subtotal,
          precioUnitario,
          cantidad,
          descuentoActual: actualDesc,
          onAplicar: (nuevoDescuentoSoles) => {
            void ejecutarDescuentoDirecto(id, nuevoDescuentoSoles);
          },
        });
        return;
      }

      // Clic en stepper down (-1)
      const btnDown = evento.target.closest(".btn-step-down");
      if (btnDown && btnDown.dataset.id) {
        const id = btnDown.dataset.id;
        const cant = Number.parseInt(btnDown.dataset.cant, 10);
        if (cant > 1) {
          void manejarModificarCantidad(id, cant - 1);
        } else {
          void manejarRetirar(id);
        }
        return;
      }

      // Clic en stepper up (+1)
      const btnUp = evento.target.closest(".btn-step-up");
      if (btnUp && btnUp.dataset.id) {
        const id = btnUp.dataset.id;
        const cant = Number.parseInt(btnUp.dataset.cant, 10);
        void manejarModificarCantidad(id, cant + 1);
        return;
      }

      // Clic en botón eliminar línea
      const boton = evento.target.closest(".boton-retirar, button[title*='Eliminar']");
      if (boton && boton.dataset.id) {
        void manejarRetirar(boton.dataset.id);
      }
    });
  }

  // Limpieza de alerta al interactuar con campos del formulario tradicional
  if (selectMedicamento) selectMedicamento.addEventListener("change", limpiarAlerta);
  if (inputCantidad) inputCantidad.addEventListener("input", limpiarAlerta);

  // Inicializar submódulos especializados
  inicializarBuscadorMedicamentos({
    onSeleccionar: (id, cant) => { void agregarProductoDirecto(id, cant); },
    obtenerCatalogo: obtenerCatalogoMedicamentos,
    mostrarAviso: mostrarAvisoCaja,
  });

  inicializarBuscadorClientes({
    obtenerCatalogoClientes,
  });

  configurarSelectorPago();

  configurarBotonCancelar({
    onCancelar: async () => {
      toggleBotonesAccion(true);
      try {
        const nuevoEstado = await vaciarPreparacion();
        pintarEstado(nuevoEstado, obtenerCatalogoMedicamentos());
        mostrarAvisoCaja("Venta cancelada. Canasta de mostrador vaciada.");
        const inputBuscar = document.getElementById("buscar-caja");
        if (inputBuscar) {
          inputBuscar.value = "";
          inputBuscar.focus();
        }
      } catch (error) {
        mostrarAvisoCaja(error.message);
        mostrarAlerta(error.message);
        throw error;
      } finally {
        toggleBotonesAccion(false);
      }
    },
    getTieneLineas: () => {
      const actual = obtenerEstadoActual();
      return !!(actual && actual.detalles && actual.detalles.length > 0);
    },
    mostrarAviso: mostrarAvisoCaja,
  });

  configurarAtajosTeclado({
    onConfirmar: () => { void manejarConfirmar(); },
  });

  inicializarModalDescuento();
}

/**
 * Inicialización asíncrona al cargar el DOM.
 */
export async function inicializar() {
  configurarEventos();
  actualizarSugerencias("", obtenerCatalogoMedicamentos(), (id, cant) => { void agregarProductoDirecto(id, cant); });
  try {
    const estado = await cargar();
    pintarEstado(estado, obtenerCatalogoMedicamentos());
  } catch (error) {
    mostrarAlerta(error.message);
  }
}

// Autoarranque seguro
if (typeof document !== "undefined") {
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => { void inicializar(); });
  } else {
    void inicializar();
  }
}

// ============================================================================
// Fachada Global en window (100% Compatibilidad hacia atrás)
// ============================================================================
if (typeof window !== "undefined") {
  Object.assign(window, {
    cargar,
    agregar,
    retirar,
    vaciarPreparacion,
    modificarCantidad,
    deshacer,
    confirmar,
    aplicarDescuento,
    manejarDescuento,
    manejarConfirmar,
    pintarEstado: (st) => pintarEstado(st, obtenerCatalogoMedicamentos()),
    formatearMoneda,
    formatearFecha,
    mostrarAlerta,
    limpiarAlerta,
    manejarAgregar,
    manejarRetirar,
    manejarModificarCantidad,
    manejarDeshacer,
    toggleBotonesAccion,
    inicializar,
    peticionPreparacion: peticion,
    SesionExpiradaError,
    MENSAJE_SESION_EXPIRADA,
    actualizarSugerencias: (term) =>
      actualizarSugerencias(term, obtenerCatalogoMedicamentos(), agregarProductoDirecto),
    agregarProductoDirecto,
    mostrarAvisoCaja,
    ejecutarDescuentoDirecto,
    seleccionarCliente,
    actualizarSugerenciasCliente: (term) =>
      actualizarSugerenciasCliente(term, obtenerCatalogoClientes()),
    obtenerCsrf,
    abrirModalDescuento,
    cerrarModalDescuento,
  });
}
