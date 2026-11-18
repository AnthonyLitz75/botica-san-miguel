/**
 * pos-modal-descuento.js — Componente Modal Flotante de Descuentos para Caja POS
 * Botica San Miguel (AyE)
 * 
 * Permite aplicar descuentos por Monto fijo (S/) o por Porcentaje (%) a una
 * línea seleccionada de la canasta, con vista previa reactiva en tiempo real,
 * validación estricta de límites (no negativo, no superior al subtotal) y
 * control accesible (Escape, Enter, trampeo de foco).
 */

import { formatearMoneda } from "../core/api-cliente.js";

// Estado interno del modal
let contextoActual = null;
let modoActivo = "monto"; // 'monto' | 'porcentaje'

/**
 * Elementos del DOM cacheados para máxima eficiencia
 */
let dom = null;

function obtenerElementos() {
  if (dom) return dom;
  dom = {
    backdrop: document.getElementById("modal-descuento-backdrop"),
    dialogo: document.getElementById("modal-descuento-dialogo"),
    btnCerrar: document.getElementById("modal-descuento-cerrar"),
    tabMonto: document.getElementById("tab-descuento-monto"),
    tabPorcentaje: document.getElementById("tab-descuento-porcentaje"),
    labelInput: document.getElementById("label-input-descuento"),
    inputValor: document.getElementById("input-valor-descuento"),
    indicadorUnidad: document.getElementById("indicador-unidad-descuento"),
    cajaPrevia: document.getElementById("caja-previa-descuento"),
    textoPreviaDesc: document.getElementById("texto-previa-descuento"),
    textoPreviaTotal: document.getElementById("texto-previa-nuevo-total"),
    mensajeError: document.getElementById("mensaje-error-descuento"),
    btnCancelar: document.getElementById("btn-cancelar-descuento"),
    btnAplicar: document.getElementById("btn-aplicar-descuento"),
    nombreMedicamento: document.getElementById("modal-descuento-producto-nombre"),
  };
  return dom;
}

/**
 * Alterna visualmente la pestaña activa entre 'monto' y 'porcentaje'.
 * @param {'monto' | 'porcentaje'} nuevoModo 
 */
function cambiarModo(nuevoModo) {
  if (modoActivo === nuevoModo) return;
  const els = obtenerElementos();
  if (!els.backdrop) return;

  const valorActual = Number.parseFloat(els.inputValor.value) || 0;
  const subtotal = contextoActual ? contextoActual.subtotal : 0;

  modoActivo = nuevoModo;

  if (nuevoModo === "monto") {
    // Convertir de porcentaje a monto
    const montoCalculado = Math.min(subtotal, (subtotal * (valorActual / 100)));
    els.labelInput.textContent = "Monto del descuento (S/)";
    els.indicadorUnidad.textContent = "S/";
    els.inputValor.value = montoCalculado.toFixed(2);
    els.inputValor.placeholder = "0.00";

    // Estilos tabs
    els.tabMonto.classList.add("tab-activa");
    els.tabMonto.classList.remove("tab-inactiva");
    els.tabPorcentaje.classList.remove("tab-activa");
    els.tabPorcentaje.classList.add("tab-inactiva");
  } else {
    // Convertir de monto a porcentaje
    let pctCalculado = 0;
    if (subtotal > 0) {
      pctCalculado = Math.min(100, Math.max(0, (valorActual / subtotal) * 100));
    }
    els.labelInput.textContent = "Porcentaje de descuento (%)";
    els.indicadorUnidad.textContent = "%";
    // Si es entero mostrar sin decimales, sino con hasta 2 decimales
    els.inputValor.value = Number.isInteger(pctCalculado)
      ? String(pctCalculado)
      : pctCalculado.toFixed(2);
    els.inputValor.placeholder = "0";

    // Estilos tabs
    els.tabPorcentaje.classList.add("tab-activa");
    els.tabPorcentaje.classList.remove("tab-inactiva");
    els.tabMonto.classList.remove("tab-activa");
    els.tabMonto.classList.add("tab-inactiva");
  }

  recalcularVistaPrevia();
  els.inputValor.focus();
  els.inputValor.select();
}

