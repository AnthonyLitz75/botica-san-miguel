/**
 * modulos/pos-atajos.js — Atajos ergonómicos de teclado (F2, F4, F9), selector de pago y cancelación
 * Botica San Miguel (AyE)
 * Optimizado para velocidad de mostrador de farmacia.
 */

/**
 * Configura los atajos globales de teclado para el mostrador:
 * - F2: Enfocar buscador de medicamentos
 * - F4: Enfocar buscador de clientes
 * - F9: Cobrar y confirmar venta
 * @param {Object} opciones
 * @param {function(): void} opciones.onConfirmar
 */
export function configurarAtajosTeclado({ onConfirmar }) {
  document.addEventListener("keydown", (e) => {
    if (e.key === "F2") {
      e.preventDefault();
      const inputBuscar = document.getElementById("buscar-caja");
      if (inputBuscar) {
        inputBuscar.focus();
        inputBuscar.select();
      }
    } else if (e.key === "F4") {
      e.preventDefault();
      const inputCli = document.getElementById("cliente-caja");
      if (inputCli) {
        inputCli.focus();
        inputCli.select();
      }
    } else if (e.key === "F9") {
      e.preventDefault();
      const btnConfirmar = document.getElementById("boton-confirmar");
      if (btnConfirmar && !btnConfirmar.disabled && typeof onConfirmar === "function") {
        void onConfirmar();
      }
    }
  });
}

/**
 * Configura el selector visual segmentado de método de pago (.seg).
 */
export function configurarSelectorPago() {
  const selectorPago = document.getElementById("selector-pago");
  if (!selectorPago) return;

  selectorPago.addEventListener("click", (e) => {
    const opcion = e.target.closest("div[data-metodo]");
    if (opcion) {
      selectorPago
        .querySelectorAll("div[data-metodo]")
        .forEach((div) => div.classList.remove("on"));
      opcion.classList.add("on");
    }
  });
}

/**
 * Configura el botón de cancelación de la venta activa y vaciado de canasta.
 * Reemplaza el uso de window.confirm() por el diálogo modal accesible #modal-cancelar-venta-backdrop.
 * @param {Object} opciones
 * @param {function(): Promise<void>} opciones.onCancelar
 * @param {function(): boolean} opciones.getTieneLineas
 * @param {function(string): void} opciones.mostrarAviso
 */
