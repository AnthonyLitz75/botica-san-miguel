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
     * Configura el formulario del modal según el disparador (modo crear, editar o subcategoría).
     */
    function configurarFormularioModal(modal, disparador) {
        const form = modal.querySelector('form');
        if (!form) return;

        // Guardar estado original del modal y formulario la primera vez
        const h3Titulo = modal.querySelector('.mh h3');
        if (h3Titulo && !modal.dataset.tituloOriginal) {
            modal.dataset.tituloOriginal = h3Titulo.textContent.trim();
        }
        const btnSubmit = form.querySelector('button[type="submit"]');
        const spanBoton = btnSubmit ? (btnSubmit.querySelector('span') || btnSubmit) : null;
        if (spanBoton && !btnSubmit.dataset.textoOriginal) {
            btnSubmit.dataset.textoOriginal = spanBoton.textContent.trim();
        }
        if (!form.dataset.actionOriginal) {
            form.dataset.actionOriginal = form.getAttribute('action') || form.action;
        }

        limpiarErroresModal(modal);

        const modo = disparador?.dataset?.modo || 'crear';

        if (modo === 'editar') {
            if (disparador.dataset.action) {
                form.action = disparador.dataset.action;
            }
            if (h3Titulo && disparador.dataset.titulo) {
                h3Titulo.textContent = disparador.dataset.titulo;
            }
            if (spanBoton) {
                spanBoton.textContent = disparador.dataset.boton || 'Guardar cambios';
            }

            // --- Medicamentos ---
            const inpCodigoMed = modal.querySelector('#modal-codigo-med');
            if (inpCodigoMed && disparador.dataset.codigo !== undefined) {
                inpCodigoMed.value = disparador.dataset.codigo;
                inpCodigoMed.setAttribute('readonly', 'true');
                inpCodigoMed.classList.add('bg-gray-100', 'cursor-not-allowed');
            }
            const inpNombreMed = modal.querySelector('#modal-nombre-med');
            if (inpNombreMed && disparador.dataset.nombre !== undefined) {
                inpNombreMed.value = disparador.dataset.nombre;
            }
            const inpConcMed = modal.querySelector('#modal-concentracion-med');
            if (inpConcMed && disparador.dataset.concentracion !== undefined) {
                inpConcMed.value = disparador.dataset.concentracion;
            }
            const inpPresMed = modal.querySelector('#modal-presentacion-med');
            if (inpPresMed && disparador.dataset.presentacion !== undefined) {
                inpPresMed.value = disparador.dataset.presentacion;
            }
            const selUnidadMed = modal.querySelector('#modal-unidad-med');
            if (selUnidadMed && disparador.dataset.unidadControl !== undefined) {
                selUnidadMed.value = disparador.dataset.unidadControl;
            }
            const selCatMed = modal.querySelector('#modal-categoria-med');
            if (selCatMed && disparador.dataset.idCategoria !== undefined) {
                selCatMed.value = disparador.dataset.idCategoria || '';
            }
            const inpPrecioMed = modal.querySelector('#modal-precio-med');
            if (inpPrecioMed && disparador.dataset.precioVenta !== undefined) {
                inpPrecioMed.value = disparador.dataset.precioVenta;
            }
            const inpStockMinMed = modal.querySelector('#modal-stockMinimo-med');
            if (inpStockMinMed && disparador.dataset.stockMinimo !== undefined) {
                inpStockMinMed.value = disparador.dataset.stockMinimo;
            }

            // --- Clientes ---
            const inpTipoDocCli = modal.querySelector('#modal-tipoDocumento');
            if (inpTipoDocCli && disparador.dataset.tipoDocumento !== undefined) {
                inpTipoDocCli.value = disparador.dataset.tipoDocumento;
                const btnToggles = modal.querySelectorAll('.btn-toggle-doc');
                btnToggles.forEach(btn => {
                    const activo = btn.dataset.tipo === disparador.dataset.tipoDocumento;
                    btn.classList.toggle('on', activo);
                    btn.setAttribute('aria-pressed', activo ? 'true' : 'false');
                });
            }
            const inpNumDocCli = modal.querySelector('#modal-numeroDocumento');
            if (inpNumDocCli && disparador.dataset.numeroDocumento !== undefined) {
                inpNumDocCli.value = disparador.dataset.numeroDocumento;
                const tipo = disparador.dataset.tipoDocumento || 'DNI';
                inpNumDocCli.setAttribute('maxlength', tipo === 'RUC' ? '11' : '8');
                inpNumDocCli.setAttribute('data-valida', tipo.toLowerCase());
            }
            const inpNomCli = modal.querySelector('#modal-nombreCompleto');
            if (inpNomCli && disparador.dataset.nombreCompleto !== undefined) {
                inpNomCli.value = disparador.dataset.nombreCompleto;
            }
            const inpTelCli = modal.querySelector('#modal-telefono');
            if (inpTelCli && disparador.dataset.telefono !== undefined) {
                inpTelCli.value = disparador.dataset.telefono;
            }
            const inpCorreoCli = modal.querySelector('#modal-correo');
            if (inpCorreoCli && disparador.dataset.correo !== undefined) {
                inpCorreoCli.value = disparador.dataset.correo;
            }

            // --- Proveedores ---
            const inpRucProv = modal.querySelector('#modal-ruc');
            if (inpRucProv && disparador.dataset.ruc !== undefined) {
                inpRucProv.value = disparador.dataset.ruc;
            }
            const inpRazonProv = modal.querySelector('#modal-razonSocial');
            if (inpRazonProv && disparador.dataset.razonSocial !== undefined) {
                inpRazonProv.value = disparador.dataset.razonSocial;
            }
            const inpTelProv = modal.querySelector('#modal-telefono-prov');
            if (inpTelProv && disparador.dataset.telefono !== undefined) {
                inpTelProv.value = disparador.dataset.telefono;
            }
            const inpCorreoProv = modal.querySelector('#modal-correo-prov');
            if (inpCorreoProv && disparador.dataset.correo !== undefined) {
                inpCorreoProv.value = disparador.dataset.correo;
            }
            const inpDirProv = modal.querySelector('#modal-direccion');
            if (inpDirProv && disparador.dataset.direccion !== undefined) {
                inpDirProv.value = disparador.dataset.direccion;
            }

            // --- Usuarios ---
            const inpNomUser = modal.querySelector('#modal-nombre-user');
            if (inpNomUser && disparador.dataset.nombre !== undefined) {
                inpNomUser.value = disparador.dataset.nombre;
            }
            const inpNomUsuario = modal.querySelector('#modal-nombreUsuario');
            if (inpNomUsuario && disparador.dataset.usuario !== undefined) {
                inpNomUsuario.value = disparador.dataset.usuario;
                inpNomUsuario.setAttribute('readonly', 'true');
                inpNomUsuario.classList.add('bg-gray-100', 'cursor-not-allowed');
            }
            const selRolUser = modal.querySelector('#modal-rol-user');
            if (selRolUser && disparador.dataset.rol !== undefined) {
                selRolUser.value = disparador.dataset.rol;
            }
            const contenedorClave = modal.querySelector('#modal-contenedor-clave');
            const inpClaveUser = modal.querySelector('#modal-clave-user');
            if (contenedorClave) {
                contenedorClave.style.display = 'none';
            }
            if (inpClaveUser) {
                inpClaveUser.removeAttribute('required');
                inpClaveUser.value = '';
            }

            // --- Categorías (Editar) ---
            const inpNomCat = modal.querySelector('#modal-nombre-cat');
            if (inpNomCat && disparador.dataset.nombre !== undefined) {
                inpNomCat.value = disparador.dataset.nombre;
            }
            const selPadreCat = modal.querySelector('#modal-padre-cat');
            if (selPadreCat) {
                selPadreCat.querySelectorAll('option').forEach(opt => {
                    opt.disabled = false;
                    if (disparador.dataset.id && opt.value === disparador.dataset.id) {
                        opt.disabled = true; // No puede ser su propio padre
                    }
                });
                if (disparador.dataset.padreId !== undefined) {
                    selPadreCat.value = disparador.dataset.padreId || '';
                }
            }

        } else if (modo === 'subcategoria') {
            // --- Categorías (Subcategoría) ---
            if (disparador.dataset.action) {
                form.action = disparador.dataset.action;
            } else if (form.dataset.actionOriginal) {
                form.action = form.dataset.actionOriginal;
            }
            if (h3Titulo && disparador.dataset.titulo) {
                h3Titulo.textContent = disparador.dataset.titulo;
            }
            if (spanBoton) {
                spanBoton.textContent = disparador.dataset.boton || 'Guardar categoría';
            }
            const inpNomCat = modal.querySelector('#modal-nombre-cat');
            if (inpNomCat) inpNomCat.value = '';
            const selPadreCat = modal.querySelector('#modal-padre-cat');
            if (selPadreCat) {
                selPadreCat.querySelectorAll('option').forEach(opt => opt.disabled = false);
                selPadreCat.value = disparador.dataset.padreId || '';
            }

        } else {
            // --- Modo Crear (Restauración completa) ---
            if (form.dataset.actionOriginal) {
                form.action = form.dataset.actionOriginal;
            }
            if (h3Titulo && modal.dataset.tituloOriginal) {
                h3Titulo.textContent = modal.dataset.tituloOriginal;
            }
            if (spanBoton && btnSubmit.dataset.textoOriginal) {
                spanBoton.textContent = btnSubmit.dataset.textoOriginal;
            }

            // Reset campos
            form.reset();

            // Medicamento
            const inpCodigoMed = modal.querySelector('#modal-codigo-med');
            if (inpCodigoMed) {
                inpCodigoMed.removeAttribute('readonly');
                inpCodigoMed.classList.remove('bg-gray-100', 'cursor-not-allowed');
            }
            const selCatMed = modal.querySelector('#modal-categoria-med');
            if (selCatMed) selCatMed.value = '';
            const inpStockMinMed = modal.querySelector('#modal-stockMinimo-med');
            if (inpStockMinMed) inpStockMinMed.value = '5';

            // Cliente
            const inpTipoDocCli = modal.querySelector('#modal-tipoDocumento');
            if (inpTipoDocCli) {
                inpTipoDocCli.value = 'DNI';
                const btnToggles = modal.querySelectorAll('.btn-toggle-doc');
                btnToggles.forEach(btn => {
                    const activo = btn.dataset.tipo === 'DNI';
                    btn.classList.toggle('on', activo);
                    btn.setAttribute('aria-pressed', activo ? 'true' : 'false');
                });
                const inpNumDocCli = modal.querySelector('#modal-numeroDocumento');
                if (inpNumDocCli) {
                    inpNumDocCli.setAttribute('maxlength', '8');
                    inpNumDocCli.setAttribute('data-valida', 'dni');
                    inpNumDocCli.setAttribute('placeholder', '8 dígitos para DNI');
                }
            }

            // Proveedor
            const inpRucProv = modal.querySelector('#modal-ruc');
            if (inpRucProv) {
                inpRucProv.setAttribute('maxlength', '11');
                inpRucProv.setAttribute('data-valida', 'ruc');
            }

            // Usuario
            const inpNomUsuario = modal.querySelector('#modal-nombreUsuario');
            if (inpNomUsuario) {
                inpNomUsuario.removeAttribute('readonly');
                inpNomUsuario.classList.remove('bg-gray-100', 'cursor-not-allowed');
            }
            const contenedorClave = modal.querySelector('#modal-contenedor-clave');
            const inpClaveUser = modal.querySelector('#modal-clave-user');
            if (contenedorClave) {
                contenedorClave.style.display = '';
            }
            if (inpClaveUser) {
                inpClaveUser.setAttribute('required', 'true');
            }

            // Categoría
            const selPadreCat = modal.querySelector('#modal-padre-cat');
            if (selPadreCat) {
                selPadreCat.querySelectorAll('option').forEach(opt => opt.disabled = false);
                selPadreCat.value = '';
            }

            // Existencias Iniciales (Spec 023)
            const inpMotivoExistencias = modal.querySelector('#modal-existencias-motivo');
            if (inpMotivoExistencias) {
                inpMotivoExistencias.value = 'Existencias iniciales';
            }
            const chkAcumularExistencias = modal.querySelector('#modal-existencias-confirmarAcumulacion');
            if (chkAcumularExistencias) {
                chkAcumularExistencias.checked = false;
            }
        }
    }

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

        configurarFormularioModal(modal, disparador);

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

        const primerControl = modal.querySelector('input:not([type="hidden"]):not([readonly]), select, textarea, button:not(.btn-cerrar-modal)');
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
                          modal.querySelector(`#modal-${campo}-prov`) ||
                          modal.querySelector(`#modal-existencias-${campo}`);
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
        const modal = formulario.closest('#modal-nuevo-cliente, #modal-nuevo-proveedor, #modal-nueva-categoria, #modal-nuevo-usuario, #modal-nuevo-medicamento, [data-ajax-modal]');
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
                if (datos.url && datos.url.includes('?')) {
                    window.location.href = datos.url;
                } else if (window.location.search) {
                    window.location.reload();
                } else {
                    window.location.href = datos.url || formulario.action || window.location.href;
                }
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
                } else if (mensajeGeneral.toLowerCase().includes('código') || mensajeGeneral.toLowerCase().includes('codigo')) {
                    mostrarErroresModal(modal, { codigo: mensajeGeneral });
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
