/**
 * core/validador-docs.js — Módulo Centralizado de Validación de Documentos de Identidad (DNI y RUC)
 * Botica San Miguel (AyE)
 * Principio DRY: Fuente única de verdad para validaciones en formularios y ventanas modales.
 */

/**
 * Sanitiza una cadena permitiendo estrictamente dígitos numéricos (0-9).
 * @param {string} valor 
 * @returns {string}
 */
export function limpiarDigitos(valor) {
  return String(valor || "").replace(/\D/g, "");
}

/**
 * Obtiene la configuración y reglas de longitud según el tipo de documento.
 * @param {string} tipoDocumento 'DNI' | 'RUC'
 * @returns {{ longitud: number, esRuc: boolean, placeholder: string, mensajeAyuda: string }}
 */
export function obtenerConfiguracionDoc(tipoDocumento) {
  const esRuc = String(tipoDocumento || "").toUpperCase() === "RUC";
  const longitud = esRuc ? 11 : 8;

  return {
    longitud,
    esRuc,
    placeholder: esRuc
      ? "11 dígitos numéricos (ej. 20123456789)"
      : "8 dígitos numéricos (ej. 72345678)",
    mensajeAyuda: esRuc
      ? "RUC: requiere exactamente 11 dígitos numéricos."
      : "DNI: requiere exactamente 8 dígitos numéricos."
  };
}

/**
 * Valida un número de documento según el tipo especificado.
 * @param {string} valor 
 * @param {'DNI' | 'RUC'} tipo 
 * @returns {{ valido: boolean, vacio: boolean, longitudActual: number, longitudEsperada: number, mensaje: string }}
 */
export function validarDocumento(valor, tipo) {
  const cfg = obtenerConfiguracionDoc(tipo);
  const textoLimpio = limpiarDigitos(valor);
  const vacio = textoLimpio.length === 0;

  if (vacio) {
    return {
      valido: false,
      vacio: true,
      longitudActual: 0,
      longitudEsperada: cfg.longitud,
      mensaje: `${tipo}: ${cfg.longitud} dígitos numéricos.`
    };
  }

  const esValido = textoLimpio.length === cfg.longitud;
  return {
    valido: esValido,
    vacio: false,
    longitudActual: textoLimpio.length,
    longitudEsperada: cfg.longitud,
    mensaje: esValido
      ? `${cfg.longitud} dígitos · validado`
      : `El ${tipo} debe tener ${cfg.longitud} dígitos numéricos (ingresaste ${textoLimpio.length}).`
  };
}

/**
 * Helper para sincronizar clases visuales de error/éxito (.err, .ok2) y textos de ayuda en el DOM.
 * @param {HTMLInputElement} inputElement 
 * @param {HTMLElement|null} contenedorHelp 
 * @param {'DNI' | 'RUC'} tipo 
 */
export function aplicarEstadoVisualDocumento(inputElement, contenedorHelp, tipo) {
  if (!inputElement) return;

  const resultado = validarDocumento(inputElement.value, tipo);
  const contenedorInp = inputElement.closest(".inp") || inputElement;

  if (resultado.vacio) {
    contenedorInp.classList.remove("err", "ok2");
    if (contenedorHelp) {
      contenedorHelp.className = "help";
      contenedorHelp.textContent = `${resultado.longitudEsperada} dígitos numéricos`;
    }
  } else if (!resultado.valido) {
    contenedorInp.classList.add("err");
    contenedorInp.classList.remove("ok2");
    if (contenedorHelp) {
      contenedorHelp.className = "help e";
      contenedorHelp.textContent = resultado.mensaje;
    }
  } else {
    contenedorInp.classList.remove("err");
    contenedorInp.classList.add("ok2");
    if (contenedorHelp) {
      contenedorHelp.className = "help";
      contenedorHelp.textContent = resultado.mensaje;
    }
  }
}

// Compatibilidad hacia atrás para navegadores que cargan sin type="module"
if (typeof window !== "undefined") {
  window.ValidadorDocs = {
    limpiarDigitos,
    obtenerConfiguracionDoc,
    validarDocumento,
    aplicarEstadoVisualDocumento
  };
}
