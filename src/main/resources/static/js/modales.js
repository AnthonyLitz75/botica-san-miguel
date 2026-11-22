/**
 * Módulo de Control de Diálogos Modales Accesibles (Spec 015 - Bocetos 14 a 19).
 * Botica San Miguel - Vanilla JS con soporte WCAG 2.1 AA.
 * Refactorizado: Consume core/validador-docs.js (Principio DRY)
 */
document.addEventListener('DOMContentLoaded', async () => {
    // Carga modular dinámica del núcleo de validación
    const { aplicarEstadoVisualDocumento } = await import('./core/validador-docs.js');

    let modalActivo = null;
    let ultimoElementoFoco = null;

    /**
     * Abre un modal por su ID, gestionando foco y estado accesible.
     * @param {string} modalId 
     * @param {HTMLElement} disparador 
     */
    function abrirModal(modalId, disparador = null) {
        const modal = document.getElementById(modalId);
        if (!modal) return;

        ultimoElementoFoco = disparador || document.activeElement;
        modalActivo = modal;

        modal.classList.add('activo');
        modal.setAttribute('aria-hidden', 'false');
        document.body.style.overflow = 'hidden'; // Evita scroll de fondo

        // Foco al primer control editable (o botón cancelar si es confirmación)
        if (modalId === 'modal-confirmar') {
            const btnCancelar = modal.querySelector('#modal-confirmar-cancelar');
            if (btnCancelar) {
                setTimeout(() => btnCancelar.focus(), 50);
                return;
            }
        }

        const primerControl = modal.querySelector('input:not([type="hidden"]), select, textarea, button:not(.btn-cerrar-modal)');
        if (primerControl) {
            setTimeout(() => primerControl.focus(), 50);
        }
    }

    /**
     * Cierra el modal activo y devuelve el foco al disparador original.
     */
    function cerrarModalActivo() {
        if (!modalActivo) return;

        modalActivo.classList.remove('activo');
        modalActivo.setAttribute('aria-hidden', 'true');
        document.body.style.overflow = '';

        if (formularioPendienteConfirmacion) {
            formularioPendienteConfirmacion = null;
        }

        if (ultimoElementoFoco && typeof ultimoElementoFoco.focus === 'function') {
            ultimoElementoFoco.focus();
        }

        modalActivo = null;
    }

    // Variable para rastrear el formulario pendiente de confirmación (Spec 023)
    let formularioPendienteConfirmacion = null;

    // Interceptar clics en disparadores de modales (Progressive Enhancement)
    document.addEventListener('click', (e) => {
        // Confirmar acción en modal de confirmación
        if (e.target.closest('#modal-confirmar-accion')) {
            e.preventDefault();
            const formulario = formularioPendienteConfirmacion;
            cerrarModalActivo();
            if (formulario) {
                formulario._confirmado = true;
                if (typeof formulario.requestSubmit === 'function') {
                    formulario.requestSubmit();
                } else {
                    formulario.submit();
                }
            }
            return;
        }

        // Cancelar acción en modal de confirmación
        if (e.target.closest('#modal-confirmar-cancelar')) {
            e.preventDefault();
            cerrarModalActivo();
            return;
        }

        const disparador = e.target.closest('[data-modal-open]');
        if (disparador) {
            const modalId = disparador.getAttribute('data-modal-open');
            const modal = document.getElementById(modalId);
            if (modal) {
                e.preventDefault(); // Evita navegación a la página /nuevo si el modal existe en DOM
                abrirModal(modalId, disparador);
                return;
            }
        }

        // Cierre al pulsar botones cancelar o la X
        if (e.target.closest('.btn-cerrar-modal') || e.target.closest('[data-modal-close]')) {
            e.preventDefault();
            cerrarModalActivo();
            return;
        }

        // Cierre al hacer clic en el backdrop fuera de .md
        if (modalActivo && e.target === modalActivo) {
            cerrarModalActivo();
        }
    });

    // Accesibilidad con teclado: Escape y trampa de foco (Focus Trap)
    document.addEventListener('keydown', (e) => {
        if (!modalActivo) return;

        if (e.key === 'Escape') {
            e.preventDefault();
            cerrarModalActivo();
            return;
        }

        if (e.key === 'Tab') {
            const elementosEnfocables = modalActivo.querySelectorAll(
                'button:not([disabled]), [href], input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
            );
            if (elementosEnfocables.length === 0) return;

            const primerElemento = elementosEnfocables[0];
            const ultimoElemento = elementosEnfocables[elementosEnfocables.length - 1];

            if (e.shiftKey) {
                if (document.activeElement === primerElemento) {
                    e.preventDefault();
                    ultimoElemento.focus();
                }
            } else {
                if (document.activeElement === ultimoElemento) {
                    e.preventDefault();
                    primerElemento.focus();
                }
            }
        }
    });

    // Toggle segmentado DNI / RUC en modales (Boceto 14)
    document.addEventListener('click', (e) => {
        const btnToggle = e.target.closest('.btn-toggle-doc');
        if (!btnToggle) return;
        const contenedor = btnToggle.closest('.seg');
        if (!contenedor) return;
        const tipo = btnToggle.dataset.tipo;
        const modal = btnToggle.closest('.md, form');
        const inputHidden = modal ? modal.querySelector('#modal-tipoDocumento, input[name="tipoDocumento"]') : null;
        if (inputHidden) {
            inputHidden.value = tipo;
        }
        contenedor.querySelectorAll('.btn-toggle-doc').forEach((btn) => {
            const activo = btn === btnToggle;
            btn.classList.toggle('on', activo);
            btn.setAttribute('aria-pressed', activo ? 'true' : 'false');
        });

        // Actualizar validación de número de documento si existe
        const inputNum = modal ? modal.querySelector('#modal-numeroDocumento, #numeroDocumento') : null;
        if (inputNum) {
            const contenedorHelp = modal.querySelector(`[data-help-for="${inputNum.id}"]`);
            if (tipo === 'DNI') {
                inputNum.setAttribute('maxlength', '8');
                inputNum.setAttribute('data-valida', 'dni');
                inputNum.setAttribute('placeholder', '8 dígitos para DNI');
                if (contenedorHelp) contenedorHelp.textContent = '8 dígitos numéricos';
            } else {
                inputNum.setAttribute('maxlength', '11');
                inputNum.setAttribute('data-valida', 'ruc');
                inputNum.setAttribute('placeholder', '11 dígitos para RUC');
                if (contenedorHelp) contenedorHelp.textContent = '11 dígitos (RUC)';
            }
            inputNum.dispatchEvent(new Event('input', { bubbles: true }));
        }
    });

    // Validación interactiva en tiempo real para DNI / RUC según bocetos (reutiliza core/validador-docs.js)
    document.addEventListener('input', (e) => {
        const input = e.target;

        if (input.matches('[data-valida="ruc"]')) {
            const contenedorHelp = input.closest('.mb, form')?.querySelector(`[data-help-for="${input.id}"]`);
            aplicarEstadoVisualDocumento(input, contenedorHelp, 'RUC');
        } else if (input.matches('[data-valida="dni"]')) {
            const contenedorHelp = input.closest('.mb, form')?.querySelector(`[data-help-for="${input.id}"]`);
            aplicarEstadoVisualDocumento(input, contenedorHelp, 'DNI');
        }
    });

    // Manejo de envío accesible e interactivo para formularios en modales
    function limpiarErroresModal(modal) {
        modal.querySelectorAll('.inp.err').forEach(el => el.classList.remove('err'));
        modal.querySelectorAll('[aria-invalid="true"]').forEach(el => el.removeAttribute('aria-invalid'));
        modal.querySelectorAll('.help.e.error-contacto, [data-error-dinamico="true"]').forEach(el => el.remove());
    }

    function mostrarErroresModal(modal, errores) {
        let primerInputConError = null;

        for (const [campo, mensaje] of Object.entries(errores)) {
            const input = modal.querySelector(`[name="${campo}"]`) ||
                          modal.querySelector(`#modal-${campo}`) ||
                          modal.querySelector(`#modal-${campo}-prov`);
            if (!input) continue;

            if (!primerInputConError) {
                primerInputConError = input;
            }

            const contenedorInp = input.closest('.inp');
            if (contenedorInp) {
                contenedorInp.classList.add('err');
            }

            input.setAttribute('aria-invalid', 'true');
            const errorId = `error-${input.id || campo}`;
            input.setAttribute('aria-describedby', errorId);

            const padre = (contenedorInp && contenedorInp.parentElement) || input.parentElement;
            let contenedorError = padre.querySelector(`#${errorId}`);
            if (!contenedorError) {
                contenedorError = document.createElement('div');
                contenedorError.id = errorId;
                contenedorError.className = 'help e error-contacto';
                contenedorError.setAttribute('role', 'alert');
                contenedorError.setAttribute('data-error-dinamico', 'true');
                padre.appendChild(contenedorError);
            }
            contenedorError.textContent = mensaje;
            contenedorError.style.display = 'block';
        }

        if (primerInputConError) {
            primerInputConError.focus();
        }
    }

    // Intercepción de formularios con confirmación previa (Spec 023)
    document.addEventListener('submit', (e) => {
        const formulario = e.target;
        const disparador = e.submitter || document.activeElement;
        const titulo = disparador?.getAttribute?.('data-confirmar-titulo') || formulario?.getAttribute?.('data-confirmar-titulo');

        if (!titulo) return;

        if (formulario._confirmado) {
            delete formulario._confirmado;
            return; // Permite el envío real
        }

        e.preventDefault();

        const modalConfirmar = document.getElementById('modal-confirmar');
        if (!modalConfirmar) {
            // Si el modal no existe en el DOM, permitir degradación progresiva
            formulario._confirmado = true;
            if (typeof formulario.requestSubmit === 'function') {
                formulario.requestSubmit();
            } else {
                formulario.submit();
            }
            return;
        }

        const mensaje = disparador?.getAttribute?.('data-confirmar-mensaje') || formulario?.getAttribute?.('data-confirmar-mensaje') || 'Esta acción no se puede deshacer.';
        const boton = disparador?.getAttribute?.('data-confirmar-boton') || formulario?.getAttribute?.('data-confirmar-boton') || 'Desactivar';

        const elTitulo = modalConfirmar.querySelector('#modal-confirmar-titulo');
        const elMensaje = modalConfirmar.querySelector('#modal-confirmar-mensaje');
        const elBoton = modalConfirmar.querySelector('#modal-confirmar-accion');

        if (elTitulo) elTitulo.textContent = titulo;
        if (elMensaje) elMensaje.textContent = mensaje;
        if (elBoton) elBoton.textContent = boton;

        formularioPendienteConfirmacion = formulario;
        abrirModal('modal-confirmar', disparador);
    }, true);

    document.addEventListener('submit', async (e) => {
        const formulario = e.target;
        const modal = formulario.closest('#modal-nuevo-cliente, #modal-nuevo-proveedor, [data-ajax-modal]');
        if (!modal || formulario.hasAttribute('data-no-ajax')) return;

        e.preventDefault();
        limpiarErroresModal(modal);

        const url = formulario.action || window.location.href;
        const formData = new FormData(formulario);
        const headers = {
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': 'application/json'
        };

        const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
        const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        if (csrfTokenMeta && csrfHeaderMeta) {
            headers[csrfHeaderMeta.getAttribute('content')] = csrfTokenMeta.getAttribute('content');
        }

        const btnSubmit = formulario.querySelector('button[type="submit"]');
        if (btnSubmit) btnSubmit.disabled = true;

        try {
            const respuesta = await fetch(url, {
                method: 'POST',
                headers,
                body: formData
            });

            if (respuesta.ok) {
                const datos = await respuesta.json().catch(() => ({}));
                cerrarModalActivo();
                window.location.href = datos.url || formulario.action || window.location.href;
                return;
            }

            const datos = await respuesta.json().catch(() => ({}));
            const errores = datos.errores || datos.campos || {};
            if (Object.keys(errores).length > 0) {
                mostrarErroresModal(modal, errores);
            } else if (datos.mensaje || datos.error) {
                // Si es un error general, mapearlo al campo correspondiente o alertar
                const mensajeGeneral = datos.mensaje || datos.error;
                if (mensajeGeneral.toLowerCase().includes('documento') || mensajeGeneral.toLowerCase().includes('ruc') || mensajeGeneral.toLowerCase().includes('dni')) {
                    mostrarErroresModal(modal, { numeroDocumento: mensajeGeneral, ruc: mensajeGeneral });
                }
            }
        } catch (err) {
            console.error('Error al procesar formulario modal:', err);
        } finally {
            if (btnSubmit) btnSubmit.disabled = false;
        }
    });

    // Exponer API global simple si se requiere invocar programáticamente
    window.BoticaModales = {
        abrir: abrirModal,
        cerrar: cerrarModalActivo
    };
});
