/**
 * modulos/pos-buscador-cli.js — Buscador predictivo de clientes y tarjeta activa (.cli)
 * Botica San Miguel (AyE)
 * Vincula el input de mostrador, el select oculto y los datos de tarjeta del cliente.
 */

import { normalizarTexto } from "../core/api-cliente.js";

/**
 * Asigna el cliente seleccionado a los controles y tarjeta informativa de la vista.
 * @param {Object|null} cliente 
 */
export function seleccionarCliente(cliente) {
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
      const doc = `${cliente.tipoDocumento ? cliente.tipoDocumento + " " : ""}${cliente.numeroDocumento || ""}`.trim();
      const compras = Number(cliente.numeroCompras || 0);
      let detalleCompras;
      if (compras === 0) {
        detalleCompras = "Cliente nuevo";
      } else if (compras === 1) {
        detalleCompras = "1 compra";
      } else {
        detalleCompras = `${compras} compras`;
      }
      docMostrado.textContent = doc ? `${doc} · ${detalleCompras}` : detalleCompras;
    }
    if (badgeMostrado) {
      if (cliente.esFrecuente) {
        badgeMostrado.textContent = "• Frecuente";
        badgeMostrado.className = "inline-flex items-center gap-1 bg-emerald-100 text-emerald-800 text-[11px] font-semibold px-2 py-0.5 rounded-full badge t-ok";
        badgeMostrado.style.display = "";
      } else {
        badgeMostrado.textContent = "";
        badgeMostrado.style.display = "none";
      }
    }
  } else {
    if (selectCliente) selectCliente.value = "";
    if (nombreMostrado) nombreMostrado.textContent = "Público general";
    if (docMostrado) docMostrado.textContent = "Sin registro o cliente eventual";
    if (badgeMostrado) {
      badgeMostrado.textContent = "• Mostrador";
      badgeMostrado.className = "inline-flex items-center gap-1 bg-emerald-100 text-emerald-800 text-[11px] font-semibold px-2 py-0.5 rounded-full badge t-ok";
      badgeMostrado.style.display = "";
    }
  }
}

/**
 * Filtra y renderiza las sugerencias de clientes coincidentes.
 * @param {string} termino 
 * @param {Array<Object>} catalogoClientes 
 */
export function actualizarSugerenciasCliente(termino, catalogoClientes = []) {
  const contenedor = document.getElementById("sugerencias-cliente");
  if (!contenedor) return;

  const query = normalizarTexto(termino);
  if (!query) {
    contenedor.style.display = "none";
    seleccionarCliente(null);
    return;
  }

  const resultados = catalogoClientes.filter((c) => {
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

/**
 * Inicializa el buscador de clientes con soporte para teclado y cierre al hacer clic fuera.
 * @param {Object} opciones
 * @param {function(): Array<Object>} opciones.obtenerCatalogoClientes
 */
export function inicializarBuscadorClientes({ obtenerCatalogoClientes }) {
  const inputCliente = document.getElementById("cliente-caja");
  const contenedorSuggCli = document.getElementById("sugerencias-cliente");

  if (!inputCliente) return;

  inputCliente.addEventListener("input", (e) => {
    actualizarSugerenciasCliente(e.target.value, obtenerCatalogoClientes());
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

  const selectCliente = document.getElementById("select-cliente");
  if (selectCliente) {
    selectCliente.addEventListener("change", () => {
      const val = selectCliente.value;
      if (!val) {
        seleccionarCliente(null);
        return;
      }
      const catalogo = typeof obtenerCatalogoClientes === "function" ? obtenerCatalogoClientes() : [];
      const encontrado = catalogo.find((c) => String(c.idCliente) === String(val));
      if (encontrado) {
        seleccionarCliente(encontrado);
      } else {
        const opt = selectCliente.options[selectCliente.selectedIndex];
        if (opt && opt.text) {
          const partes = opt.text.split("—");
          seleccionarCliente({
            idCliente: val,
            nombreCompleto: partes[0] ? partes[0].trim() : opt.text,
            tipoDocumento: "",
            numeroDocumento: partes[1] ? partes[1].trim() : ""
          });
        }
      }
    });
  }
}