/**
 * Calcula el descuento en soles y el nuevo total con base en la entrada actual.
 * Actualiza la vista previa y valida límites de negocio.
 * @returns {{ esValido: boolean, descuentoFinalSoles: number }}
 */
function recalcularVistaPrevia() {
  const els = obtenerElementos();
  if (!els.backdrop || !contextoActual) {
    return { esValido: false, descuentoFinalSoles: 0 };
  }

  const subtotal = contextoActual.subtotal;
  const texto = els.inputValor.value.trim();
  const valor = Number.parseFloat(texto);

  let esValido = true;
  let mensaje = "";
  let descuentoSoles = 0;

  if (texto === "" || Number.isNaN(valor)) {
    descuentoSoles = 0;
  } else if (valor < 0) {
    esValido = false;
    mensaje = "El descuento no puede ser un valor negativo.";
  } else if (modoActivo === "monto") {
    if (valor > subtotal + 0.0001) {
      esValido = false;
      mensaje = `El descuento no puede superar el subtotal (${formatearMoneda(subtotal)}).`;
    } else {
      descuentoSoles = valor;
    }
  } else {
    // modo porcentaje
    if (valor > 100) {
      esValido = false;
      mensaje = "El porcentaje de descuento no puede ser mayor a 100%.";
    } else {
      descuentoSoles = (subtotal * (valor / 100));
    }
  }

  // Redondear a 2 decimales
  descuentoSoles = Math.round(descuentoSoles * 100) / 100;
  if (descuentoSoles > subtotal) {
    descuentoSoles = subtotal;
  }
  const nuevoTotal = Math.max(0, subtotal - descuentoSoles);

  // Actualizar textos informativos
  if (els.textoPreviaDesc) {
    els.textoPreviaDesc.textContent = `Se aplicará un descuento de ${formatearMoneda(descuentoSoles)}`;
  }
  if (els.textoPreviaTotal) {
    els.textoPreviaTotal.textContent = `Nuevo total: ${formatearMoneda(nuevoTotal)}`;
  }

  // Manejo de mensajes de error y estado del botón aplicar
  if (els.mensajeError) {
    if (!esValido) {
      els.mensajeError.textContent = mensaje;
      els.mensajeError.classList.remove("hidden");
      els.mensajeError.style.display = "block";
    } else {
      els.mensajeError.textContent = "";
      els.mensajeError.classList.add("hidden");
      els.mensajeError.style.display = "none";
    }
  }

  if (els.cajaPrevia) {
    if (!esValido) {
      els.cajaPrevia.classList.add("caja-previa-error");
    } else {
      els.cajaPrevia.classList.remove("caja-previa-error");
    }
  }

  if (els.btnAplicar) {
    els.btnAplicar.disabled = !esValido;
  }

  return { esValido, descuentoFinalSoles: descuentoSoles };
}

/**
 * Aplica el descuento confirmado y ejecuta la acción hacia el orquestador.
 */
function confirmarYAplicar() {
  const { esValido, descuentoFinalSoles } = recalcularVistaPrevia();
  if (!esValido || !contextoActual) return;

  const callback = contextoActual.onAplicar;
  cerrarModalDescuento();

  if (typeof callback === "function") {
    callback(descuentoFinalSoles.toFixed(2));
  }
}

/**
 * Abre el modal de descuentos con la configuración de la línea seleccionada.
 * @param {Object} opciones
 * @param {number|string} opciones.idMedicamento
 * @param {string} opciones.nombre
 * @param {number} opciones.subtotal
 * @param {number} [opciones.precioUnitario=0]
 * @param {number} [opciones.cantidad=1]
 * @param {number} [opciones.descuentoActual=0]
 * @param {(descuentoFinal: string) => void} opciones.onAplicar
 */
