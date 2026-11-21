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

    // Exponer API global simple si se requiere invocar programáticamente
    window.BoticaModales = {
        abrir: abrirModal,
        cerrar: cerrarModalActivo
    };
});
