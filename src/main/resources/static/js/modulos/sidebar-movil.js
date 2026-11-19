/**
 * sidebar-movil.js — Módulo de Drawer Off-Canvas y Accesibilidad Modal (H-05)
 * Botica San Miguel (AyE)
 *
 * Gestiona el menú lateral en pantallas tablet y móviles (<= 1024px):
 * - Apertura mediante #btn-abrir-sidebar y cierre mediante #btn-cerrar-sidebar, backdrop o Escape.
 * - Accesibilidad modal completa: role="dialog", aria-modal="true", Focus Trap (Tab/Shift+Tab).
 * - Aislamiento del contenido de fondo con inert y aria-hidden="true".
 * - Bloqueo de desplazamiento del documento (body overflow: hidden).
 * - Convivencia estricta con modales de negocio (deshabilitación mutua, Escape jerárquico).
 * - Protección contra atajos de Caja POS (F2, F4, F9) mientras el drawer esté abierto.
 * - Cierre automático al hacer clic en enlaces de navegación.
 */

(function () {
  'use strict';

  function initSidebarMovil() {
    const sidebar = document.getElementById('sidebar-lateral') || document.querySelector('aside.inventario-lateral');
    const disparador = document.getElementById('btn-abrir-sidebar') || document.querySelector('[aria-controls="sidebar-lateral"]');
    const btnCerrar = document.getElementById('btn-cerrar-sidebar') || sidebar?.querySelector('[aria-label*="cerrar" i]');
    let backdrop = document.getElementById('sidebar-backdrop') || document.querySelector('.sidebar-backdrop');
    const areaFondo = document.querySelector('.inventario-area');

    // Si la vista no contiene navegación (ej. /acceso/login), salir silenciosamente sin errores
    if (!sidebar && !disparador) {
      return;
    }

    // Asegurar que el elemento backdrop exista en el DOM
    if (!backdrop) {
      backdrop = document.createElement('div');
      backdrop.id = 'sidebar-backdrop';
      backdrop.className = 'sidebar-backdrop';
      backdrop.setAttribute('aria-hidden', 'true');
      document.body.appendChild(backdrop);
    }

    let sidebarAbierto = false;
    let ultimoElementoEnfocado = null;
    let timerFocoApertura = null;

    /**
     * Determina si existe algún modal de negocio o diálogo activo en pantalla.
     */
    function hayModalNegocioActivo() {
      // 1. Modales genéricos de la aplicación
      const modalGenerico = document.querySelector('.modal.activo, dialog[open], [role="dialog"].activo:not(#sidebar-lateral)');
      if (modalGenerico && window.getComputedStyle(modalGenerico).display !== 'none') {
        return true;
      }
      // 2. Modal de cancelación de venta en Caja POS
      const modalCancelar = document.getElementById('modal-cancelar-venta-backdrop');
      if (modalCancelar && window.getComputedStyle(modalCancelar).display !== 'none') {
        return true;
      }
      // 3. Modal de descuentos en Caja POS
      const modalDescuento = document.getElementById('modal-descuento-backdrop');
      if (modalDescuento && window.getComputedStyle(modalDescuento).display !== 'none') {
        return true;
      }
      // 4. Bandera global en el body (cuando no es originada por el drawer)
      if (document.body.classList.contains('modal-abierto') && !sidebarAbierto) {
        return true;
      }
      return false;
    }

    /**
     * Actualiza la interactividad y accesibilidad del disparador según los modales activos.
     */
    function actualizarEstadoDisparador() {
      if (!disparador) return;
      if (hayModalNegocioActivo()) {
        disparador.setAttribute('disabled', 'true');
        disparador.style.pointerEvents = 'none';
        disparador.setAttribute('aria-hidden', 'true');
      } else if (!sidebarAbierto) {
        disparador.removeAttribute('disabled');
        disparador.style.pointerEvents = '';
        disparador.removeAttribute('aria-hidden');
      }
    }

    /**
     * Abre el drawer de navegación en modo diálogo modal.
     */
    function abrirSidebar() {
      if (sidebarAbierto || hayModalNegocioActivo() || !sidebar) {
        return;
      }

      ultimoElementoEnfocado = document.activeElement;

      // 1. Activar clases de visibilidad
      sidebar.classList.add('sidebar-abierto');
      sidebar.classList.remove('hidden');
      backdrop.classList.add('activo');

      // 2. Semántica ARIA modal
      sidebar.setAttribute('role', 'dialog');
      sidebar.setAttribute('aria-modal', 'true');
      if (disparador) {
        disparador.setAttribute('aria-expanded', 'true');
      }

      // 3. Aislamiento del contenido de fondo (área principal)
      if (areaFondo) {
        areaFondo.setAttribute('inert', '');
        areaFondo.setAttribute('aria-hidden', 'true');
      }

      // 4. Bloquear scroll del documento
      document.body.style.overflow = 'hidden';
      document.body.classList.add('sidebar-abierto');

      // 5. Transferir foco al interior del drawer (botón cerrar o primer enlace)
      sidebarAbierto = true;
      const controlInicial = btnCerrar || sidebar.querySelector('a[href], button');
      if (controlInicial) {
        controlInicial.focus();
      }
    }

    /**
     * Cierra el drawer de navegación y restaura el estado y foco anterior.
     */
    function cerrarSidebar() {
      if (!sidebarAbierto || !sidebar) {
        return;
      }

      // 1. Desactivar clases de visibilidad
      sidebar.classList.remove('sidebar-abierto');
      backdrop.classList.remove('activo');

      // 2. Retirar semántica modal
      sidebar.removeAttribute('role');
      sidebar.removeAttribute('aria-modal');
      if (disparador) {
        disparador.setAttribute('aria-expanded', 'false');
      }

      // 3. Retirar aislamiento del contenido de fondo
      if (areaFondo) {
        areaFondo.removeAttribute('inert');
        areaFondo.removeAttribute('aria-hidden');
      }

      // 4. Restaurar scroll del documento
      document.body.style.overflow = '';
      document.body.classList.remove('sidebar-abierto');

      sidebarAbierto = false;
      actualizarEstadoDisparador();

      // 5. Restaurar foco al disparador original del drawer
      const objetivoFoco = disparador || ultimoElementoEnfocado;
      if (objetivoFoco && typeof objetivoFoco.focus === 'function') {
        objetivoFoco.focus();
      }
    }

    // Clic en disparador para abrir/cerrar
    if (disparador) {
      disparador.addEventListener('click', (e) => {
        e.preventDefault();
        if (sidebarAbierto) {
          cerrarSidebar();
        } else {
          abrirSidebar();
        }
      });
    }

    // Clic en botón X para cerrar
    if (btnCerrar) {
      btnCerrar.addEventListener('click', (e) => {
        e.preventDefault();
        cerrarSidebar();
      });
    }

    // Clic en el backdrop exterior para cerrar
    if (backdrop) {
      backdrop.addEventListener('click', () => {
        cerrarSidebar();
      });
    }

    // Cierre automático al hacer clic en enlaces de navegación dentro del drawer
    if (sidebar) {
      sidebar.addEventListener('click', (e) => {
        const enlace = e.target.closest('a[href]');
        if (enlace && !enlace.getAttribute('href').startsWith('#')) {
          cerrarSidebar();
        }
      });
    }

    // Gestión de teclado en fase de captura (Focus Trap, Escape y aislamiento de atajos POS)
    document.addEventListener('keydown', (e) => {
      if (!sidebarAbierto) {
        return;
      }

      // Escape cierra exclusivamente el drawer
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        cerrarSidebar();
        return;
      }

      // Bloquear atajos POS (F2, F4, F9) para que no desvíen el foco al fondo mientras el drawer esté abierto
      if (e.key === 'F2' || e.key === 'F4' || e.key === 'F9') {
        e.preventDefault();
        e.stopPropagation();
        return;
      }

      // Focus Trap cíclico para Tab y Shift+Tab dentro del drawer
      if (e.key === 'Tab') {
        const elementosFocables = sidebar.querySelectorAll(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
        );
        const focablesVisibles = Array.from(elementosFocables).filter((el) => {
          const style = window.getComputedStyle(el);
          return style.display !== 'none' && style.visibility !== 'hidden';
        });

        if (focablesVisibles.length === 0) {
          e.preventDefault();
          return;
        }

        const primerFocable = focablesVisibles[0];
        const ultimoFocable = focablesVisibles[focablesVisibles.length - 1];

        if (e.shiftKey) {
          if (document.activeElement === primerFocable || !sidebar.contains(document.activeElement)) {
            e.preventDefault();
            ultimoFocable.focus();
          }
        } else {
          if (document.activeElement === ultimoFocable || !sidebar.contains(document.activeElement)) {
            e.preventDefault();
            primerFocable.focus();
          }
        }
      }
    }, true);

    // Cerrar automáticamente el drawer si la ventana se amplía a tamaño escritorio (> 1024px)
    window.addEventListener('resize', () => {
      if (window.innerWidth > 1024 && sidebarAbierto) {
        cerrarSidebar();
      }
    });

    // Observador de mutaciones para sincronizar interactividad del disparador con modales de negocio
    const observer = new MutationObserver(() => {
      actualizarEstadoDisparador();
    });
    observer.observe(document.body, {
      attributes: true,
      attributeFilter: ['class', 'style'],
      subtree: true
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initSidebarMovil);
  } else {
    initSidebarMovil();
  }
})();
