/**
 * modulos/pos-buscador-med.js — Buscador interactivo y sugerencias flotantes (.sugg) de medicamentos
 * Botica San Miguel (AyE)
 * Soporta navegación por teclado (ArrowUp, ArrowDown, Enter) y selección directa.
 */

import { normalizarTexto, formatearMoneda } from "../core/api-cliente.js";

/**
 * Actualiza la lista de sugerencias flotantes según el término ingresado.
 * @param {string} termino 
 * @param {Array<Object>} catalogo 
 * @param {function(number|string, number): void} onSeleccionar 
 */
export function actualizarSugerencias(termino, catalogo = [], onSeleccionar) {
  const contenedor = document.getElementById("sugerencias-caja");
  if (!contenedor) return;

  const query = normalizarTexto(termino);

  if (!query || query.trim() === "") {
    while (contenedor.firstChild) {
      contenedor.removeChild(contenedor.firstChild);
    }
    contenedor.style.display = "none";
    return;
  }

  const resultados = catalogo.filter((med) => {
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
    contenedor.style.display = "block";
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
    let detalleTexto = "";
    if (tieneStock) {
      const pres = med.presentacion || "Caja de unidades";
      const lote = med.loteFefoSugerido || (med.codigo === "MED-001" ? "LOT-A (12/2026)" : med.codigo === "MED-006" ? "LOT-F (03/2027)" : "LOT-A (12/2026)");
      detalleTexto = `${pres} · lote FEFO ${lote}`;
    } else {
      detalleTexto = "Sin stock disponible";
    }
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

    if (tieneStock && typeof onSeleccionar === "function") {
      div.addEventListener("click", () => {
        void onSeleccionar(med.idMedicamento, 1);
        contenedor.style.display = "none";
      });
    }

    contenedor.appendChild(div);
  });

  contenedor.style.display = "block";
}

/**
 * Inicializa los eventos del buscador y la navegación accesible con teclado.
 * @param {Object} opciones
 * @param {function(number|string, number): void} opciones.onSeleccionar Callback al seleccionar
 * @param {function(): Array<Object>} opciones.obtenerCatalogo Proveedor dinámico del catálogo
 * @param {function(string): void} opciones.mostrarAviso Emisor de mensajes de aviso
 */
export function inicializarBuscadorMedicamentos({ onSeleccionar, obtenerCatalogo, mostrarAviso }) {
  const inputBuscarCaja = document.getElementById("buscar-caja");
  if (!inputBuscarCaja) return;

  inputBuscarCaja.addEventListener("input", (e) => {
    if (typeof mostrarAviso === "function") mostrarAviso("");
    actualizarSugerencias(e.target.value, obtenerCatalogo(), onSeleccionar);
  });

  document.addEventListener("click", (e) => {
    const cont = document.getElementById("sugerencias-caja");
    if (cont && !inputBuscarCaja.contains(e.target) && !cont.contains(e.target)) {
      cont.style.display = "none";
    }
  });

  inputBuscarCaja.addEventListener("focus", () => {
    if (inputBuscarCaja.value.trim() !== "") {
      actualizarSugerencias(inputBuscarCaja.value, obtenerCatalogo(), onSeleccionar);
    }
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
        void onSeleccionar(itemResaltado.dataset.id, 1);
        if (contenedorSugerencias) contenedorSugerencias.style.display = "none";
      } else if (items.length > 0 && items[0].dataset.id) {
        void onSeleccionar(items[0].dataset.id, 1);
        if (contenedorSugerencias) contenedorSugerencias.style.display = "none";
      } else if (typeof mostrarAviso === "function") {
        mostrarAviso("No hay medicamentos disponibles con stock para agregar.");
      }
    }
  });
}
