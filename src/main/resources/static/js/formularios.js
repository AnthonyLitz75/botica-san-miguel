/**
 * formularios.js — Utilidades de usabilidad, micro-interacciones y prevención de doble envío.
 * Botica San Miguel (AyE)
 * Refactorizado: Consume core/validador-docs.js (Principio DRY)
 */

document.addEventListener("DOMContentLoaded", async () => {
  // Carga modular dinámica del núcleo de validación
  const { limpiarDigitos, obtenerConfiguracionDoc } = await import("./core/validador-docs.js");

  // -------------------------------------------------------------
  // 1. Dinamismo reactivo para DNI / RUC (Directorio de Clientes)
  // -------------------------------------------------------------
  const selectTipoDoc = document.getElementById("tipoDocumento");
  const inputNumeroDoc = document.getElementById("numeroDocumento");
  const ayudaDoc = document.getElementById("ayuda-documento");

  if (selectTipoDoc && inputNumeroDoc) {
    function sincronizarTipoDocumento() {
      const config = obtenerConfiguracionDoc(selectTipoDoc.value);

      // Reiniciar minLength primero a 0 para prevenir IndexSizeError en navegadores
      inputNumeroDoc.minLength = 0;
      inputNumeroDoc.maxLength = config.longitud;
      inputNumeroDoc.minLength = config.longitud;
      inputNumeroDoc.placeholder = config.placeholder;

      if (ayudaDoc) {
        ayudaDoc.innerHTML = `<span class="text-emerald-700 font-medium">${config.mensajeAyuda}</span>`;
      }

      // Si al cambiar excede la longitud del nuevo tipo, recortar
      if (inputNumeroDoc.value.length > config.longitud) {
        inputNumeroDoc.value = inputNumeroDoc.value.slice(0, config.longitud);
      }
    }

    selectTipoDoc.addEventListener("change", sincronizarTipoDocumento);
    selectTipoDoc.addEventListener("input", sincronizarTipoDocumento);
    sincronizarTipoDocumento();

    // Sanitización centralizada: admitir estrictamente dígitos numéricos
    inputNumeroDoc.addEventListener("input", (e) => {
      const valorLimpio = limpiarDigitos(e.target.value);
      if (e.target.value !== valorLimpio) {
        e.target.value = valorLimpio;
      }
    });
  }

  // -------------------------------------------------------------
  // 2. Prevención de doble envío y estado de carga visual
  // -------------------------------------------------------------
  document.querySelectorAll("form").forEach((form) => {
    // Ignorar formularios de búsqueda tipo GET
    if (form.method && form.method.toUpperCase() === "GET") return;

    form.addEventListener("submit", (e) => {
      if (e.defaultPrevented || !form.checkValidity()) return;

      const submitBtn = form.querySelector('button[type="submit"]');
      if (submitBtn && !submitBtn.disabled) {
        const anchoOriginal = submitBtn.offsetWidth;
        const htmlOriginal = submitBtn.innerHTML;

        // Deferir la deshabilitación para que el navegador procese el submit
        setTimeout(() => {
          submitBtn.disabled = true;
          submitBtn.style.minWidth = `${anchoOriginal}px`;
          submitBtn.classList.add("opacity-80", "cursor-wait");
          submitBtn.innerHTML = `
            <svg class="animate-spin -ml-1 mr-2 h-4 w-4 inline-block text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
            </svg>
            <span>Guardando...</span>
          `;
        }, 0);

        // Salvaguarda: rehabilitar a los 10 segundos si la petición no navegó
        setTimeout(() => {
          if (submitBtn && submitBtn.disabled) {
            submitBtn.disabled = false;
            submitBtn.classList.remove("opacity-80", "cursor-wait");
            submitBtn.innerHTML = htmlOriginal;
          }
        }, 10000);
      }
    });
  });

  // Restaurar botones si el usuario navega con atrás/adelante en el historial (bfcache)
  window.addEventListener("pageshow", () => {
    document.querySelectorAll('button[type="submit"]').forEach((btn) => {
      btn.disabled = false;
      btn.classList.remove("opacity-80", "cursor-wait");
    });
  });

  // -------------------------------------------------------------
  // 3. Foco accesible en el primer campo con error al cargar
  // -------------------------------------------------------------
  const primerError = document.querySelector('[aria-invalid="true"], .inp.err input, .inp.err select');
  if (primerError) {
    setTimeout(() => primerError.focus(), 50);
  }
});
