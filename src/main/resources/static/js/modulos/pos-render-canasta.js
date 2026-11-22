/**
 * modulos/pos-render-canasta.js — Renderizado seguro del DOM para la canasta de venta (Punto de Venta)
 * Botica San Miguel (AyE)
 * Renderizado sin innerHTML arbitrario en contenido de usuario (RNF-002, CA-2.4).
 */

import { formatearMoneda, MENSAJE_SESION_EXPIRADA, obtenerCsrf } from "../core/api-cliente.js";

let estadoActual = null;

/**
 * Obtiene la referencia local del último estado de la preparación.
 * @returns {Object|null}
 */
export function obtenerEstadoActual() {
  return estadoActual;
}

/**
 * Muestra el panel de alertas superior con soporte seguro para sesión expirada.
 * @param {string} mensaje 
 */
export function mostrarAlerta(mensaje) {
  const zonaAlertas = document.getElementById("zona-alertas");
  const textoAlerta = document.getElementById("texto-alerta");
  if (zonaAlertas && textoAlerta) {
    textoAlerta.textContent = mensaje || "Ocurrió un error inesperado.";
    if (mensaje === MENSAJE_SESION_EXPIRADA) {
      textoAlerta.appendChild(document.createTextNode(" "));
      const enlace = document.createElement("a");
      enlace.href = "/login";
      enlace.className = "font-semibold underline";
      enlace.textContent = "Iniciar sesión";
      textoAlerta.appendChild(enlace);
    }
    zonaAlertas.classList.remove("hidden");
  }
}

/**
 * Limpia y oculta el panel de alertas superior.
 */
export function limpiarAlerta() {
  const zonaAlertas = document.getElementById("zona-alertas");
  const textoAlerta = document.getElementById("texto-alerta");
  if (zonaAlertas && textoAlerta) {
    textoAlerta.textContent = "";
    zonaAlertas.classList.add("hidden");
  }
}

/**
 * Muestra u oculta avisos informativos contextuales dentro de la caja POS.
 * @param {string} mensaje 
 */
export function mostrarAvisoCaja(mensaje) {
  const avisoCaja = document.getElementById("aviso-caja");
  if (!avisoCaja) return;
  if (mensaje) {
    avisoCaja.textContent = mensaje;
    avisoCaja.style.display = "block";
  } else {
    avisoCaja.textContent = "";
    avisoCaja.style.display = "none";
  }
}

/**
 * Bloquea o desbloquea controles de acción durante peticiones activas (RF-007).
 * @param {boolean} bloqueado 
 */
export function toggleBotonesAccion(bloqueado) {
  const botonAgregar = document.getElementById("boton-agregar");
  const botonDeshacer = document.getElementById("boton-deshacer");
  const botonConfirmar = document.getElementById("boton-confirmar");
  const botonesRetirar = document.querySelectorAll(
    ".boton-retirar, button[title*='Eliminar']"
  );
  const camposDescuento = document.querySelectorAll(".input-descuento");

  camposDescuento.forEach((campo) => {
    campo.disabled = bloqueado;
  });

  if (botonAgregar) {
    botonAgregar.disabled = bloqueado;
  }

  if (botonConfirmar) {
    const tieneLineas = !!(
      estadoActual &&
      estadoActual.detalles &&
      estadoActual.detalles.length > 0
    );
    botonConfirmar.disabled = bloqueado || !tieneLineas;
  }

  const btnCancelarVenta = document.getElementById("btn-cancelar-venta");
  if (btnCancelarVenta) {
    btnCancelarVenta.disabled = bloqueado;
  }

  botonesRetirar.forEach((btn) => {
    btn.disabled = bloqueado;
  });

  if (botonDeshacer) {
    if (bloqueado) {
      botonDeshacer.disabled = true;
    } else {
      botonDeshacer.disabled = !(estadoActual && estadoActual.puedeDeshacer);
    }
  }
}

/**
 * Renderiza el estado completo de la preparación en la interfaz.
 * @param {Object} estado 
 * @param {Array<Object>} [catalogoMedicamentos=[]] 
 */
