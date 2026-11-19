/**
 * modulos/pos-api.js — Cliente REST para preparación de venta (Punto de Venta / Caja)
 * Botica San Miguel (AyE)
 * Consume core/api-cliente.js
 */

import { peticion, obtenerCsrf, SesionExpiradaError, MENSAJE_SESION_EXPIRADA } from "../core/api-cliente.js";

/**
 * Obtiene el estado activo de preparación de la venta desde la sesión.
 * @returns {Promise<Object>}
 */
export async function cargar() {
  return peticion("/ventas/preparacion", { method: "GET" });
}

/**
 * Agrega un medicamento y cantidad a la preparación activa (FEFO).
 * @param {number|string} idMedicamento 
 * @param {number|string} cantidad 
 * @returns {Promise<Object>}
 */
export async function agregar(idMedicamento, cantidad) {
  return peticion("/ventas/preparacion", {
    method: "POST",
    body: JSON.stringify({
      idMedicamento: Number(idMedicamento),
      cantidad: Number(cantidad),
    }),
  });
}

/**
 * Retira un medicamento completo de la preparación.
 * @param {number|string} idMedicamento 
 * @returns {Promise<Object>}
 */
export async function retirar(idMedicamento) {
  return peticion(`/ventas/preparacion/${encodeURIComponent(idMedicamento)}`, {
    method: "DELETE",
  });
}

/**
 * Vacía toda la preparación activa del vendedor en sesión.
 * @returns {Promise<Object>}
 */
export async function vaciarPreparacion() {
  return peticion("/ventas/preparacion", {
    method: "DELETE",
  });
}

/**
 * Modifica directamente la cantidad de una línea ya preparada (stepper + / -).
 * @param {number|string} idMedicamento 
 * @param {number|string} cantidad 
 * @returns {Promise<Object>}
 */
export async function modificarCantidad(idMedicamento, cantidad) {
  return peticion(
    `/ventas/preparacion/${encodeURIComponent(idMedicamento)}/cantidad`,
    {
      method: "PUT",
      body: JSON.stringify({ cantidad: Number(cantidad) }),
    }
  );
}

/**
 * Reversión LIFO del último cambio en la preparación (Deshacer con Pila).
 * @returns {Promise<Object>}
 */
export async function deshacer() {
  return peticion("/ventas/preparacion/deshacer", {
    method: "POST",
  });
}

/**
 * Fija el descuento en soles para una línea de producto (spec 004).
 * @param {number|string} idMedicamento 
 * @param {number|string} descuento 
 * @returns {Promise<Object>}
 */
export async function aplicarDescuento(idMedicamento, descuento) {
  return peticion(
    `/ventas/preparacion/${encodeURIComponent(idMedicamento)}/descuento`,
    {
      method: "PUT",
      body: JSON.stringify({ descuento: descuento }),
    }
  );
}

/**
 * Confirma la venta y genera el comprobante interno (spec 003, spec 016).
 * @param {number|string|null} idCliente Vacío o null = "Público general"
 * @param {string} [metodoPago="EFECTIVO"] Método de pago (EFECTIVO, YAPE_PLIN, TARJETA)
 * @returns {Promise<{ idVenta: number, numeroComprobante: string }>}
 */
export async function confirmar(idCliente, metodoPago = "EFECTIVO") {
  return peticion("/ventas/confirmar", {
    method: "POST",
    body: JSON.stringify({
      idCliente: idCliente ? Number(idCliente) : null,
      metodoPago: metodoPago || "EFECTIVO",
    }),
  });
}

export { peticion, obtenerCsrf, SesionExpiradaError, MENSAJE_SESION_EXPIRADA };
