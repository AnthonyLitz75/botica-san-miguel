/**
 * Módulo de Control de Diálogos Modales Accesibles (Spec 015 - Bocetos 14 a 19).
 * Botica San Miguel - Vanilla JS con soporte WCAG 2.1 AA.
 */
document.addEventListener('DOMContentLoaded', () => {
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

        // Foco al primer control editable
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

        if (ultimoElementoFoco && typeof ultimoElementoFoco.focus === 'function') {
            ultimoElementoFoco.focus();
        }

        modalActivo = null;
    }

    // Interceptar clics en disparadores de modales (Progressive Enhancement)
    document.addEventListener('click', (e) => {
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

    // Validación interactiva en tiempo real para DNI / RUC según bocetos
    document.addEventListener('input', (e) => {
        const input = e.target;

        // Validación interactiva de RUC (Modal 15 y Cliente)
        if (input.matches('[data-valida="ruc"]')) {
            const contenedorInp = input.closest('.inp') || input;
            const contenedorHelp = input.closest('.mb, form')?.querySelector(`[data-help-for="${input.id}"]`);
            const val = input.value.trim();

            if (val.length === 0) {
                contenedorInp.classList.remove('err', 'ok2');
                if (contenedorHelp) {
                    contenedorHelp.className = 'help';
                    contenedorHelp.textContent = '11 dígitos numéricos';
                }
            } else if (!/^\d+$/.test(val) || val.length !== 11) {
                contenedorInp.classList.add('err');
                contenedorInp.classList.remove('ok2');
                if (contenedorHelp) {
                    contenedorHelp.className = 'help e';
                    contenedorHelp.textContent = `El RUC debe tener 11 dígitos numéricos (ingresaste ${val.length}).`;
                }
            } else {
                contenedorInp.classList.remove('err');
                contenedorInp.classList.add('ok2');
                if (contenedorHelp) {
                    contenedorHelp.className = 'help';
                    contenedorHelp.textContent = '11 dígitos · validado';
                }
            }
        }

        // Validación interactiva de DNI
        if (input.matches('[data-valida="dni"]')) {
            const contenedorInp = input.closest('.inp') || input;
            const contenedorHelp = input.closest('.mb, form')?.querySelector(`[data-help-for="${input.id}"]`);
            const val = input.value.trim();

            if (val.length === 0) {
                contenedorInp.classList.remove('err', 'ok2');
                if (contenedorHelp) {
                    contenedorHelp.className = 'help';
                    contenedorHelp.textContent = '8 dígitos numéricos';
                }
            } else if (!/^\d+$/.test(val) || val.length !== 8) {
                contenedorInp.classList.add('err');
                contenedorInp.classList.remove('ok2');
                if (contenedorHelp) {
                    contenedorHelp.className = 'help e';
                    contenedorHelp.textContent = `El DNI debe tener 8 dígitos numéricos (ingresaste ${val.length}).`;
                }
            } else {
                contenedorInp.classList.remove('err');
                contenedorInp.classList.add('ok2');
                if (contenedorHelp) {
                    contenedorHelp.className = 'help';
                    contenedorHelp.textContent = '8 dígitos · validado';
                }
            }
        }
    });

    // Exponer API global simple si se requiere invocar programáticamente
    window.BoticaModales = {
        abrir: abrirModal,
        cerrar: cerrarModalActivo
    };
});
