/**
 * core/api-cliente.js — Cliente HTTP resiliente, manejo de seguridad CSRF y formateadores
 * Botica San Miguel (AyE)
 */

export const MENSAJE_SESION_EXPIRADA = "Tu sesión expiró. Vuelve a iniciar sesión.";

export class SesionExpiradaError extends Error {
  constructor(mensaje = MENSAJE_SESION_EXPIRADA) {
    super(mensaje);
    this.name = "SesionExpiradaError";
  }
}

/**
 * Obtiene el token CSRF y el nombre del header desde las etiquetas <meta> de Thymeleaf.
 * @returns {{ token: string, headerName: string }}
 */
export function obtenerCsrf() {
  const tokenMeta = document.querySelector('meta[name="_csrf"]');
  const headerMeta = document.querySelector('meta[name="_csrf_header"]');
  return {
    token: tokenMeta ? tokenMeta.getAttribute("content") : "",
    headerName: headerMeta ? headerMeta.getAttribute("content") : "X-CSRF-TOKEN"
  };
}

/**
 * Normaliza una cadena de texto retirando tildes y diacríticos para búsquedas flexibles.
 * @param {string} texto 
 * @returns {string}
 */
export function normalizarTexto(texto) {
  return String(texto || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();
}

/**
 * Formatea un valor numérico a moneda peruana (S/ 0.00).
 * @param {number|string} valor 
 * @returns {string}
 */
export function formatearMoneda(valor) {
  const numero = Number(valor) || 0;
  return `S/ ${numero.toFixed(2)}`;
}

/**
 * Formatea una fecha ISO (YYYY-MM-DD) al formato local (DD/MM/AAAA).
 * @param {string} fechaIso 
 * @returns {string}
 */
export function formatearFecha(fechaIso) {
  if (!fechaIso) return "-";
  const partes = String(fechaIso).split("-");
  if (partes.length === 3) {
    return `${partes[2]}/${partes[1]}/${partes[0]}`;
  }
  return String(fechaIso);
}

/**
 * Realiza una petición HTTP con fetch gestionando CSRF, cabeceras AJAX y expiración de sesión.
 * @param {string} url 
 * @param {RequestInit} opciones 
 * @returns {Promise<any>}
 */
export async function peticion(url, opciones = {}) {
  const csrf = obtenerCsrf();
  const headers = {
    Accept: "application/json",
    "X-Requested-With": "XMLHttpRequest",
    ...(opciones.headers || {})
  };

  const metodo = (opciones.method || "GET").toUpperCase();
  if (metodo !== "GET" && metodo !== "HEAD") {
    if (csrf.token && csrf.headerName) {
      headers[csrf.headerName] = csrf.token;
    }
    if (!headers["Content-Type"] && opciones.body && typeof opciones.body === "string") {
      headers["Content-Type"] = "application/json";
    }
  }

  const respuesta = await fetch(url, {
    ...opciones,
    headers,
    credentials: opciones.credentials || "same-origin"
  });

  const tipoContenido = respuesta.headers.get("content-type") || "";
  const esJson = tipoContenido.includes("application/json");
  const fueAlLogin =
    respuesta.redirected && respuesta.url && respuesta.url.includes("/acceso/login");

  if (fueAlLogin || respuesta.status === 401 || respuesta.status === 403) {
    const esApi = url.includes("/api/");
    if (!esApi && fueAlLogin) {
      window.location.href = respuesta.url;
      return null;
    }
    throw new SesionExpiradaError();
  }

  if (respuesta.ok) {
    if (esJson) {
      return await respuesta.json();
    }
    const cuerpo = await respuesta.text();
    if (cuerpo && cuerpo.includes('class="pagina-login"')) {
      throw new SesionExpiradaError();
    }
    try {
      return JSON.parse(cuerpo);
    } catch {
      return cuerpo;
    }
  }

  let mensaje = `Error del servidor (${respuesta.status})`;
  if (esJson) {
    try {
      const datosError = await respuesta.json();
      if (datosError && (datosError.mensaje || datosError.error)) {
        mensaje = datosError.mensaje || datosError.error;
      }
    } catch {}
  } else {
    try {
      const texto = await respuesta.text();
      if (texto && texto.length < 200) mensaje = texto;
    } catch {}
  }

  const errorHttp = new Error(mensaje);
  errorHttp.status = respuesta.status;
  throw errorHttp;
}

// Compatibilidad global
if (typeof window !== "undefined") {
  window.ApiClient = {
    MENSAJE_SESION_EXPIRADA,
    SesionExpiradaError,
    obtenerCsrf,
    normalizarTexto,
    formatearMoneda,
    formatearFecha,
    peticion
  };
}