export function configurarBotonCancelar({ onCancelar, getTieneLineas, mostrarAviso }) {
  const btnCancelarVenta = document.getElementById("btn-cancelar-venta");
  const backdrop = document.getElementById("modal-cancelar-venta-backdrop");
  const dialogo = document.getElementById("modal-cancelar-venta-dialogo");
  const btnCerrar = document.getElementById("modal-cancelar-venta-cerrar");
  const btnAbortar = document.getElementById("btn-abortar-cancelacion");
  const btnConfirmar = document.getElementById("btn-confirmar-cancelar-venta");
  const elErrorModal = document.getElementById("modal-cancelar-error");

  if (!btnCancelarVenta) return;

  let ultimoDisparador = null;

  const esElementoEnfocable = (el) => {
    if (!el || typeof el.focus !== "function") return false;
    if (!document.contains(el)) return false;
    if (el.disabled) return false;
    if (el.hasAttribute("hidden") || el.getAttribute("aria-hidden") === "true") return false;
    const estilo = window.getComputedStyle ? window.getComputedStyle(el) : el.style;
    if (estilo && (estilo.display === "none" || estilo.visibility === "hidden")) return false;
    return true;
  };

  const abrirModalCancelar = (disparador = null) => {
    if (!backdrop) return;
    ultimoDisparador = disparador || document.activeElement || btnCancelarVenta;
    backdrop.classList.remove("hidden");
    backdrop.style.display = "flex";
    document.body.classList.add("modal-abierto");
    if (elErrorModal) {
      elErrorModal.textContent = "";
      elErrorModal.style.display = "none";
    }
    if (btnConfirmar) {
      btnConfirmar.disabled = false;
    }
    if (btnAbortar) {
      btnAbortar.disabled = false;
      setTimeout(() => btnAbortar.focus(), 50);
    }
    if (btnCerrar) {
      btnCerrar.disabled = false;
    }
  };

  const cerrarModalCancelar = (opciones = {}) => {
    if (!backdrop) return;
    backdrop.classList.add("hidden");
    backdrop.style.display = "none";
    document.body.classList.remove("modal-abierto");
    if (elErrorModal) {
      elErrorModal.textContent = "";
      elErrorModal.style.display = "none";
    }
    if (btnConfirmar) {
      btnConfirmar.disabled = false;
    }
    if (btnAbortar) {
      btnAbortar.disabled = false;
    }
    if (btnCerrar) {
      btnCerrar.disabled = false;
    }

    const debeRestaurar =
      opciones === false || (typeof opciones === "object" && opciones !== null && opciones.restaurarFoco === false)
        ? false
        : true;

    if (debeRestaurar) {
      const elementoRestaurar = ultimoDisparador || btnCancelarVenta;
      if (esElementoEnfocable(elementoRestaurar)) {
        elementoRestaurar.focus();
      } else {
        const inputBuscar = document.getElementById("buscar-caja");
        if (esElementoEnfocable(inputBuscar)) {
          inputBuscar.focus();
        }
      }
    }

    ultimoDisparador = null;
  };

  // Clic en el botón principal "Cancelar venta" de la barra de acciones
  btnCancelarVenta.addEventListener("click", () => {
    const tieneLineas = typeof getTieneLineas === "function" ? getTieneLineas() : false;

    if (tieneLineas) {
      abrirModalCancelar(btnCancelarVenta);
    } else if (typeof mostrarAviso === "function") {
      mostrarAviso("La canasta ya se encuentra vacía.");
    }
  });

  // Botón "Continuar venta" (abortar cancelación)
  if (btnAbortar) {
    btnAbortar.addEventListener("click", () => cerrarModalCancelar({ restaurarFoco: true }));
  }

  // Botón X de cierre
  if (btnCerrar) {
    btnCerrar.addEventListener("click", () => cerrarModalCancelar({ restaurarFoco: true }));
  }

  // Clic en la zona exterior del backdrop para cerrar (sin cerrar si se hace clic dentro del diálogo)
  if (backdrop) {
    backdrop.addEventListener("click", (e) => {
      if (e.target === backdrop) {
        cerrarModalCancelar({ restaurarFoco: true });
      }
    });
  }

  // Tecla Escape global cuando el modal está abierto
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && backdrop && !backdrop.classList.contains("hidden")) {
      cerrarModalCancelar({ restaurarFoco: true });
    }
  });

  // Trampa de foco (Focus Trap) para tecla Tab y Shift+Tab dentro del modal de cancelación (OBS-D2B-01)
  document.addEventListener("keydown", (e) => {
    if (e.key !== "Tab") return;
    if (!backdrop || backdrop.classList.contains("hidden") || backdrop.style.display === "none") return;

    const elementosEnfocables = Array.from(
      (dialogo || backdrop).querySelectorAll(
        'button:not([disabled]), [href], input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
    );

    if (elementosEnfocables.length === 0) {
      e.preventDefault();
      return;
    }

    const primerElemento = elementosEnfocables[0];
    const ultimoElemento = elementosEnfocables[elementosEnfocables.length - 1];

    if (e.shiftKey) {
      if (document.activeElement === primerElemento || !(dialogo || backdrop).contains(document.activeElement)) {
        e.preventDefault();
        ultimoElemento.focus();
      }
    } else {
      if (document.activeElement === ultimoElemento || !(dialogo || backdrop).contains(document.activeElement)) {
        e.preventDefault();
        primerElemento.focus();
      }
    }
  });

  // Botón "Sí, cancelar venta" (confirmar y vaciar canasta)
  if (btnConfirmar) {
    btnConfirmar.addEventListener("click", async () => {
      if (btnConfirmar.disabled) return;
      btnConfirmar.disabled = true;
      if (btnAbortar) btnAbortar.disabled = true;
      if (btnCerrar) btnCerrar.disabled = true;

      try {
        if (elErrorModal) {
          elErrorModal.textContent = "";
          elErrorModal.style.display = "none";
        }
        if (typeof onCancelar === "function") {
          await onCancelar();
        }
        cerrarModalCancelar({ restaurarFoco: false });
      } catch (error) {
        btnConfirmar.disabled = false;
        if (btnAbortar) btnAbortar.disabled = false;
        if (btnCerrar) btnCerrar.disabled = false;
        if (elErrorModal) {
          elErrorModal.textContent = error.message || "Error al cancelar la venta. Intenta nuevamente.";
          elErrorModal.style.display = "block";
        }
        // El modal permanece abierto ante error para permitir reintentar o abortar
      }
    });
  }
}
