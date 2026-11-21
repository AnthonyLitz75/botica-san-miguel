import { test, expect } from '@playwright/test';

/**
 * e2e/spec-017-comprobante.spec.js
 * Especificación 017: Comprobante y ticket térmico con trazabilidad real
 * Botica San Miguel (AyE)
 */
test.describe('Spec 017: Comprobante y ticket térmico', () => {

  test.beforeEach(async ({ page }) => {
    test.setTimeout(45000);
    await page.setViewportSize({ width: 1600, height: 900 });
  });

  async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
  }

  async function asegurarCanastaLimpia(page) {
    await page.goto('/ventas/nueva');
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();

    const btnCancelarVenta = page.locator('#btn-cancelar-venta');
    if (await btnCancelarVenta.isEnabled()) {
      await btnCancelarVenta.click();
      const modalConfirmar = page.locator('#btn-confirmar-cancelar-venta');
      if (await modalConfirmar.isVisible()) {
        await modalConfirmar.click();
      }
      await expect(page.locator('#estado-vacio')).toBeVisible();
    }
  }

  async function agregarProducto(page, termino = 'amox') {
    const inputBuscador = page.locator('#buscar-caja');
    await inputBuscador.fill(termino);
    const sugerencia = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
    await expect(sugerencia).toBeVisible();
    await sugerencia.click();
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
  }

  async function capturarPantallaSegura(page, ruta) {
    for (let intento = 0; intento < 3; intento++) {
      try {
        await page.screenshot({ path: ruta });
        return;
      } catch (e) {
        if (intento === 2) throw e;
        await page.waitForTimeout(300);
      }
    }
  }

  test('CA-1.1, CA-1.2 y CA-4.2: comprobante muestra lote con vencimiento (Venc MM/AAAA) y línea PAGO', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await asegurarCanastaLimpia(page);
    await agregarProducto(page, 'amox');

    const btnConfirmar = page.locator('#boton-confirmar');
    await expect(btnConfirmar).toBeEnabled();
    await btnConfirmar.click();

    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });
    await expect(page.getByRole('heading', { level: 1, name: /Venta confirmada/i })).toBeVisible();

    const ticket = page.locator('#comprobante');
    await expect(ticket).toBeVisible();

    // CA-3.2: Correlativo legible
    const numeroComp = page.locator('#titulo-comprobante');
    await expect(numeroComp).toBeVisible();
    await expect(numeroComp).toHaveText(/^B001-\d{8}$/);

    // CA-1.1: Cada línea muestra Lote <número> · Venc MM/AAAA
    const lineasLoteVenc = ticket.locator('.font-mono span');
    await expect(lineasLoteVenc.first()).toBeVisible();
    const textoLinea = await lineasLoteVenc.first().textContent();
    expect(textoLinea).toMatch(/Lote\s+\S+\s+·\s+Venc\s+\d{2}\/\d{4}/);

    // CA-4.2: Línea PAGO si tiene método
    const metodoPago = page.locator('#metodo-pago-comprobante');
    await expect(metodoPago).toBeVisible();
    await expect(ticket).toContainText('PAGO');
  });

  test('CA-3.1: el comprobante y el panel informativo no contienen ningún recuadro QR ni mención a código QR', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/ventas/1');
    if (!page.url().includes('/ventas/1')) {
      // Si no existe id 1, ir al historial y abrir el primer comprobante
      await page.goto('/ventas');
      const primerEnlace = page.locator('tbody tr a[href*="/ventas/"]').first();
      await primerEnlace.click();
      await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });
    }

    // CA-3.1: sin recuadro .qr ni texto QR
    await expect(page.locator('.qr')).toHaveCount(0);
    const contenidoTicket = await page.locator('#comprobante').textContent();
    expect(contenidoTicket).not.toContain('QR');

    const contenidoPanel = await page.locator('.ann').textContent();
    expect(contenidoPanel).not.toContain('QR');
  });

  test('CA-4.1 / T1709: en vista de impresión (emulateMedia print) se ocultan sidebar, cabecera, botones, anotaciones y controles de drawer', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/ventas');
    const primerEnlace = page.locator('tbody tr a[href*="/ventas/"]').first();
    await primerEnlace.click();
    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });

    // En modo screen los controles están en el DOM
    await expect(page.locator('#comprobante')).toBeVisible();

    // Activar emulación de impresión
    await page.emulateMedia({ media: 'print' });

    // El ticket debe seguir visible para imprimir
    await expect(page.locator('#comprobante')).toBeVisible();

    // Todos los elementos de interfaz, navegación, cabecera, botones y controles móviles deben estar ocultos
    await expect(page.locator('aside.inventario-lateral')).toBeHidden();
    await expect(page.locator('header.inventario-cabecera')).toBeHidden();
    await expect(page.locator('.no-imprimir').first()).toBeHidden();
    await expect(page.locator('.ann')).toBeHidden();
    await expect(page.locator('#boton-imprimir')).toBeHidden();
    await expect(page.locator('#btn-abrir-sidebar')).toBeHidden();
    await expect(page.locator('#btn-cerrar-sidebar')).toBeHidden();
    await expect(page.locator('#sidebar-backdrop')).toBeHidden();

    // Restaurar a pantalla
    await page.emulateMedia({ media: 'screen' });
  });

  test('Reglas Fase D: Comprobante no genera scroll horizontal a 320 px', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.setViewportSize({ width: 320, height: 600 });
    await page.goto('/ventas');
    const primerEnlace = page.locator('tbody tr a[href*="/ventas/"]').first();
    await primerEnlace.click();
    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });

    const sinDesborde = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
    expect(sinDesborde).toBe(true);
  });

  test('CA-V.1 / T1711: Captura de pantalla a 1600x900 para contraste visual con 02-ticket.png', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.goto('/ventas');
    const primerEnlace = page.locator('tbody tr a[href*="/ventas/"]').first();
    await primerEnlace.click();
    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });

    await capturarPantallaSegura(page, 'e2e/evidencias/spec017-01-ticket-1600x900.png');
    await expect(page.locator('#comprobante')).toBeVisible();
  });

});
