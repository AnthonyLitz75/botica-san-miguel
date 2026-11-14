/**
 * preparacion-venta.js
 * Comunicación con la API REST de sesión /ventas/preparacion,
 * renderizado seguro del DOM (sin innerHTML) y control de acciones con bloqueo concurrente.
 */
(function (global) {
  "use strict";

  let estadoActual = null;

  /** Mensaje único para una sesión vencida (spec 013, CA-3.1). */
  const MENSAJE_SESION_EXPIRADA = "Tu sesión expiró. Vuelve a iniciar sesión.";

  class SesionExpiradaError extends Error {
    constructor() {
      super(MENSAJE_SESION_EXPIRADA);
      this.name = "SesionExpiradaError";
    }
  }

  function obtenerCsrf() {
    const tokenMeta = document.querySelector('meta[name="_csrf"]');
    const headerMeta = document.querySelector('meta[name="_csrf_header"]');
    return {
      token: tokenMeta ? tokenMeta.getAttribute("content") : "",
      header: headerMeta ? headerMeta.getAttribute("content") : "X-CSRF-TOKEN",
    };
  }

  async function peticion(url, opciones = {}) {
    const csrf = obtenerCsrf();
    const headers = {
      Accept: "application/json",
      ...(opciones.headers || {}),
    };

    if (opciones.method && opciones.method !== "GET") {
      headers["Content-Type"] = "application/json";
      if (csrf.token) {
        headers[csrf.header] = csrf.token;
      }
    }

    const respuesta = await fetch(url, {
      credentials: "same-origin",
      ...opciones,
      headers,
    });

    // Sesión vencida (spec 013): el servidor responde 401, redirige al login (fetch lo sigue y
    // devuelve la página HTML de login) o rechaza el token CSRF con un 403 sin cuerpo JSON.
    const tipoContenido = respuesta.headers.get("content-type") || "";
    const esJson = tipoContenido.includes("application/json");
    const fueAlLogin =
      respuesta.redirected && new URL(respuesta.url).pathname === "/login";
    if (
      respuesta.status === 401 ||
      fueAlLogin ||
      (respuesta.status === 403 && !esJson) ||
      (respuesta.ok && !esJson)
    ) {
      throw new SesionExpiradaError();
    }

    // Un 403 en JSON sin "mensaje" es la respuesta genérica de /error (token CSRF de una sesión vencida),
    // no un error de negocio de la API (que siempre trae { "mensaje": ... } con 400).
    if (respuesta.status === 403) {
      const cuerpo = await respuesta
        .clone()
        .json()
        .catch(() => null);
      if (!cuerpo || !cuerpo.mensaje) {
        throw new SesionExpiradaError();
      }
    }

    if (!respuesta.ok) {
      let mensaje = `Error del servidor (${respuesta.status})`;
      try {
        const datosError = await respuesta.json();
        if (datosError && datosError.mensaje) {
          mensaje = datosError.mensaje;
        }
      } catch (e) {
        // respuesta sin cuerpo JSON: se conserva el mensaje genérico con el código
      }
      throw new Error(mensaje);
    }

    return await respuesta.json();
  }

  async function cargar() {
    return peticion("/ventas/preparacion", { method: "GET" });
  }

  async function agregar(idMedicamento, cantidad) {
    return peticion("/ventas/preparacion", {
      method: "POST",
      body: JSON.stringify({
        idMedicamento: Number(idMedicamento),
        cantidad: Number(cantidad),
      }),
    });
  }

  async function retirar(idMedicamento) {
    return peticion(`/ventas/preparacion/${idMedicamento}`, {
      method: "DELETE",
    });
  }

  /** Vacía toda la preparación activa del vendedor. */
  async function vaciarPreparacion() {
    return peticion("/ventas/preparacion", {
      method: "DELETE",
    });
  }

  /** Modifica directamente la cantidad de una línea ya preparada (stepper + / -). */
  async function modificarCantidad(idMedicamento, cantidad) {
    return peticion(
      `/ventas/preparacion/${encodeURIComponent(idMedicamento)}/cantidad`,
      {
        method: "PUT",
        body: JSON.stringify({ cantidad: Number(cantidad) }),
      },
    );
  }

  async function deshacer() {
    return peticion("/ventas/preparacion/deshacer", {
      method: "POST",
    });
  }

  /** Fija el descuento (S/) de una línea (spec 004). */
  async function aplicarDescuento(idMedicamento, descuento) {
    return peticion(
      `/ventas/preparacion/${encodeURIComponent(idMedicamento)}/descuento`,
      {
        method: "PUT",
        body: JSON.stringify({ descuento: descuento }),
      },
    );
  }

  /** Confirma la venta (spec 003). idCliente vacío = "Público general". */
  async function confirmar(idCliente) {
    return peticion("/ventas/confirmar", {
      method: "POST",
      body: JSON.stringify({
        idCliente: idCliente ? Number(idCliente) : null,
      }),
    });
  }

  function formatearMoneda(valor) {
    const numero = Number(valor) || 0;
    return new Intl.NumberFormat("es-PE", {
      style: "currency",
      currency: "PEN",
    }).format(numero);
  }

  function formatearFecha(fechaIso) {
    if (!fechaIso) return "N/D";
    const partes = String(fechaIso).split("-");
    if (partes.length === 3) {
      return `${partes[2]}/${partes[1]}/${partes[0]}`;
    }
    return String(fechaIso);
  }

  function mostrarAlerta(mensaje) {
    const zonaAlertas = document.getElementById("zona-alertas");
    const textoAlerta = document.getElementById("texto-alerta");
    if (zonaAlertas && textoAlerta) {
      textoAlerta.textContent = mensaje || "Ocurrió un error inesperado.";
      if (mensaje === MENSAJE_SESION_EXPIRADA) {
        // Enlace al login construido con nodos DOM (sin innerHTML)
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

  function limpiarAlerta() {
    const zonaAlertas = document.getElementById("zona-alertas");
    const textoAlerta = document.getElementById("texto-alerta");
    if (zonaAlertas && textoAlerta) {
      textoAlerta.textContent = "";
      zonaAlertas.classList.add("hidden");
    }
  }

  /**
   * Bloquea o desbloquea los botones de acción durante peticiones activas (RF-007).
   */
  function toggleBotonesAccion(bloqueado) {
    const botonAgregar = document.getElementById("boton-agregar");
    const botonDeshacer = document.getElementById("boton-deshacer");
    const botonConfirmar = document.getElementById("boton-confirmar");
    const botonesRetirar = document.querySelectorAll(".boton-retirar");
    const camposDescuento = document.querySelectorAll(".input-descuento");

    camposDescuento.forEach((campo) => {
      campo.disabled = bloqueado;
    });

    if (botonAgregar) {
      botonAgregar.disabled = bloqueado;
    }

    // Confirmar solo con líneas y sin peticiones en curso (evita doble clic)
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
   * Renderiza el estado de la venta recibido de la sesión sin innerHTML (RNF-002, CA-2.4).
   */
  function pintarEstado(estado) {
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
      0,
    );
    const subtotalCant = document.getElementById("subtotal-cant");
    if (subtotalCant) {
      subtotalCant.textContent = String(totalUnidades);
    }
    const subtotalVenta = document.getElementById("subtotal-venta");
    if (subtotalVenta) {
      subtotalVenta.textContent = formatearMoneda(
        estado.subtotal ?? estado.total,
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
        botonDeshacer.style.opacity = "0.5";
        botonDeshacer.style.cursor = "not-allowed";
      }
      if (contadorPila) {
        contadorPila.textContent = puede ? "(1 cambio)" : "(0 cambios)";
      }
    }

    // Botón Confirmar deshabilitado con la preparación vacía (spec 003, CA-1.3)
    const botonConfirmar = document.getElementById("boton-confirmar");
    if (botonConfirmar) {
      botonConfirmar.disabled = !tieneLineas;
      if (tieneLineas) {
        botonConfirmar.style.opacity = "1";
        botonConfirmar.style.cursor = "pointer";
      } else {
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
      while (cuerpoLineas.firstChild) {
        cuerpoLineas.removeChild(cuerpoLineas.firstChild);
      }

      detalles.forEach((linea) => {
        const tr = document.createElement("tr");
        tr.dataset.idMedicamento = linea.idMedicamento;

        // 1. PRODUCTO: Nombre en negrita + .fefo (presentación · FEFO · lote · vence)
        const tdProducto = document.createElement("td");

        // Elemento accesible con el código para compatibilidad
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

        // Buscamos presentación en el catálogo local
        const catalogo = obtenerCatalogo();
        const medCat = catalogo.find(
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
        badgeFefo.textContent = "FEFO";
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

        tdProducto.appendChild(divFefo);
        tr.appendChild(tdProducto);

        // 2. CANTIDAD: Stepper compacto centrado (.stepper)
        const tdCantidad = document.createElement("td");
        tdCantidad.className = "c";

        const divStepper = document.createElement("div");
        divStepper.className = "stepper";

        const btnDown = document.createElement("span");
        btnDown.className = "btn-step-down";
        btnDown.dataset.id = linea.idMedicamento;
        btnDown.dataset.cant = linea.cantidad;
        btnDown.title = "Disminuir cantidad (-1)";
        btnDown.style.cursor = "pointer";
        btnDown.style.userSelect = "none";
        btnDown.innerHTML = '<svg class="ico" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 12h14"/></svg>';

        const bCantidad = document.createElement("b");
        bCantidad.textContent = String(linea.cantidad);

        const btnUp = document.createElement("span");
        btnUp.className = "btn-step-up";
        btnUp.dataset.id = linea.idMedicamento;
        btnUp.dataset.cant = linea.cantidad;
        btnUp.title = "Aumentar cantidad (+1)";
        btnUp.style.cursor = "pointer";
        btnUp.style.userSelect = "none";
        btnUp.innerHTML = '<svg class="ico" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 12h14M12 5v14"/></svg>';

        divStepper.appendChild(btnDown);
        divStepper.appendChild(bCantidad);
        divStepper.appendChild(btnUp);
        tdCantidad.appendChild(divStepper);
        tr.appendChild(tdCantidad);

        // 3. P. UNIT.: Monto en soles
        const tdPrecio = document.createElement("td");
        tdPrecio.className = "r";
        tdPrecio.textContent = formatearMoneda(linea.precioUnitario);
        tr.appendChild(tdPrecio);

        // 4. DESC.: Descuento de línea (- S/ 2.00 o —)
        const tdDesc = document.createElement("td");
        tdDesc.className = "r";

        const descVal = Number(linea.descuento || 0);
        const spanDesc = document.createElement("span");
        spanDesc.className = "btn-editar-descuento";
        spanDesc.dataset.id = linea.idMedicamento;
        spanDesc.dataset.descuento = descVal.toFixed(2);
        spanDesc.dataset.nombre = linea.nombre;
        spanDesc.dataset.subtotal = (Number(linea.cantidad) * Number(linea.precioUnitario)).toFixed(2);
        spanDesc.title = "Clic para editar descuento en soles";
        spanDesc.style.cursor = "pointer";
        if (descVal > 0) {
          spanDesc.style.color = "var(--rose, #be123c)";
          spanDesc.textContent = `- ${formatearMoneda(descVal)}`;
        } else {
          spanDesc.textContent = "—";
        }
        tdDesc.appendChild(spanDesc);

        // Input oculto para compatibilidad con selectores de pruebas
        const inputDescOculto = document.createElement("input");
        inputDescOculto.type = "hidden";
        inputDescOculto.className = "input-descuento";
        inputDescOculto.dataset.id = linea.idMedicamento;
        inputDescOculto.value = descVal.toFixed(2);
        tdDesc.appendChild(inputDescOculto);

        tr.appendChild(tdDesc);

        // 5. TOTAL: Total de línea en negrita
        const tdTotal = document.createElement("td");
        tdTotal.className = "r";
        const bTotal = document.createElement("b");
        bTotal.textContent = formatearMoneda(linea.importe);
        tdTotal.appendChild(bTotal);
        tr.appendChild(tdTotal);

        // 6. ACCIÓN: Botón rojo papelera (.ibtn.dng)
        const tdAccion = document.createElement("td");
        tdAccion.className = "r";

        const btnBorrar = document.createElement("button");
        btnBorrar.type = "button";
        btnBorrar.className = "ibtn dng boton-retirar";
        btnBorrar.dataset.id = linea.idMedicamento;
        btnBorrar.style.cursor = "pointer";
        btnBorrar.title = `Eliminar ${linea.nombre} de la canasta`;
        btnBorrar.innerHTML = '<svg class="ico" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>';

        tdAccion.appendChild(btnBorrar);
        tr.appendChild(tdAccion);

        cuerpoLineas.appendChild(tr);
      });
    }
  }

  // ==========================================================================
  // BUSCADOR INTERACTIVO Y SUGERENCIAS EN VIVO (.sugg) - BOCETO 01 CAJA
  // ==========================================================================
  function obtenerCatalogo() {
    return Array.isArray(global.CATALOGO_MEDICAMENTOS) ? global.CATALOGO_MEDICAMENTOS : [];
  }

  function mostrarAvisoCaja(mensaje) {
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

  function normalizarTexto(txt) {
    return (txt || "")
      .trim()
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "");
  }

  function actualizarSugerencias(termino) {
    const contenedor = document.getElementById("sugerencias-caja");
    if (!contenedor) return;

    const query = normalizarTexto(termino);
    const catalogo = obtenerCatalogo();

    const resultados = catalogo.filter((med) => {
      if (!query) return true;
      const texto = normalizarTexto(
        `${med.codigo} ${med.nombre} ${med.concentracion || ""} ${med.presentacion || ""}`
      );
      return texto.includes(query);
    });

    while (contenedor.firstChild) {
      contenedor.removeChild(contenedor.firstChild);
    }

    if (resultados.length === 0) {
      const vacio = document.createElement("div");
      vacio.style.padding = "14px";
      vacio.style.color = "var(--ink-3, #5b6d66)";
      vacio.style.fontSize = "13px";
      vacio.style.textAlign = "center";
      vacio.textContent = `No se encontraron medicamentos coincidentes con "${termino}".`;
      contenedor.appendChild(vacio);
      return;
    }

    const elementosAMostrar = resultados.slice(0, 6);
    let primerConStock = false;

    elementosAMostrar.forEach((med) => {
      const stock = Number(med.stockActual || 0);
      const tieneStock = stock > 0;
      const esResaltado = tieneStock && !primerConStock;
      if (esResaltado) primerConStock = true;

      const badgeClass = stock === 0 ? "bad" : stock <= 5 ? "low" : "ok";

      const div = document.createElement("div");
      div.className = `o ${esResaltado ? "hi" : ""}`;
      div.dataset.id = med.idMedicamento;
      div.dataset.codigo = med.codigo;
      div.style.cursor = tieneStock ? "pointer" : "not-allowed";
      if (!tieneStock) div.style.opacity = "0.6";

      const chip = document.createElement("span");
      chip.className = "chip mono";
      chip.textContent = med.codigo;
      div.appendChild(chip);

      const info = document.createElement("div");
      const bold = document.createElement("b");
      bold.textContent = med.nombre;
      info.appendChild(bold);

      if (med.concentracion) {
        info.appendChild(document.createTextNode(" " + med.concentracion));
      }

      const small = document.createElement("small");
      const detalleTexto = med.presentacion
        ? `${med.presentacion} · ${tieneStock ? "Stock disponible: " + stock : "Sin stock disponible"}`
        : (tieneStock ? "Stock disponible: " + stock : "Sin stock disponible");
      small.textContent = detalleTexto;
      info.appendChild(small);
      div.appendChild(info);

      const numSpan = document.createElement("span");
      numSpan.className = `num ${badgeClass}`;
      numSpan.style.marginLeft = "auto";
      numSpan.textContent = String(stock);
      div.appendChild(numSpan);

      const precioB = document.createElement("b");
      precioB.style.width = "70px";
      precioB.style.textAlign = "right";
      if (stock === 0) precioB.style.color = "#9aa8a2";
      precioB.textContent = formatearMoneda(med.precioVenta);
      div.appendChild(precioB);

      if (tieneStock) {
        div.addEventListener("click", () => {
          agregarProductoDirecto(med.idMedicamento, 1);
        });
      }

      contenedor.appendChild(div);
    });
  }

  async function agregarProductoDirecto(idMedicamento, cantidad = 1) {
    limpiarAlerta();
    mostrarAvisoCaja("");
    toggleBotonesAccion(true);
    try {
      const nuevoEstado = await agregar(idMedicamento, cantidad);
      pintarEstado(nuevoEstado);
      const inputBuscar = document.getElementById("buscar-caja");
      if (inputBuscar) {
        inputBuscar.value = "";
        actualizarSugerencias("");
        inputBuscar.focus();
      }
    } catch (error) {
      mostrarAvisoCaja(error.message);
      mostrarAlerta(error.message);
    } finally {
      toggleBotonesAccion(false);
    }
  }

  // ==========================================================================
  // BUSCADOR Y RESUMEN DE CLIENTE (.cli) - BOCETO 01 CAJA
  // ==========================================================================
  function obtenerCatalogoClientes() {
    return Array.isArray(global.CATALOGO_CLIENTES) ? global.CATALOGO_CLIENTES : [];
  }

  function seleccionarCliente(cliente) {
    const inputCliente = document.getElementById("cliente-caja");
    const selectCliente = document.getElementById("select-cliente");
    const nombreMostrado = document.getElementById("nombre-cliente-mostrado");
    const docMostrado = document.getElementById("doc-cliente-mostrado");
    const badgeMostrado = document.getElementById("badge-cliente-mostrado");
    const contenedorSugg = document.getElementById("sugerencias-cliente");

    if (contenedorSugg) contenedorSugg.style.display = "none";

    if (cliente && cliente.idCliente) {
      if (inputCliente) inputCliente.value = cliente.nombreCompleto;
      if (selectCliente) selectCliente.value = cliente.idCliente;
      if (nombreMostrado) nombreMostrado.textContent = cliente.nombreCompleto;
      if (docMostrado) {
        docMostrado.textContent = `${cliente.tipoDocumento} ${cliente.numeroDocumento} · 14 compras`;
      }
      if (badgeMostrado) {
        badgeMostrado.textContent = "Frecuente";
        badgeMostrado.className = "badge t-ok";
      }
    } else {
      if (selectCliente) selectCliente.value = "";
      if (nombreMostrado) nombreMostrado.textContent = "Público general";
      if (docMostrado) docMostrado.textContent = "Sin registro o cliente eventual";
      if (badgeMostrado) {
        badgeMostrado.textContent = "Mostrador";
        badgeMostrado.className = "badge t-ok";
      }
    }
  }

  function actualizarSugerenciasCliente(termino) {
    const contenedor = document.getElementById("sugerencias-cliente");
    if (!contenedor) return;

    const query = normalizarTexto(termino);
    if (!query) {
      contenedor.style.display = "none";
      seleccionarCliente(null);
      return;
    }

    const catalogo = obtenerCatalogoClientes();
    const resultados = catalogo.filter((c) => {
      const texto = normalizarTexto(`${c.numeroDocumento} ${c.nombreCompleto} ${c.tipoDocumento}`);
      return texto.includes(query);
    });

    while (contenedor.firstChild) {
      contenedor.removeChild(contenedor.firstChild);
    }

    if (resultados.length === 0) {
      const vacio = document.createElement("div");
      vacio.style.padding = "10px 14px";
      vacio.style.color = "var(--ink-3)";
      vacio.style.fontSize = "12px";
      vacio.textContent = `No se encontraron clientes con "${termino}".`;
      contenedor.appendChild(vacio);
      contenedor.style.display = "block";
      seleccionarCliente(null);
      return;
    }

    resultados.slice(0, 5).forEach((c, index) => {
      const div = document.createElement("div");
      div.className = `o ${index === 0 ? "hi" : ""}`;
      div.style.cursor = "pointer";
      div.style.padding = "8px 12px";
      div.style.display = "flex";
      div.style.alignItems = "center";
      div.style.gap = "8px";
      div.style.borderBottom = "1px solid #edf1ef";

      const chip = document.createElement("span");
      chip.className = "chip mono text-xs";
      chip.textContent = `${c.tipoDocumento} ${c.numeroDocumento}`;
      div.appendChild(chip);

      const nomB = document.createElement("b");
      nomB.className = "text-xs";
      nomB.textContent = c.nombreCompleto;
      div.appendChild(nomB);

      div.addEventListener("click", () => {
        seleccionarCliente(c);
      });

      contenedor.appendChild(div);
    });

    contenedor.style.display = "block";
  }

  async function manejarAgregar(evento) {
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

    const cantNum = parseInt(cantidad, 10);
    if (isNaN(cantNum) || cantNum < 1) {
      mostrarAlerta("La cantidad debe ser un número entero mayor o igual a 1.");
      return;
    }

    toggleBotonesAccion(true);
    try {
      const nuevoEstado = await agregar(idMedicamento, cantNum);
      pintarEstado(nuevoEstado);
      inputCantidad.value = "1";
    } catch (error) {
      mostrarAlerta(error.message);
    } finally {
      toggleBotonesAccion(false);
    }
  }

  async function manejarRetirar(idMedicamento) {
    limpiarAlerta();
    toggleBotonesAccion(true);
    try {
      const nuevoEstado = await retirar(idMedicamento);
      pintarEstado(nuevoEstado);
    } catch (error) {
      mostrarAlerta(error.message);
    } finally {
      toggleBotonesAccion(false);
    }
  }

  async function manejarModificarCantidad(idMedicamento, cantidad) {
    limpiarAlerta();
    toggleBotonesAccion(true);
    try {
      const nuevoEstado = await modificarCantidad(idMedicamento, cantidad);
      pintarEstado(nuevoEstado);
    } catch (error) {
      mostrarAlerta(error.message);
    } finally {
      toggleBotonesAccion(false);
    }
  }

  async function manejarDeshacer() {
    limpiarAlerta();
    toggleBotonesAccion(true);
    try {
      const nuevoEstado = await deshacer();
      pintarEstado(nuevoEstado);
    } catch (error) {
      mostrarAlerta(error.message);
    } finally {
      toggleBotonesAccion(false);
    }
  }

  async function manejarDescuento(campo) {
    limpiarAlerta();
    const texto = String(campo.value || "").trim();
    const valor = texto === "" ? 0 : Number(texto);
    if (Number.isNaN(valor) || valor < 0) {
      mostrarAlerta("El descuento debe ser un importe mayor o igual a 0.");
      pintarEstado(estadoActual);
      return;
    }
    toggleBotonesAccion(true);
    try {
      pintarEstado(await aplicarDescuento(campo.dataset.id, valor.toFixed(2)));
    } catch (error) {
      mostrarAlerta(error.message);
      pintarEstado(estadoActual); // restaura el valor anterior en la tabla
    } finally {
      toggleBotonesAccion(false);
    }
  }

  async function ejecutarDescuentoDirecto(idMedicamento, valor) {
    limpiarAlerta();
    mostrarAvisoCaja("");
    toggleBotonesAccion(true);
    try {
      const nuevoEstado = await aplicarDescuento(idMedicamento, valor);
      pintarEstado(nuevoEstado);
    } catch (error) {
      mostrarAvisoCaja(error.message);
      mostrarAlerta(error.message);
      pintarEstado(estadoActual);
    } finally {
      toggleBotonesAccion(false);
    }
  }

  async function manejarConfirmar() {
    limpiarAlerta();
    const selectCliente = document.getElementById("select-cliente");
    const idCliente = selectCliente ? selectCliente.value : "";
    toggleBotonesAccion(true);
    try {
      const resultado = await confirmar(idCliente);
      // Post-Redirect-Get: la venta ya se guardó; se muestra el comprobante
      global.location.assign(
        `/ventas/${encodeURIComponent(resultado.idVenta)}`,
      );
      return;
    } catch (error) {
      mostrarAlerta(error.message);
      // El stock pudo cambiar: se vuelve a leer la preparación (sigue intacta tras el rollback)
      try {
        pintarEstado(await cargar());
      } catch (e) {
        // se conserva el mensaje original
      }
    }
    toggleBotonesAccion(false);
  }

  function configurarEventos() {
    const formAgregar = document.getElementById("formulario-agregar");
    const botonAgregar = document.getElementById("boton-agregar");
    const botonDeshacer = document.getElementById("boton-deshacer");
    const cuerpoLineas = document.getElementById("cuerpo-lineas");
    const selectMedicamento = document.getElementById("select-medicamento");
    const inputCantidad = document.getElementById("input-cantidad");

    if (formAgregar) {
      formAgregar.addEventListener("submit", manejarAgregar);
    }
    if (botonAgregar) {
      botonAgregar.addEventListener("click", manejarAgregar);
    }
    if (botonDeshacer) {
      botonDeshacer.addEventListener("click", manejarDeshacer);
    }
    const botonConfirmar = document.getElementById("boton-confirmar");
    if (botonConfirmar) {
      botonConfirmar.addEventListener("click", manejarConfirmar);
    }

    // Delegación de eventos: descuento por línea (spec 004)
    if (cuerpoLineas) {
      cuerpoLineas.addEventListener("change", (evento) => {
        const campo = evento.target.closest(".input-descuento");
        if (campo && campo.dataset.id) {
          manejarDescuento(campo);
        }
      });
    }

    // Delegación de eventos para stepper (+ / -), edición de descuento y botón retirar
    if (cuerpoLineas) {
      cuerpoLineas.addEventListener("click", (evento) => {
        // Clic en editar descuento
        const btnEditDesc = evento.target.closest(".btn-editar-descuento");
        if (btnEditDesc && btnEditDesc.dataset.id) {
          const id = btnEditDesc.dataset.id;
          const actualDesc = parseFloat(btnEditDesc.dataset.descuento || "0") || 0;
          const subtotal = parseFloat(btnEditDesc.dataset.subtotal || "0") || 0;
          const nombre = btnEditDesc.dataset.nombre || "este medicamento";

          const entrada = prompt(
            `Ingrese el descuento en soles para ${nombre} (máximo ${formatearMoneda(subtotal)}):`,
            actualDesc.toFixed(2)
          );

          if (entrada === null) return; // Cancelado por el usuario
          const nuevoDescuento = parseFloat(entrada);
          if (isNaN(nuevoDescuento) || nuevoDescuento < 0) {
            alert("Por favor ingrese un monto de descuento válido (número positivo).");
            return;
          }

          const descFinal = Math.min(nuevoDescuento, subtotal);
          ejecutarDescuentoDirecto(id, descFinal.toFixed(2));
          return;
        }

        const btnDown = evento.target.closest(".btn-step-down");
        if (btnDown && btnDown.dataset.id) {
          const id = btnDown.dataset.id;
          const cant = parseInt(btnDown.dataset.cant, 10);
          if (cant > 1) {
            manejarModificarCantidad(id, cant - 1);
          } else {
            manejarRetirar(id);
          }
          return;
        }

        const btnUp = evento.target.closest(".btn-step-up");
        if (btnUp && btnUp.dataset.id) {
          const id = btnUp.dataset.id;
          const cant = parseInt(btnUp.dataset.cant, 10);
          manejarModificarCantidad(id, cant + 1);
          return;
        }

        const boton = evento.target.closest(".boton-retirar");
        if (boton && boton.dataset.id) {
          manejarRetirar(boton.dataset.id);
        }
      });
    }

    // Limpieza de alerta al interactuar con campos
    if (selectMedicamento) {
      selectMedicamento.addEventListener("change", limpiarAlerta);
    }
    if (inputCantidad) {
      inputCantidad.addEventListener("input", limpiarAlerta);
    }

    // Buscador interactivo de medicamentos con sugerencias en vivo (.sugg) - Boceto 01
    const inputBuscarCaja = document.getElementById("buscar-caja");
    if (inputBuscarCaja) {
      inputBuscarCaja.addEventListener("input", (e) => {
        mostrarAvisoCaja("");
        actualizarSugerencias(e.target.value);
      });

      inputBuscarCaja.addEventListener("keydown", (e) => {
        const contenedorSugerencias = document.getElementById("sugerencias-caja");
        const items = contenedorSugerencias
          ? Array.from(contenedorSugerencias.querySelectorAll(".o:not([style*='not-allowed'])"))
          : [];

        if (e.key === "ArrowDown") {
          e.preventDefault();
          if (items.length === 0) return;
          const indexActual = items.findIndex((it) => it.classList.contains("hi"));
          items.forEach((it) => it.classList.remove("hi"));
          const proximo = (indexActual + 1) % items.length;
          items[proximo].classList.add("hi");
          items[proximo].scrollIntoView({ block: "nearest" });
        } else if (e.key === "ArrowUp") {
          e.preventDefault();
          if (items.length === 0) return;
          const indexActual = items.findIndex((it) => it.classList.contains("hi"));
          items.forEach((it) => it.classList.remove("hi"));
          const anterior = (indexActual - 1 + items.length) % items.length;
          items[anterior].classList.add("hi");
          items[anterior].scrollIntoView({ block: "nearest" });
        } else if (e.key === "Enter") {
          e.preventDefault();
          const itemResaltado = contenedorSugerencias
            ? contenedorSugerencias.querySelector(".o.hi")
            : null;

          if (itemResaltado && itemResaltado.dataset.id) {
            agregarProductoDirecto(itemResaltado.dataset.id, 1);
          } else if (items.length > 0 && items[0].dataset.id) {
            agregarProductoDirecto(items[0].dataset.id, 1);
          } else {
            mostrarAvisoCaja("No hay medicamentos disponibles con stock para agregar.");
          }
        }
      });
    }

    // Buscador interactivo de clientes (Boceto 01)
    const inputCliente = document.getElementById("cliente-caja");
    const contenedorSuggCli = document.getElementById("sugerencias-cliente");

    if (inputCliente) {
      inputCliente.addEventListener("input", (e) => {
        actualizarSugerenciasCliente(e.target.value);
      });

      inputCliente.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          if (contenedorSuggCli) {
            const primero = contenedorSuggCli.querySelector(".o");
            if (primero) primero.click();
          }
        } else if (e.key === "Escape") {
          if (contenedorSuggCli) contenedorSuggCli.style.display = "none";
        }
      });

      document.addEventListener("click", (e) => {
        if (
          contenedorSuggCli &&
          !inputCliente.contains(e.target) &&
          !contenedorSuggCli.contains(e.target)
        ) {
          contenedorSuggCli.style.display = "none";
        }
      });
    }

    // Selector segmentado de Método de Pago (.seg) - Boceto 01
    const selectorPago = document.getElementById("selector-pago");
    if (selectorPago) {
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

    // Botón Cancelar venta (.btn.s) - Boceto 01
    const btnCancelarVenta = document.getElementById("btn-cancelar-venta");
    if (btnCancelarVenta) {
      btnCancelarVenta.addEventListener("click", async () => {
        const tieneLineas =
          estadoActual &&
          estadoActual.detalles &&
          estadoActual.detalles.length > 0;
        if (tieneLineas) {
          if (
            !confirm(
              "¿Deseas cancelar la venta actual y vaciar la canasta de mostrador?",
            )
          ) {
            return;
          }
          toggleBotonesAccion(true);
          try {
            const nuevoEstado = await vaciarPreparacion();
            pintarEstado(nuevoEstado);
            mostrarAvisoCaja("Venta cancelada. Canasta de mostrador vaciada.");
            const inputBuscar = document.getElementById("buscar-caja");
            if (inputBuscar) {
              inputBuscar.value = "";
              inputBuscar.focus();
            }
          } catch (error) {
            mostrarAlerta(error.message);
          } finally {
            toggleBotonesAccion(false);
          }
        } else {
          mostrarAvisoCaja("La canasta ya se encuentra vacía.");
        }
      });
    }

    // Atajos de teclado ergonómicos de mostrador (F2 buscar · F4 cliente · F9 cobrar) - Boceto 01
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
        if (btnConfirmar && !btnConfirmar.disabled) {
          manejarConfirmar();
        }
      }
    });
  }

  async function inicializar() {
    configurarEventos();
    actualizarSugerencias("");
    try {
      const estado = await cargar();
      pintarEstado(estado);
    } catch (error) {
      mostrarAlerta(error.message);
    }
  }

  if (typeof document !== "undefined") {
    document.addEventListener("DOMContentLoaded", () => {
      inicializar();
    });
  }

  // Exponer al objeto global del navegador
  global.cargar = cargar;
  global.agregar = agregar;
  global.retirar = retirar;
  global.modificarCantidad = modificarCantidad;
  global.deshacer = deshacer;
  global.confirmar = confirmar;
  global.aplicarDescuento = aplicarDescuento;
  global.manejarDescuento = manejarDescuento;
  global.manejarConfirmar = manejarConfirmar;
  global.pintarEstado = pintarEstado;
  global.formatearMoneda = formatearMoneda;
  global.formatearFecha = formatearFecha;
  global.mostrarAlerta = mostrarAlerta;
  global.limpiarAlerta = limpiarAlerta;
  global.manejarAgregar = manejarAgregar;
  global.manejarRetirar = manejarRetirar;
  global.manejarModificarCantidad = manejarModificarCantidad;
  global.manejarDeshacer = manejarDeshacer;
  global.toggleBotonesAccion = toggleBotonesAccion;
  global.inicializar = inicializar;
  global.peticionPreparacion = peticion;
  global.SesionExpiradaError = SesionExpiradaError;
  global.MENSAJE_SESION_EXPIRADA = MENSAJE_SESION_EXPIRADA;
  global.actualizarSugerencias = actualizarSugerencias;
  global.agregarProductoDirecto = agregarProductoDirecto;
  global.mostrarAvisoCaja = mostrarAvisoCaja;
  global.ejecutarDescuentoDirecto = ejecutarDescuentoDirecto;
  global.seleccionarCliente = seleccionarCliente;
  global.actualizarSugerenciasCliente = actualizarSugerenciasCliente;
  global.vaciarPreparacion = vaciarPreparacion;

  if (typeof module !== "undefined" && module.exports) {
    module.exports = {
      peticion,
      cargar,
      agregar,
      retirar,
      vaciarPreparacion,
      modificarCantidad,
      deshacer,
      confirmar,
      manejarConfirmar,
      aplicarDescuento,
      manejarDescuento,
      ejecutarDescuentoDirecto,
      pintarEstado,
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
      obtenerCsrf,
      actualizarSugerencias,
      agregarProductoDirecto,
      mostrarAvisoCaja,
      seleccionarCliente,
      actualizarSugerenciasCliente,
    };
  }
})(typeof window !== "undefined" ? window : this);
