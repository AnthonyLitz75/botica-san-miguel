const formularioRecepcion = document.getElementById("formulario-recepcion");

if (formularioRecepcion) {
    const filas = [...formularioRecepcion.querySelectorAll(".recepcion-tabla tbody tr")];
    const resumen = document.getElementById("resumen-seleccion");
    const errorSeleccion = document.getElementById("error-seleccion");

    function actualizarFila(fila) {
        const selector = fila.querySelector(".recepcion-incluir");
        const campos = [...fila.querySelectorAll("[data-campo]")];
        const cantidad = fila.querySelector(".recepcion-cantidad");
        const activa = selector.checked;

        fila.classList.toggle("linea-seleccionada", activa);
        campos.forEach((campo) => {
            campo.disabled = !activa;
            campo.required = activa && campo.dataset.campo !== "idDetalleOrden";
        });
        actualizarSaldo(fila);
        actualizarResumen();
    }

    function actualizarSaldo(fila) {
        const saldo = Number(fila.dataset.pendiente || 0);
        const cantidad = Number(fila.querySelector(".recepcion-cantidad")?.value || 0);
        fila.querySelector("[data-restante]").textContent = String(Math.max(0, saldo - cantidad));
    }

    function actualizarResumen() {
        const seleccionadas = filas.filter((fila) => fila.querySelector(".recepcion-incluir").checked);
        const unidades = seleccionadas.reduce((total, fila) => {
            return total + Number(fila.querySelector(".recepcion-cantidad").value || 0);
        }, 0);
        resumen.textContent = `${seleccionadas.length} ${seleccionadas.length === 1 ? "producto seleccionado" : "productos seleccionados"}`
            + (unidades ? ` · ${unidades} ${unidades === 1 ? "unidad" : "unidades"}` : "");
    }

    filas.forEach((fila) => {
        const selector = fila.querySelector(".recepcion-incluir");
        selector.addEventListener("change", () => actualizarFila(fila));
        fila.querySelector(".recepcion-cantidad").addEventListener("input", () => {
            actualizarSaldo(fila);
            actualizarResumen();
        });
        actualizarFila(fila);
    });

    formularioRecepcion.addEventListener("submit", (evento) => {
        const seleccionadas = filas.filter((fila) => fila.querySelector(".recepcion-incluir").checked);
        if (seleccionadas.length === 0) {
            evento.preventDefault();
            errorSeleccion.hidden = false;
            filas[0]?.querySelector(".recepcion-incluir").focus();
            return;
        }

        errorSeleccion.hidden = true;
        seleccionadas.forEach((fila, indice) => {
            fila.querySelectorAll("[data-campo]").forEach((campo) => {
                campo.name = `detalles[${indice}].${campo.dataset.campo}`;
            });
        });

        const submitBtn = formularioRecepcion.querySelector('button[type="submit"]');
        if (submitBtn && !submitBtn.disabled) {
            setTimeout(() => {
                submitBtn.disabled = true;
                submitBtn.classList.add("opacity-80", "cursor-wait");
                submitBtn.innerHTML = `
                    <svg class="animate-spin -ml-1 mr-2 h-4 w-4 inline-block text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                    </svg>
                    <span>Guardando recepción...</span>
                `;
            }, 0);
        }
    });
}
