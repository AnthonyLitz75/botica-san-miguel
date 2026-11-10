/**
 * preparacion-venta.js
 * Comunicación con la API REST de sesión /ventas/preparacion,
 * renderizado seguro del DOM (sin innerHTML) y control de acciones con bloqueo concurrente.
 */
(function (global) {
    'use strict';

    let estadoActual = null;

    function obtenerCsrf() {
        const tokenMeta = document.querySelector('meta[name="_csrf"]');
        const headerMeta = document.querySelector('meta[name="_csrf_header"]');
        return {
            token: tokenMeta ? tokenMeta.getAttribute('content') : '',
            header: headerMeta ? headerMeta.getAttribute('content') : 'X-CSRF-TOKEN'
        };
    }

    async function peticion(url, opciones = {}) {
        const csrf = obtenerCsrf();
        const headers = {
            'Accept': 'application/json',
            ...(opciones.headers || {})
        };

        if (opciones.method && opciones.method !== 'GET') {
            headers['Content-Type'] = 'application/json';
            if (csrf.token) {
                headers[csrf.header] = csrf.token;
            }
        }

        const respuesta = await fetch(url, {
            credentials: 'same-origin',
            ...opciones,
            headers
        });

        if (!respuesta.ok) {
            let mensaje = `Error del servidor (${respuesta.status})`;
            try {
                const datosError = await respuesta.json();
                if (datosError && datosError.mensaje) {
                    mensaje = datosError.mensaje;
                }
            } catch (e) {
                if (respuesta.status === 401 || respuesta.status === 403) {
                    mensaje = 'Sesión expirada o permisos insuficientes.';
                }
            }
            throw new Error(mensaje);
        }

        return await respuesta.json();
    }

    async function cargar() {
        return peticion('/ventas/preparacion', { method: 'GET' });
    }

    async function agregar(idMedicamento, cantidad) {
        return peticion('/ventas/preparacion', {
            method: 'POST',
            body: JSON.stringify({
                idMedicamento: Number(idMedicamento),
                cantidad: Number(cantidad)
            })
        });
    }

    async function retirar(idMedicamento) {
        return peticion(`/ventas/preparacion/${idMedicamento}`, {
            method: 'DELETE'
        });
    }

    async function deshacer() {
        return peticion('/ventas/preparacion/deshacer', {
            method: 'POST'
        });
    }

    function formatearMoneda(valor) {
        const numero = Number(valor) || 0;
        return new Intl.NumberFormat('es-PE', {
            style: 'currency',
            currency: 'PEN'
        }).format(numero);
    }

    function formatearFecha(fechaIso) {
        if (!fechaIso) return 'N/D';
        const partes = String(fechaIso).split('-');
        if (partes.length === 3) {
            return `${partes[2]}/${partes[1]}/${partes[0]}`;
        }
        return String(fechaIso);
    }

    function mostrarAlerta(mensaje) {
        const zonaAlertas = document.getElementById('zona-alertas');
        const textoAlerta = document.getElementById('texto-alerta');
        if (zonaAlertas && textoAlerta) {
            textoAlerta.textContent = mensaje || 'Ocurrió un error inesperado.';
            zonaAlertas.classList.remove('hidden');
        }
    }

    function limpiarAlerta() {
        const zonaAlertas = document.getElementById('zona-alertas');
        const textoAlerta = document.getElementById('texto-alerta');
        if (zonaAlertas && textoAlerta) {
            textoAlerta.textContent = '';
            zonaAlertas.classList.add('hidden');
        }
    }

    /**
     * Bloquea o desbloquea los botones de acción durante peticiones activas (RF-007).
     */
    function toggleBotonesAccion(bloqueado) {
        const botonAgregar = document.getElementById('boton-agregar');
        const botonDeshacer = document.getElementById('boton-deshacer');
        const botonesRetirar = document.querySelectorAll('.boton-retirar');

        if (botonAgregar) {
            botonAgregar.disabled = bloqueado;
        }

        botonesRetirar.forEach(btn => {
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

        const estadoVacio = document.getElementById('estado-vacio');
        const contenedorTabla = document.getElementById('contenedor-tabla');
        const cuerpoLineas = document.getElementById('cuerpo-lineas');
        const totalVenta = document.getElementById('total-venta');
        const botonDeshacer = document.getElementById('boton-deshacer');

        const detalles = estado.detalles || [];
        const tieneLineas = detalles.length > 0;

        // Total en soles (RF-006)
        if (totalVenta) {
            totalVenta.textContent = formatearMoneda(estado.total);
        }

        // Botón Deshacer habilitado según puedeDeshacer (CA-4.2)
        if (botonDeshacer) {
            botonDeshacer.disabled = !estado.puedeDeshacer;
        }

        // Alternancia Estado Vacío (CA-1.1) vs Tabla de Líneas (CA-1.2)
        if (estadoVacio) {
            if (tieneLineas) {
                estadoVacio.classList.add('hidden');
            } else {
                estadoVacio.classList.remove('hidden');
            }
        }

        if (contenedorTabla) {
            if (tieneLineas) {
                contenedorTabla.classList.remove('hidden');
            } else {
                contenedorTabla.classList.add('hidden');
            }
        }

        // Renderizado seguro de filas mediante nodos DOM y textContent (RNF-002)
        if (cuerpoLineas) {
            while (cuerpoLineas.firstChild) {
                cuerpoLineas.removeChild(cuerpoLineas.firstChild);
            }

            detalles.forEach(linea => {
                const tr = document.createElement('tr');
                tr.dataset.idMedicamento = linea.idMedicamento;

                // Código
                const tdCodigo = document.createElement('td');
                const spanCodigo = document.createElement('span');
                spanCodigo.className = 'codigo-medicamento font-mono text-xs';
                spanCodigo.textContent = linea.codigo;
                tdCodigo.appendChild(spanCodigo);
                tr.appendChild(tdCodigo);

                // Nombre
                const tdNombre = document.createElement('td');
                tdNombre.className = 'font-medium text-slate-800';
                tdNombre.textContent = linea.nombre;
                tr.appendChild(tdNombre);

                // Cantidad solicitada
                const tdCantidad = document.createElement('td');
                tdCantidad.className = 'text-center font-semibold text-slate-700';
                tdCantidad.textContent = String(linea.cantidad);
                tr.appendChild(tdCantidad);

                // Precio Unitario
                const tdPrecio = document.createElement('td');
                tdPrecio.className = 'text-right text-slate-600';
                tdPrecio.textContent = formatearMoneda(linea.precioUnitario);
                tr.appendChild(tdPrecio);

                // Importe total de la línea
                const tdImporte = document.createElement('td');
                tdImporte.className = 'text-right font-bold text-slate-900';
                tdImporte.textContent = formatearMoneda(linea.importe);
                tr.appendChild(tdImporte);

                // Desglose de Lotes FEFO (CA-2.4: número de lote, fecha de vencimiento y cantidad asignada)
                const tdLotes = document.createElement('td');
                tdLotes.className = 'text-xs text-slate-600';

                const lotes = linea.lotesFefo || [];
                if (lotes.length > 0) {
                    const listaLotes = document.createElement('div');
                    listaLotes.className = 'space-y-1.5';

                    lotes.forEach(lote => {
                        const itemLote = document.createElement('div');
                        itemLote.className = 'fefo-lote-item flex flex-wrap items-center gap-1.5 py-0.5';

                        // Badge de número de lote
                        const badgeLote = document.createElement('span');
                        badgeLote.className = 'font-mono text-[11px] font-semibold bg-emerald-50 text-emerald-800 border border-emerald-200 px-1.5 py-0.5 rounded shadow-2xs';
                        badgeLote.textContent = lote.numeroLote;
                        itemLote.appendChild(badgeLote);

                        // Fecha de vencimiento formateada DD/MM/AAAA
                        const textoVencimiento = document.createElement('span');
                        textoVencimiento.className = 'text-[11px] text-slate-500';
                        textoVencimiento.textContent = `(Vence: ${formatearFecha(lote.fechaVencimiento)})`;
                        itemLote.appendChild(textoVencimiento);

                        // Cantidad asignada según FEFO
                        const badgeCantidad = document.createElement('span');
                        badgeCantidad.className = 'inline-flex items-center text-[11px] font-bold text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded border border-emerald-100';
                        badgeCantidad.textContent = `${lote.cantidadAsignada} un.`;
                        itemLote.appendChild(badgeCantidad);

                        listaLotes.appendChild(itemLote);
                    });

                    tdLotes.appendChild(listaLotes);
                } else {
                    const sinLotes = document.createElement('span');
                    sinLotes.className = 'text-amber-600 italic text-xs';
                    sinLotes.textContent = 'Sin lotes asignados';
                    tdLotes.appendChild(sinLotes);
                }
                tr.appendChild(tdLotes);

                // Acciones: botón Retirar (US-3, CA-3.1)
                const tdAcciones = document.createElement('td');
                tdAcciones.className = 'text-center';
                const btnRetirar = document.createElement('button');
                btnRetirar.type = 'button';
                btnRetirar.className = 'boton-retirar inventario-boton boton-secundario text-xs py-1 px-2 text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200 transition-all';
                btnRetirar.dataset.id = linea.idMedicamento;
                btnRetirar.textContent = 'Retirar';
                tdAcciones.appendChild(btnRetirar);
                tr.appendChild(tdAcciones);

                cuerpoLineas.appendChild(tr);
            });
        }
    }

    async function manejarAgregar(evento) {
        if (evento) evento.preventDefault();
        limpiarAlerta();

        const selectMedicamento = document.getElementById('select-medicamento');
        const inputCantidad = document.getElementById('input-cantidad');

        if (!selectMedicamento || !inputCantidad) return;

        const idMedicamento = selectMedicamento.value;
        const cantidad = inputCantidad.value;

        if (!idMedicamento) {
            mostrarAlerta('Por favor selecciona un medicamento de la lista.');
            return;
        }

        const cantNum = parseInt(cantidad, 10);
        if (isNaN(cantNum) || cantNum < 1) {
            mostrarAlerta('La cantidad debe ser un número entero mayor o igual a 1.');
            return;
        }

        toggleBotonesAccion(true);
        try {
            const nuevoEstado = await agregar(idMedicamento, cantNum);
            pintarEstado(nuevoEstado);
            inputCantidad.value = '1';
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

    function configurarEventos() {
        const formAgregar = document.getElementById('formulario-agregar');
        const botonAgregar = document.getElementById('boton-agregar');
        const botonDeshacer = document.getElementById('boton-deshacer');
        const cuerpoLineas = document.getElementById('cuerpo-lineas');
        const selectMedicamento = document.getElementById('select-medicamento');
        const inputCantidad = document.getElementById('input-cantidad');

        if (formAgregar) {
            formAgregar.addEventListener('submit', manejarAgregar);
        }
        if (botonAgregar) {
            botonAgregar.addEventListener('click', manejarAgregar);
        }
        if (botonDeshacer) {
            botonDeshacer.addEventListener('click', manejarDeshacer);
        }

        // Delegación de eventos para botones de retirar en la tabla
        if (cuerpoLineas) {
            cuerpoLineas.addEventListener('click', (evento) => {
                const boton = evento.target.closest('.boton-retirar');
                if (boton && boton.dataset.id) {
                    manejarRetirar(boton.dataset.id);
                }
            });
        }

        // Limpieza de alerta al interactuar con campos
        if (selectMedicamento) {
            selectMedicamento.addEventListener('change', limpiarAlerta);
        }
        if (inputCantidad) {
            inputCantidad.addEventListener('input', limpiarAlerta);
        }
    }

    async function inicializar() {
        configurarEventos();
        try {
            const estado = await cargar();
            pintarEstado(estado);
        } catch (error) {
            mostrarAlerta(error.message);
        }
    }

    if (typeof document !== 'undefined') {
        document.addEventListener('DOMContentLoaded', () => {
            inicializar();
        });
    }

    // Exponer al objeto global del navegador
    global.cargar = cargar;
    global.agregar = agregar;
    global.retirar = retirar;
    global.deshacer = deshacer;
    global.pintarEstado = pintarEstado;
    global.formatearMoneda = formatearMoneda;
    global.formatearFecha = formatearFecha;
    global.mostrarAlerta = mostrarAlerta;
    global.limpiarAlerta = limpiarAlerta;
    global.manejarAgregar = manejarAgregar;
    global.manejarRetirar = manejarRetirar;
    global.manejarDeshacer = manejarDeshacer;
    global.toggleBotonesAccion = toggleBotonesAccion;
    global.inicializar = inicializar;
    global.peticionPreparacion = peticion;

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {
            peticion, cargar, agregar, retirar, deshacer,
            pintarEstado, formatearMoneda, formatearFecha,
            mostrarAlerta, limpiarAlerta,
            manejarAgregar, manejarRetirar, manejarDeshacer,
            toggleBotonesAccion, inicializar, obtenerCsrf
        };
    }
})(typeof window !== 'undefined' ? window : this);