export function abrirModalDescuento(opciones) {
  const els = obtenerElementos();
  if (!els.backdrop) return;

  const nombreTexto = (opciones && opciones.nombre && typeof opciones.nombre === "string" && opciones.nombre.trim().length > 0)
    ? opciones.nombre.trim()
    : "este medicamento";

  contextoActual = {
    idMedicamento: opciones ? opciones.idMedicamento : null,
    nombre: nombreTexto,
    subtotal: Number(opciones ? opciones.subtotal : 0) || 0,
    precioUnitario: Number(opciones ? opciones.precioUnitario : 0) || 0,
    cantidad: Number(opciones ? opciones.cantidad : 1) || 1,
    descuentoActual: Number(opciones ? opciones.descuentoActual : 0) || 0,
    onAplicar: opciones ? opciones.onAplicar : null,
  };

  // Restablecer modo inicial por defecto: Monto (CA-3)
  modoActivo = "monto";
  els.tabMonto.classList.add("tab-activa");
  els.tabMonto.classList.remove("tab-inactiva");
  els.tabPorcentaje.classList.remove("tab-activa");
  els.tabPorcentaje.classList.add("tab-inactiva");

  els.labelInput.textContent = "Monto del descuento (S/)";
  els.indicadorUnidad.textContent = "S/";
  els.inputValor.placeholder = "0.00";
  els.inputValor.value = contextoActual.descuentoActual.toFixed(2);

  if (els.nombreMedicamento) {
    els.nombreMedicamento.textContent = contextoActual.nombre;
  }

  recalcularVistaPrevia();

  // Mostrar modal accesible
  els.backdrop.classList.remove("hidden");
  els.backdrop.style.display = "flex";
  document.body.classList.add("modal-abierto");

  // Foco automático en el input
  setTimeout(() => {
    els.inputValor.focus();
    els.inputValor.select();
  }, 50);
}

/**
 * Cierra la ventana modal sin aplicar cambios.
 */
export function cerrarModalDescuento() {
  const els = obtenerElementos();
  if (!els.backdrop) return;

  els.backdrop.classList.add("hidden");
  els.backdrop.style.display = "none";
  document.body.classList.remove("modal-abierto");
  if (els.nombreMedicamento) {
    els.nombreMedicamento.textContent = "este medicamento";
  }
  contextoActual = null;
}

/**
 * Inicializa los escuchadores de eventos del modal de descuento.
 */
export function inicializarModalDescuento() {
  const els = obtenerElementos();
  if (!els.backdrop) return;

  // Clic en pestaña Monto
  if (els.tabMonto) {
    els.tabMonto.addEventListener("click", () => cambiarModo("monto"));
  }

  // Clic en pestaña Porcentaje
  if (els.tabPorcentaje) {
    els.tabPorcentaje.addEventListener("click", () => cambiarModo("porcentaje"));
  }

  // Entrada reactiva en el input
  if (els.inputValor) {
    els.inputValor.addEventListener("input", recalcularVistaPrevia);
    els.inputValor.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        confirmarYAplicar();
      } else if (e.key === "Escape") {
        e.preventDefault();
        cerrarModalDescuento();
      }
    });
  }

  // Botón Aplicar
  if (els.btnAplicar) {
    els.btnAplicar.addEventListener("click", confirmarYAplicar);
  }

  // Botón Cancelar y Botón X
  if (els.btnCancelar) {
    els.btnCancelar.addEventListener("click", cerrarModalDescuento);
  }
  if (els.btnCerrar) {
    els.btnCerrar.addEventListener("click", cerrarModalDescuento);
  }

  // Clic en el backdrop exterior para cerrar
  els.backdrop.addEventListener("click", (e) => {
    if (e.target === els.backdrop) {
      cerrarModalDescuento();
    }
  });

  // Tecla Escape global cuando el modal está abierto
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !els.backdrop.classList.contains("hidden")) {
      cerrarModalDescuento();
    }
  });
}