export function pintarEstado(estado, catalogoMedicamentos = []) {
  if (!estado) return;
  estadoActual = estado;

  const estadoVacio = document.getElementById("estado-vacio");
  const contenedorTabla = document.getElementById("contenedor-tabla");
  const cuerpoLineas = document.getElementById("cuerpo-lineas");
  const totalVenta = document.getElementById("total-venta");
  const botonDeshacer = document.getElementById("boton-deshacer");

  const detalles = estado.detalles || [];
  const tieneLineas = detalles.length > 0;

  // Total en soles (RF-006) y resumen subtotal/descuento (spec 004)
  if (totalVenta) {
    totalVenta.textContent = formatearMoneda(estado.total);
  }
  const totalUnidades = detalles.reduce(
    (acc, l) => acc + (Number(l.cantidad) || 0),
    0
  );
  const subtotalCant = document.getElementById("subtotal-cant");
  if (subtotalCant) {
    subtotalCant.textContent = String(totalUnidades);
  }
  const subtotalVenta = document.getElementById("subtotal-venta");
  if (subtotalVenta) {
    subtotalVenta.textContent = formatearMoneda(
      estado.subtotal ?? estado.total
    );
  }
  const descuentoVenta = document.getElementById("descuento-venta");
  if (descuentoVenta) {
    const descVal = Number(estado.descuentoTotal) || 0;
    if (descVal > 0) {
      descuentoVenta.textContent = "- " + formatearMoneda(descVal);
      descuentoVenta.style.color = "var(--rose, #be123c)";
    } else {
      descuentoVenta.textContent = "—";
      descuentoVenta.style.color = "inherit";
    }
  }

  // Botón Deshacer habilitado según puedeDeshacer (CA-4.2)
  const contadorPila = document.getElementById("contador-pila");
  if (botonDeshacer) {
    const puede = Boolean(estado.puedeDeshacer);
    botonDeshacer.disabled = !puede;
    if (puede) {
      botonDeshacer.style.opacity = "1";
      botonDeshacer.style.cursor = "pointer";
    } else {
      botonDeshacer.style.opacity = "0.4";
      botonDeshacer.style.cursor = "not-allowed";
    }
    if (contadorPila) {
      contadorPila.textContent = "";
    }
  }

  // Botón Confirmar deshabilitado con la preparación vacía (spec 003, CA-1.3)
  const botonConfirmar = document.getElementById("boton-confirmar");
  if (botonConfirmar) {
    botonConfirmar.disabled = !tieneLineas;
    if (tieneLineas) {
      botonConfirmar.className = "w-full bg-emerald-800 hover:bg-emerald-900 text-white font-semibold py-3.5 px-4 rounded-xl flex items-center justify-between shadow-sm transition";
      botonConfirmar.style.opacity = "1";
      botonConfirmar.style.cursor = "pointer";
    } else {
      botonConfirmar.className = "w-full bg-emerald-800/50 text-white/70 font-semibold py-3.5 px-4 rounded-xl flex items-center justify-between shadow-sm transition";
      botonConfirmar.style.opacity = "0.45";
      botonConfirmar.style.cursor = "not-allowed";
    }
  }

  const btnCancelarVenta = document.getElementById("btn-cancelar-venta");
  if (btnCancelarVenta) {
    if (tieneLineas) {
      btnCancelarVenta.style.opacity = "1";
      btnCancelarVenta.style.cursor = "pointer";
    } else {
      btnCancelarVenta.style.opacity = "0.6";
      btnCancelarVenta.style.cursor = "default";
    }
  }

  // Contador reactivo de productos en la canasta (estilo boceto)
  const contadorCanasta = document.getElementById("contador-canasta");
  if (contadorCanasta) {
    contadorCanasta.textContent = `${detalles.length} ${detalles.length === 1 ? "producto" : "productos"}`;
  }

  // Alternancia Estado Vacío (CA-1.1) vs Tabla de Líneas (CA-1.2)
  if (estadoVacio) {
    if (tieneLineas) {
      estadoVacio.classList.add("hidden");
    } else {
      estadoVacio.classList.remove("hidden");
    }
  }

  if (contenedorTabla) {
    if (tieneLineas) {
      contenedorTabla.classList.remove("hidden");
    } else {
      contenedorTabla.classList.add("hidden");
    }
  }

  // Renderizado seguro de filas de la canasta (5 columnas integradas - Boceto 01)
  if (cuerpoLineas) {
    const activeEl = document.activeElement;
    let focusedStepSelector = null;
    if (activeEl && activeEl.dataset && activeEl.dataset.id) {
      if (activeEl.classList.contains("btn-step-up")) {
        focusedStepSelector = `.btn-step-up[data-id="${activeEl.dataset.id}"]`;
      } else if (activeEl.classList.contains("btn-step-down")) {
        focusedStepSelector = `.btn-step-down[data-id="${activeEl.dataset.id}"]`;
      }
    }

    while (cuerpoLineas.firstChild) {
      cuerpoLineas.removeChild(cuerpoLineas.firstChild);
    }

    detalles.forEach((linea) => {
      const tr = document.createElement("tr");
      tr.dataset.idMedicamento = linea.idMedicamento;

      // 1. PRODUCTO: Nombre en negrita + .fefo (presentación · FEFO · lote · vence)
      const tdProducto = document.createElement("td");

      const spanCodigoOculto = document.createElement("span");
      spanCodigoOculto.className = "codigo-medicamento hidden";
      spanCodigoOculto.style.display = "none";
      spanCodigoOculto.textContent = linea.codigo;
      tdProducto.appendChild(spanCodigoOculto);

      const bNombre = document.createElement("b");
      bNombre.textContent = linea.nombre;
      tdProducto.appendChild(bNombre);

      const divFefo = document.createElement("div");
      divFefo.className = "fefo";

      const medCat = catalogoMedicamentos.find(
        (m) => m.idMedicamento === linea.idMedicamento || m.codigo === linea.codigo
      );
      const presentacionTexto = medCat && medCat.presentacion ? medCat.presentacion : "Caja o unidades";

      const spanPres = document.createElement("span");
      spanPres.textContent = presentacionTexto;
      divFefo.appendChild(spanPres);

      const punto = document.createElement("span");
      punto.textContent = "·";
      divFefo.appendChild(punto);

      const badgeFefo = document.createElement("i");
      badgeFefo.textContent = "Vence primero";
      divFefo.appendChild(badgeFefo);

      // Lotes asignados según política FEFO
      const lotes = linea.lotesFefo || [];
      if (lotes.length > 0) {
        const lotesInfo = lotes.map((l) => {
          const fechaStr = String(l.fechaVencimiento || "");
          let vence = fechaStr;
          if (fechaStr.length >= 7) {
            const partes = fechaStr.split("-");
            if (partes.length >= 2) {
              vence = `${partes[1]}/${partes[0]}`;
            }
          }
          return `Lote ${l.numeroLote} · vence ${vence}`;
        }).join(", ");

        const spanLote = document.createElement("span");
        spanLote.textContent = lotesInfo;
        divFefo.appendChild(spanLote);
      } else {
        const spanSinLote = document.createElement("span");
        spanSinLote.textContent = "Sin lote asignado";
        divFefo.appendChild(spanSinLote);
      }

      tdProducto.className = "col-prod";
      tdProducto.appendChild(divFefo);
      tr.appendChild(tdProducto);

      // 2. CANTIDAD: Stepper compacto centrado (.stepper)
      const tdCantidad = document.createElement("td");
      tdCantidad.className = "col-cant c";

      const divStepper = document.createElement("div");
      divStepper.className = "stepper";

      const btnDown = document.createElement("button");
      btnDown.type = "button";
      btnDown.className = "btn-step-down";
      btnDown.dataset.id = linea.idMedicamento;
      btnDown.dataset.cant = linea.cantidad;
      btnDown.setAttribute("aria-label", "Disminuir cantidad");
      btnDown.title = "Disminuir cantidad (-1)";
      btnDown.innerHTML = '<svg class="ico" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14"/></svg>';

      const bCantidad = document.createElement("b");
      bCantidad.textContent = String(linea.cantidad);

      const btnUp = document.createElement("button");
      btnUp.type = "button";
      btnUp.className = "btn-step-up";
      btnUp.dataset.id = linea.idMedicamento;
      btnUp.dataset.cant = linea.cantidad;
      btnUp.setAttribute("aria-label", "Aumentar cantidad");
      btnUp.title = "Aumentar cantidad (+1)";
      btnUp.innerHTML = '<svg class="ico" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14M12 5v14"/></svg>';

      divStepper.appendChild(btnDown);
      divStepper.appendChild(bCantidad);
      divStepper.appendChild(btnUp);
      tdCantidad.appendChild(divStepper);
      tr.appendChild(tdCantidad);

      // 3. P. UNIT.: Monto en soles
      const tdPrecio = document.createElement("td");
      tdPrecio.className = "col-precio r";
      tdPrecio.textContent = formatearMoneda(linea.precioUnitario);
      tr.appendChild(tdPrecio);

      // 4. DESC.: Descuento de línea (- S/ 2.00 o —)
      const tdDesc = document.createElement("td");
      tdDesc.className = "col-desc r";

      const descVal = Number(linea.descuento || 0);
      const spanDesc = document.createElement("span");
      spanDesc.className = "btn-editar-descuento";
      spanDesc.dataset.id = linea.idMedicamento;
      spanDesc.dataset.descuento = descVal.toFixed(2);
      spanDesc.dataset.nombre = linea.nombre;
      spanDesc.dataset.subtotal = (Number(linea.cantidad) * Number(linea.precioUnitario)).toFixed(2);
      spanDesc.dataset.precioUnitario = Number(linea.precioUnitario).toFixed(2);
      spanDesc.dataset.cantidad = String(linea.cantidad);
      spanDesc.title = "Clic para aplicar descuento";
      spanDesc.style.cursor = "pointer";
      if (descVal > 0) {
        spanDesc.style.color = "var(--rose, #be123c)";
        spanDesc.textContent = `- ${formatearMoneda(descVal)}`;
      } else {
        spanDesc.textContent = "—";
      }
      tdDesc.appendChild(spanDesc);

      const inputDescOculto = document.createElement("input");
      inputDescOculto.type = "hidden";
      inputDescOculto.className = "input-descuento";
      inputDescOculto.dataset.id = linea.idMedicamento;
      inputDescOculto.value = descVal.toFixed(2);
      tdDesc.appendChild(inputDescOculto);

      tr.appendChild(tdDesc);

      // 5. TOTAL: Total de línea en negrita
      const tdTotal = document.createElement("td");
      tdTotal.className = "col-tot r";
      const bTotal = document.createElement("b");
      bTotal.textContent = formatearMoneda(linea.importe);
      tdTotal.appendChild(bTotal);
      tr.appendChild(tdTotal);

      // 6. ACCIÓN: Botón rojo papelera (.ibtn.dng)
      const tdAccion = document.createElement("td");
      tdAccion.className = "py-3 px-4 text-right align-middle";

      const formBorrar = document.createElement("form");
      formBorrar.className = "m-0 inline-flex items-center justify-center";
      formBorrar.action = `/ventas/preparacion/${encodeURIComponent(linea.idMedicamento)}`;
      formBorrar.method = "POST";
      formBorrar.onsubmit = function (e) {
        e.preventDefault();
      };

      const csrf = obtenerCsrf();
      if (csrf && csrf.token) {
        const inputCsrf = document.createElement("input");
        inputCsrf.type = "hidden";
        inputCsrf.name = "_csrf";
        inputCsrf.value = csrf.token;
        formBorrar.appendChild(inputCsrf);
      }

      const inputMethod = document.createElement("input");
      inputMethod.type = "hidden";
      inputMethod.name = "_method";
      inputMethod.value = "DELETE";
      formBorrar.appendChild(inputMethod);

      const btnBorrar = document.createElement("button");
      btnBorrar.type = "button";
      btnBorrar.className = "inline-flex h-9 w-9 items-center justify-center rounded-lg border border-rose-200 bg-rose-50/70 text-rose-500 transition hover:bg-rose-100 hover:text-rose-700 boton-retirar";
      btnBorrar.dataset.id = linea.idMedicamento;
      btnBorrar.style.cursor = "pointer";
      btnBorrar.title = `Eliminar ${linea.nombre} de la canasta`;
      btnBorrar.innerHTML = '<svg class="h-4 w-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>';

      formBorrar.appendChild(btnBorrar);
      tdAccion.appendChild(formBorrar);
      tr.appendChild(tdAccion);

      cuerpoLineas.appendChild(tr);
    });

    if (focusedStepSelector) {
      const elToFocus = cuerpoLineas.querySelector(focusedStepSelector);
      if (elToFocus) {
        elToFocus.focus();
      }
    }
  }
}
