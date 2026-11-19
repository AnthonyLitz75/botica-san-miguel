import { test, expect } from '@playwright/test';

/**
 * e2e/fase-d2b-cancelacion.spec.js — Especificación RED para H-06 (Caja POS)
 * Botica San Miguel (AyE)
 * 
 * Reemplazo de window.confirm() nativo por un modal accesible e integrado
 * (#modal-cancelar-venta-backdrop) con soporte para rechazo, cierre por Escape,
 * cierre por backdrop, protección anti-doble clic y manejo de fallos HTTP.
 */

test.describe('Fase D.2B: Confirmación de Cancelación de Venta en Caja POS (H-06)', () => {

  test.beforeEach(async ({ page }) => {
    // 1. Autenticación como admin_demo
    await page.goto('/acceso/login');
    const usuario = process.env.DEMO_ADMIN_USER || 'admin_demo';
    const clave = process.env.DEMO_ADMIN_PASSWORD || 'AdminDemo2026!';
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => url.pathname.includes('/medicamentos'));

    // 2. Navegar a la pantalla de Caja POS
    await page.goto('/ventas/nueva');
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();

    // 3. Aislamiento estricto: vaciar la preparación activa en backend sin disparar diálogos UI
    await page.evaluate(async () => {
      if (typeof window.vaciarPreparacion === 'function') {
        try {
          await window.vaciarPreparacion();
        } catch (e) {
          // Ignorar si ya está vacía
        }
      }
    });
    await page.reload();
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();
  });

  /**
   * Helper reutilizable para agregar Paracetamol 500 mg con stock a la canasta.
   */
  async function agregarMedicamento(page) {
    const inputBuscador = page.locator('#buscar-caja');
    await inputBuscador.fill('para');
    const primeraOpcion = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
    await expect(primeraOpcion).toBeVisible();
    await primeraOpcion.click();
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
    await expect(page.locator('#contador-canasta')).toContainText('producto');
  }

  /**
   * Helper reutilizable para aplicar un descuento a la primera línea de la canasta (OBS-D2B-03).
   */
  async function aplicarDescuento(page, monto = '2.00') {
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    await expect(btnEditarDesc).toBeVisible();
    await btnEditarDesc.click();

    const modalDescuento = page.locator('#modal-descuento-backdrop');
    await expect(modalDescuento).toBeVisible();

    const inputDescuento = page.locator('#input-valor-descuento');
    await inputDescuento.fill(monto);

    const btnAplicar = page.locator('#btn-aplicar-descuento');
    await btnAplicar.click();

    await expect(modalDescuento).toBeHidden();
  }

  // ==========================================================================
  // CA-01 — Canasta vacía
  // ==========================================================================
  test('CA-01: pulsar cancelar con canasta vacía no abre modal, muestra aviso y no envía DELETE', async ({ page }) => {
    await expect(page.locator('#estado-vacio')).toBeVisible();

    let deleteEnviado = false;
    page.on('request', (req) => {
      if (req.url().includes('/ventas/preparacion') && req.method() === 'DELETE') {
        deleteEnviado = true;
      }
    });

    const btnCancelarVenta = page.locator('#btn-cancelar-venta');
    await btnCancelarVenta.click();

    // El modal de confirmación no debe aparecer
    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeHidden();

    // Debe mostrarse el aviso de que la canasta ya se encuentra vacía
    const aviso = page.locator('#aviso-caja');
    await expect(aviso).toBeVisible();
    await expect(aviso).toContainText(/La canasta ya se encuentra vacía/i);

    // No debe haberse emitido ninguna petición DELETE
    expect(deleteEnviado).toBe(false);
  });

  // ==========================================================================
  // CA-02 — Apertura del modal
  // ==========================================================================
  test('CA-02: al cancelar con productos debe abrirse #modal-cancelar-venta-backdrop con rol alertdialog', async ({ page }) => {
    await agregarMedicamento(page);

    const btnCancelarVenta = page.locator('#btn-cancelar-venta');
    await btnCancelarVenta.click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();
    await expect(modalBackdrop).toHaveAttribute('role', 'alertdialog');
    await expect(modalBackdrop).toHaveAttribute('aria-modal', 'true');

    // Debe contener las acciones de continuar venta y confirmar cancelación
    const btnContinuar = modalBackdrop.getByRole('button', { name: /continuar venta|seguir en la venta/i });
    const btnConfirmar = modalBackdrop.getByRole('button', { name: /sí, cancelar venta|cancelar venta/i });
    await expect(btnContinuar).toBeVisible();
    await expect(btnConfirmar).toBeVisible();

    // Capturas de evidencia visual en los 3 viewports requeridos
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.screenshot({ path: 'e2e/evidencias/d2b-desktop-1440-modal-cancelar.png' });

    await page.setViewportSize({ width: 768, height: 1024 });
    await page.screenshot({ path: 'e2e/evidencias/d2b-tablet-768-modal-cancelar.png' });

    await page.setViewportSize({ width: 375, height: 667 });
    await page.screenshot({ path: 'e2e/evidencias/d2b-mobile-375-modal-cancelar.png' });
  });

  // ==========================================================================
  // CA-03 — Rechazo de cancelación
  // ==========================================================================
  test('CA-03: pulsar «Continuar venta» debe cerrar el modal y preservar la preparación sin enviar DELETE', async ({ page }) => {
    await agregarMedicamento(page);

    const totalInicial = await page.locator('#total-venta').textContent();
    const cantInicial = await page.locator('#contador-canasta').textContent();

    let deleteEnviado = false;
    page.on('request', (req) => {
      if (req.url().includes('/ventas/preparacion') && req.method() === 'DELETE') {
        deleteEnviado = true;
      }
    });

    await page.locator('#btn-cancelar-venta').click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnContinuar = modalBackdrop.getByRole('button', { name: /continuar venta|seguir en la venta/i });
    await btnContinuar.click();

    // El modal debe ocultarse
    await expect(modalBackdrop).toBeHidden();

    // La canasta, productos, cantidades y totales deben preservarse
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
    await expect(page.locator('#total-venta')).toHaveText(totalInicial || '');
    await expect(page.locator('#contador-canasta')).toHaveText(cantInicial || '');

    // No debe haberse emitido petición DELETE
    expect(deleteEnviado).toBe(false);
  });

  // ==========================================================================
  // CA-04 — Cierre con Escape
  // ==========================================================================
  test('CA-04: pulsar tecla Escape con el modal abierto debe cerrarlo y mantener la preparación intacta', async ({ page }) => {
    await agregarMedicamento(page);

    await page.locator('#btn-cancelar-venta').click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    await page.keyboard.press('Escape');

    await expect(modalBackdrop).toBeHidden();
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
    await expect(page.locator('#contador-canasta')).toContainText('producto');
  });

  // ==========================================================================
  // CA-05 — Cierre mediante backdrop
  // ==========================================================================
  test('CA-05: clic exterior en backdrop debe cerrar el modal sin alterar la preparación, clic interior no debe cerrarlo', async ({ page }) => {
    await agregarMedicamento(page);

    await page.locator('#btn-cancelar-venta').click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    const dialogo = page.locator('#modal-cancelar-venta-dialogo');
    await expect(modalBackdrop).toBeVisible();
    await expect(dialogo).toBeVisible();

    // Clic dentro del cuerpo del diálogo: no debe cerrarse
    await dialogo.click();
    await expect(modalBackdrop).toBeVisible();

    // Clic en la zona exterior del backdrop
    await modalBackdrop.click({ position: { x: 10, y: 10 } });
    await expect(modalBackdrop).toBeHidden();
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
  });

  // ==========================================================================
  // CA-06 — Confirmación de cancelación
  // ==========================================================================
  test('CA-06: confirmar cancelación debe enviar DELETE, vaciar la canasta y mostrar aviso de éxito', async ({ page }) => {
    await agregarMedicamento(page);

    await page.locator('#btn-cancelar-venta').click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnConfirmar = modalBackdrop.getByRole('button', { name: /sí, cancelar venta|cancelar venta/i });

    const [deleteRes] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/ventas/preparacion') && res.request().method() === 'DELETE'),
      btnConfirmar.click(),
    ]);

    expect(deleteRes.status()).toBe(200);

    // El modal debe cerrarse
    await expect(modalBackdrop).toBeHidden();

    // La canasta debe mostrar el estado vacío
    await expect(page.locator('#estado-vacio')).toBeVisible();
    await expect(page.locator('#contenedor-tabla')).toBeHidden();

    // Debe mostrarse el aviso de cancelación
    const aviso = page.locator('#aviso-caja');
    await expect(aviso).toBeVisible();
    await expect(aviso).toContainText(/Venta cancelada/i);
  });

  // ==========================================================================
  // CA-07 — Ausencia de diálogos nativos
  // ==========================================================================
  test('CA-07: cancelar venta con productos no debe invocar window.confirm() ni ningún diálogo nativo', async ({ page }) => {
    await agregarMedicamento(page);

    let dialogNativoInvocado = false;
    let mensajeDialogo = '';

    page.on('dialog', (dialog) => {
      dialogNativoInvocado = true;
      mensajeDialogo = dialog.message();
      // Se descarta para evitar congelar el test en RED
      void dialog.dismiss();
    });

    await page.locator('#btn-cancelar-venta').click();

    // El diálogo nativo del navegador NO debe haberse disparado
    expect(dialogNativoInvocado).toBe(false);

    // En su lugar, el modal integrado debe estar visible
    await expect(page.locator('#modal-cancelar-venta-backdrop')).toBeVisible();
  });

  // ==========================================================================
  // CA-08 — Protección contra doble clic
  // ==========================================================================
  test('CA-08: el botón de confirmación debe deshabilitarse durante el DELETE para evitar peticiones duplicadas', async ({ page }) => {
    await agregarMedicamento(page);

    let llamadasDelete = 0;
    await page.route('**/ventas/preparacion', async (route) => {
      if (route.request().method() === 'DELETE') {
        llamadasDelete++;
        // Demorar la respuesta para verificar el estado deshabilitado del botón
        await new Promise((resolve) => setTimeout(resolve, 800));
        await route.continue();
      } else {
        await route.continue();
      }
    });

    await page.locator('#btn-cancelar-venta').click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnConfirmar = modalBackdrop.getByRole('button', { name: /sí, cancelar venta|cancelar venta/i });

    // Primer clic
    await btnConfirmar.click({ noWaitAfter: true });

    // Durante el procesamiento debe quedar deshabilitado
    await expect(btnConfirmar).toBeDisabled();

    // Intentar segundo clic
    await btnConfirmar.click({ force: true, noWaitAfter: true }).catch(() => {});

    // Esperar finalización del vaciado
    await expect(page.locator('#estado-vacio')).toBeVisible();

    // Debe haberse enviado exactamente 1 petición DELETE
    expect(llamadasDelete).toBe(1);
  });

  // ==========================================================================
  // CA-09 — Error del DELETE
  // ==========================================================================
  test('CA-09: si el servidor retorna error 500 en DELETE, la preparación debe conservarse y mostrar alerta de error', async ({ page }) => {
    await agregarMedicamento(page);

    const totalInicial = await page.locator('#total-venta').textContent();

    // Simular error 500 en el backend al vaciar
    await page.route('**/ventas/preparacion', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ mensaje: 'Error interno simulado del servidor.' }),
        });
      } else {
        await route.continue();
      }
    });

    await page.locator('#btn-cancelar-venta').click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnConfirmar = modalBackdrop.getByRole('button', { name: /sí, cancelar venta|cancelar venta/i });
    await btnConfirmar.click();

    // La preparación no debe perderse
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
    await expect(page.locator('#total-venta')).toHaveText(totalInicial || '');

    // Debe mostrarse mensaje de error y no éxito
    const alertaError = page.locator('#texto-alerta, #zona-alertas, #aviso-caja');
    await expect(alertaError.first()).toContainText(/Error/i);
    await expect(page.locator('#aviso-caja')).not.toContainText(/Venta cancelada\. Canasta de mostrador vaciada/i);
  });

  // ==========================================================================
  // CA-10 — Resiliencia ante error HTTP 500 y Reintento desde el Modal (OBS-D2B-04)
  // ==========================================================================
  test('CA-10: ante HTTP 500 el modal debe permanecer abierto permitiendo reintentar, y cerrarse solo tras respuesta 200', async ({ page }) => {
    await agregarMedicamento(page);

    const totalInicial = await page.locator('#total-venta').textContent();
    const cantInicial = await page.locator('#contador-canasta').textContent();

    let intentosDelete = 0;
    await page.route('**/ventas/preparacion', async (route) => {
      if (route.request().method() === 'DELETE') {
        intentosDelete++;
        if (intentosDelete === 1) {
          // Primer intento: simular fallo 500 del servidor
          await route.fulfill({
            status: 500,
            contentType: 'application/json',
            body: JSON.stringify({ mensaje: 'Error interno simulado del servidor.' }),
          });
        } else {
          // Segundo intento: procesar exitosamente con el backend real
          await route.continue();
        }
      } else {
        await route.continue();
      }
    });

    // 1. Abrir el modal de cancelación
    await page.locator('#btn-cancelar-venta').click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnConfirmar = modalBackdrop.getByRole('button', { name: /sí, cancelar venta|cancelar venta/i });

    // 2. Primer intento: clic que dispara HTTP 500
    await btnConfirmar.click();

    // 3. Comprobar que ante HTTP 500 el modal permanece ABIERTO
    await expect(modalBackdrop).toBeVisible();

    // 4. Comprobar que la canasta, productos, cantidades y totales se conservan intactos
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
    await expect(page.locator('#contenedor-tabla')).toContainText('Paracetamol');
    await expect(page.locator('.stepper b')).toHaveText('1');
    await expect(page.locator('#total-venta')).toHaveText(totalInicial || '');
    await expect(page.locator('#contador-canasta')).toHaveText(cantInicial || '');

    // 5. Comprobar que el mensaje de error real es visible y no aparece notificación de éxito
    const alertaError = page.locator('#modal-cancelar-error, #texto-alerta, #zona-alertas, #aviso-caja');
    await expect(alertaError.first()).toBeVisible();
    await expect(alertaError.first()).toContainText('Error interno simulado del servidor.');
    await expect(page.locator('#aviso-caja')).not.toContainText(/Venta cancelada\. Canasta de mostrador vaciada/i);

    // 6. Comprobar que el botón de confirmación vuelve a habilitarse para permitir reintentar
    await expect(btnConfirmar).toBeEnabled();

    // 7. Segundo intento: reintentar desde el mismo modal abierto (devolverá HTTP 200)
    const [deleteRes] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/ventas/preparacion') && res.request().method() === 'DELETE'),
      btnConfirmar.click(),
    ]);

    expect(deleteRes.status()).toBe(200);

    // 8. Comprobar que tras el éxito el modal se cierra
    await expect(modalBackdrop).toBeHidden();

    // 9. Comprobar que la canasta queda vacía
    await expect(page.locator('#estado-vacio')).toBeVisible();
    await expect(page.locator('#contenedor-tabla')).toBeHidden();

    // 10. Comprobar que se presenta el aviso de éxito
    const aviso = page.locator('#aviso-caja');
    await expect(aviso).toBeVisible();
    await expect(aviso).toContainText(/Venta cancelada\. Canasta de mostrador vaciada/i);

    // 11. Comprobar que hubo exactamente 2 solicitudes DELETE
    expect(intentosDelete).toBe(2);
  });

  // ==========================================================================
  // CA-11 — Trampa de Foco: Navegación Hacia Adelante con Tab (OBS-D2B-01)
  // ==========================================================================
  test('CA-11: con modal abierto, Tab desde el último botón (#btn-confirmar-cancelar-venta) debe ciclar al primer botón (#modal-cancelar-venta-cerrar)', async ({ page }) => {
    await agregarMedicamento(page);

    await page.locator('#btn-cancelar-venta').click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnAbortar = page.locator('#btn-abortar-cancelacion');
    const btnConfirmar = page.locator('#btn-confirmar-cancelar-venta');
    const btnCerrar = page.locator('#modal-cancelar-venta-cerrar');

    // 1. Verificar foco inicial en Continuar venta
    await expect(btnAbortar).toBeFocused();

    // 2. Presionar Tab y verificar foco en Sí, cancelar venta
    await page.keyboard.press('Tab');
    await expect(btnConfirmar).toBeFocused();

    // 3. Presionar Tab desde el último elemento y verificar que cicla a la X de cierre
    await page.keyboard.press('Tab');
    await expect(btnCerrar).toBeFocused();

    // 4. Presionar Tab desde la X y verificar que vuelve a Continuar venta
    await page.keyboard.press('Tab');
    await expect(btnAbortar).toBeFocused();
  });

  // ==========================================================================
  // CA-12 — Trampa de Foco: Navegación Hacia Atrás con Shift+Tab (OBS-D2B-01)
  // ==========================================================================
  test('CA-12: con modal abierto, Shift+Tab desde el primer botón (#modal-cancelar-venta-cerrar) debe ciclar al último botón (#btn-confirmar-cancelar-venta)', async ({ page }) => {
    await agregarMedicamento(page);

    await page.locator('#btn-cancelar-venta').click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnAbortar = page.locator('#btn-abortar-cancelacion');
    const btnCerrar = page.locator('#modal-cancelar-venta-cerrar');
    const btnConfirmar = page.locator('#btn-confirmar-cancelar-venta');

    // 1. Esperar que el foco inicial asiente en Continuar venta
    await expect(btnAbortar).toBeFocused();

    // 2. Mover el foco al primer control interactivo (botón X de cierre)
    await btnCerrar.focus();
    await expect(btnCerrar).toBeFocused();

    // 3. Presionar Shift+Tab desde el primer elemento y verificar que cicla al último botón (Sí, cancelar venta)
    await page.keyboard.press('Shift+Tab');
    await expect(btnConfirmar).toBeFocused();
  });

  // ==========================================================================
  // CA-13 — Restauración de Foco al Disparador tras «Continuar venta» (OBS-D2B-02)
  // ==========================================================================
  test('CA-13: al cerrar el modal mediante «Continuar venta», el foco debe retornar a #btn-cancelar-venta', async ({ page }) => {
    await agregarMedicamento(page);

    const btnCancelarVenta = page.locator('#btn-cancelar-venta');
    await btnCancelarVenta.focus();
    await expect(btnCancelarVenta).toBeFocused();

    await btnCancelarVenta.click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnAbortar = page.locator('#btn-abortar-cancelacion');
    await expect(btnAbortar).toBeFocused();

    await btnAbortar.click();

    await expect(modalBackdrop).toBeHidden();
    await expect(btnCancelarVenta).toBeFocused();
  });

  // ==========================================================================
  // CA-14 — Restauración de Foco al Disparador tras presionar Escape (OBS-D2B-02)
  // ==========================================================================
  test('CA-14: al cerrar el modal mediante tecla Escape, el foco debe retornar a #btn-cancelar-venta', async ({ page }) => {
    await agregarMedicamento(page);

    const btnCancelarVenta = page.locator('#btn-cancelar-venta');
    await btnCancelarVenta.focus();
    await expect(btnCancelarVenta).toBeFocused();

    await btnCancelarVenta.click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnAbortar = page.locator('#btn-abortar-cancelacion');
    await expect(btnAbortar).toBeFocused();

    await page.keyboard.press('Escape');

    await expect(modalBackdrop).toBeHidden();
    await expect(btnCancelarVenta).toBeFocused();
  });

  // ==========================================================================
  // CA-15 — Restauración de Foco al Disparador tras pulsar botón X (OBS-D2B-02)
  // ==========================================================================
  test('CA-15: al cerrar el modal mediante el botón X de cierre, el foco debe retornar a #btn-cancelar-venta', async ({ page }) => {
    await agregarMedicamento(page);

    const btnCancelarVenta = page.locator('#btn-cancelar-venta');
    await btnCancelarVenta.focus();
    await expect(btnCancelarVenta).toBeFocused();

    await btnCancelarVenta.click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnCerrar = page.locator('#modal-cancelar-venta-cerrar');
    await btnCerrar.click();

    await expect(modalBackdrop).toBeHidden();
    await expect(btnCancelarVenta).toBeFocused();
  });

  // ==========================================================================
  // CA-16 — Restauración de Foco al Disparador tras Desistir post-HTTP 500 (OBS-D2B-02)
  // ==========================================================================
  test('CA-16: al desistir de la cancelación tras un error HTTP 500, el foco debe retornar a #btn-cancelar-venta', async ({ page }) => {
    await agregarMedicamento(page);

    // Simular error 500 en DELETE
    await page.route('**/ventas/preparacion', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ mensaje: 'Error interno simulado del servidor.' }),
        });
      } else {
        await route.continue();
      }
    });

    const btnCancelarVenta = page.locator('#btn-cancelar-venta');
    await btnCancelarVenta.focus();
    await expect(btnCancelarVenta).toBeFocused();

    await btnCancelarVenta.click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnConfirmar = page.locator('#btn-confirmar-cancelar-venta');
    await btnConfirmar.click();

    // El modal permanece abierto con mensaje de error (OBS-D2B-04)
    await expect(modalBackdrop).toBeVisible();
    const alertaError = page.locator('#modal-cancelar-error, #texto-alerta, #zona-alertas, #aviso-caja');
    await expect(alertaError.first()).toContainText('Error interno simulado del servidor.');

    // Desistir haciendo clic en Continuar venta
    const btnAbortar = page.locator('#btn-abortar-cancelacion');
    await expect(btnAbortar).toBeEnabled();
    await btnAbortar.click();

    await expect(modalBackdrop).toBeHidden();
    await expect(btnCancelarVenta).toBeFocused();
  });

  // ==========================================================================
  // CA-17 — Preservación de Foco en #buscar-caja tras Cancelación Exitosa (OBS-D2B-02)
  // ==========================================================================
  test('CA-17: tras una cancelación confirmada y exitosa (HTTP 200), el foco debe permanecer en #buscar-caja', async ({ page }) => {
    await agregarMedicamento(page);

    await page.locator('#btn-cancelar-venta').click();

    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnConfirmar = page.locator('#btn-confirmar-cancelar-venta');

    const [deleteRes] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/ventas/preparacion') && res.request().method() === 'DELETE'),
      btnConfirmar.click(),
    ]);

    expect(deleteRes.status()).toBe(200);

    await expect(modalBackdrop).toBeHidden();
    await expect(page.locator('#estado-vacio')).toBeVisible();

    // El foco debe residir en el buscador para iniciar la siguiente venta
    const inputBuscar = page.locator('#buscar-caja');
    await expect(inputBuscar).toBeFocused();
  });

  // ==========================================================================
  // CA-18 — Conservación del Descuento al Desistir de la Cancelación (OBS-D2B-03)
  // ==========================================================================
  test('CA-18: al desistir de la cancelación mediante «Continuar venta», la línea y los importes deben conservar el descuento aplicado', async ({ page }) => {
    // 1. Agregar Paracetamol 500 mg (P.U. S/ 8.50)
    await agregarMedicamento(page);

    // 2. Aplicar descuento de S/ 2.00 usando el flujo real del modal de descuentos
    await aplicarDescuento(page, '2.00');

    // 3. Verificar condiciones iniciales
    const spanDescuento = page.locator('.btn-editar-descuento').first();
    await expect(spanDescuento).toHaveText('- S/ 2.00');
    await expect(spanDescuento).toHaveAttribute('data-descuento', '2.00');
    await expect(page.locator('#subtotal-venta')).toHaveText('S/ 8.50');
    await expect(page.locator('#descuento-venta')).toHaveText('- S/ 2.00');
    await expect(page.locator('#total-venta')).toHaveText('S/ 6.50');

    // 4. Abrir el modal de cancelación
    let deleteEnviado = false;
    page.on('request', (req) => {
      if (req.url().includes('/ventas/preparacion') && req.method() === 'DELETE') {
        deleteEnviado = true;
      }
    });

    await page.locator('#btn-cancelar-venta').click();
    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    // 5. Pulsar «Continuar venta»
    const btnAbortar = page.locator('#btn-abortar-cancelacion');
    await btnAbortar.click();

    // 6. Comprobar que el modal desaparece
    await expect(modalBackdrop).toBeHidden();

    // 7. Repetir todas las comprobaciones del descuento y los importes
    await expect(spanDescuento).toHaveText('- S/ 2.00');
    await expect(spanDescuento).toHaveAttribute('data-descuento', '2.00');
    await expect(page.locator('#subtotal-venta')).toHaveText('S/ 8.50');
    await expect(page.locator('#descuento-venta')).toHaveText('- S/ 2.00');
    await expect(page.locator('#total-venta')).toHaveText('S/ 6.50');

    // 8. Comprobar que no se envió ninguna petición DELETE
    expect(deleteEnviado).toBe(false);
  });

  // ==========================================================================
  // CA-19 — Conservación del Descuento ante Error HTTP 500 en Cancelación (OBS-D2B-03)
  // ==========================================================================
  test('CA-19: si el servidor retorna error 500 en DELETE, la línea y los importes deben conservar el descuento aplicado', async ({ page }) => {
    // 1. Preparar la misma línea con descuento de S/ 2.00
    await agregarMedicamento(page);
    await aplicarDescuento(page, '2.00');

    // 2. Verificar condiciones iniciales
    const spanDescuento = page.locator('.btn-editar-descuento').first();
    await expect(spanDescuento).toHaveText('- S/ 2.00');
    await expect(spanDescuento).toHaveAttribute('data-descuento', '2.00');
    await expect(page.locator('#subtotal-venta')).toHaveText('S/ 8.50');
    await expect(page.locator('#descuento-venta')).toHaveText('- S/ 2.00');
    await expect(page.locator('#total-venta')).toHaveText('S/ 6.50');

    // 3. Configurar la respuesta HTTP 500 para la petición DELETE (patrón CA-09)
    let deleteInterceptado = false;
    await page.route('**/ventas/preparacion', async (route) => {
      if (route.request().method() === 'DELETE') {
        deleteInterceptado = true;
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ mensaje: 'Error interno simulado del servidor.' }),
        });
      } else {
        await route.continue();
      }
    });

    // 4. Abrir el modal y confirmar la cancelación
    await page.locator('#btn-cancelar-venta').click();
    const modalBackdrop = page.locator('#modal-cancelar-venta-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const btnConfirmar = page.locator('#btn-confirmar-cancelar-venta');
    await btnConfirmar.click();

    // 5. Verificar que el modal permanece abierto y muestra el error (OBS-D2B-04)
    await expect(modalBackdrop).toBeVisible();
    const alertaError = page.locator('#modal-cancelar-error, #texto-alerta, #zona-alertas, #aviso-caja');
    await expect(alertaError.first()).toContainText('Error interno simulado del servidor.');
    expect(deleteInterceptado).toBe(true);

    // 6. Comprobar que la línea y los importes siguen conservando el descuento
    await expect(spanDescuento).toHaveText('- S/ 2.00');
    await expect(spanDescuento).toHaveAttribute('data-descuento', '2.00');
    await expect(page.locator('#subtotal-venta')).toHaveText('S/ 8.50');
    await expect(page.locator('#descuento-venta')).toHaveText('- S/ 2.00');
    await expect(page.locator('#total-venta')).toHaveText('S/ 6.50');

    // 7. Pulsar «Continuar venta»
    const btnAbortar = page.locator('#btn-abortar-cancelacion');
    await expect(btnAbortar).toBeEnabled();
    await btnAbortar.click();

    // 8. Comprobar nuevamente el descuento, los importes y el cierre del modal
    await expect(modalBackdrop).toBeHidden();
    await expect(spanDescuento).toHaveText('- S/ 2.00');
    await expect(spanDescuento).toHaveAttribute('data-descuento', '2.00');
    await expect(page.locator('#subtotal-venta')).toHaveText('S/ 8.50');
    await expect(page.locator('#descuento-venta')).toHaveText('- S/ 2.00');
    await expect(page.locator('#total-venta')).toHaveText('S/ 6.50');
  });

});
