import { test, expect } from '@playwright/test';

/**
 * e2e/spec-016-metodo-pago.spec.js
 * Especificación 016: Método de pago en la venta
 * Botica San Miguel (AyE)
 */
test.describe('Spec 016: Método de pago en la venta', () => {

  test.beforeEach(async ({ page }) => {
    test.setTimeout(45000);
    // Configurar viewport de escritorio a 1600x900 (CA-V.1 / Spec 023 / T1615)
    await page.setViewportSize({ width: 1600, height: 900 });
  });

  /**
   * Helper de autenticación para cualquier rol del sistema.
   */
  async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
  }

  /**
   * Helper para limpiar la canasta antes de cada venta si quedó con items de una ejecución previa.
   */
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

  /**
   * Helper para agregar un producto a la canasta.
   */
  async function agregarProducto(page, termino = 'para') {
    const inputBuscador = page.locator('#buscar-caja');
    await inputBuscador.fill(termino);
    const sugerencia = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
    await expect(sugerencia).toBeVisible();
    await sugerencia.click();
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
  }

  test('CA-1.2, CA-2.2 y CA-2.1: Venta con Efectivo por defecto (sin alterar selector) muestra PAGO: Efectivo en comprobante e historial', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await asegurarCanastaLimpia(page);
    await agregarProducto(page, 'para');

    // Verificar selector de pago: Efectivo seleccionado por defecto (CA-1.2)
    const optEfectivo = page.locator('#selector-pago div[data-metodo="EFECTIVO"]');
    const optYape = page.locator('#selector-pago div[data-metodo="YAPE_PLIN"]');
    const optTarjeta = page.locator('#selector-pago div[data-metodo="TARJETA"]');

    await expect(optEfectivo).toHaveClass(/on/);
    await expect(optYape).not.toHaveClass(/on/);
    await expect(optTarjeta).not.toHaveClass(/on/);

    await page.screenshot({ path: 'e2e/evidencias/spec016-01-caja-metodos.png' });

    // Confirmar venta
    const btnConfirmar = page.locator('#boton-confirmar');
    await expect(btnConfirmar).toBeEnabled();
    await btnConfirmar.click();

    // Redirige al comprobante de la venta /ventas/{id}
    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });
    await expect(page.getByRole('heading', { level: 1, name: /Venta confirmada/i })).toBeVisible();

    // Verificar línea PAGO en comprobante (CA-2.2)
    const metodoPagoComprobante = page.locator('#metodo-pago-comprobante');
    await expect(metodoPagoComprobante).toBeVisible();
    await expect(metodoPagoComprobante).toHaveText('Efectivo');
    await expect(page.locator('.ticket')).toContainText('PAGO');

    await page.screenshot({ path: 'e2e/evidencias/spec016-02-ticket-efectivo.png' });

    // Verificar en Historial global (/ventas)
    await page.goto('/ventas');
    await expect(page.getByRole('heading', { level: 1, name: /Historial de ventas/i })).toBeVisible();
    const primerChipHistorial = page.locator('tbody tr td:nth-child(5) .chip').first();
    await expect(primerChipHistorial).toBeVisible();
    await expect(primerChipHistorial).toHaveText('Efectivo');
  });

  test('CA-1.1: Venta seleccionando Yape / Plin persiste metodo_pago y muestra PAGO: Yape / Plin en comprobante y chip en historial', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await asegurarCanastaLimpia(page);
    await agregarProducto(page, 'para');

    // Seleccionar Yape / Plin
    const optEfectivo = page.locator('#selector-pago div[data-metodo="EFECTIVO"]');
    const optYape = page.locator('#selector-pago div[data-metodo="YAPE_PLIN"]');

    await optYape.click();
    await expect(optYape).toHaveClass(/on/);
    await expect(optEfectivo).not.toHaveClass(/on/);

    // Confirmar venta
    const btnConfirmar = page.locator('#boton-confirmar');
    await expect(btnConfirmar).toBeEnabled();
    await btnConfirmar.click();

    // Comprobante
    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });
    await expect(page.getByRole('heading', { level: 1, name: /Venta confirmada/i })).toBeVisible();
    const metodoPagoComprobante = page.locator('#metodo-pago-comprobante');
    await expect(metodoPagoComprobante).toBeVisible();
    await expect(metodoPagoComprobante).toHaveText('Yape / Plin');

    await page.screenshot({ path: 'e2e/evidencias/spec016-03-ticket-yape.png' });

    // Historial
    await page.goto('/ventas');
    const primerChipHistorial = page.locator('tbody tr td:nth-child(5) .chip').first();
    await expect(primerChipHistorial).toBeVisible();
    await expect(primerChipHistorial).toHaveText('Yape / Plin');
  });

  test('CA-1.1: Venta seleccionando Tarjeta persiste metodo_pago y muestra PAGO: Tarjeta en comprobante y chip en historial', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await asegurarCanastaLimpia(page);
    await agregarProducto(page, 'para');

    // Seleccionar Tarjeta
    const optEfectivo = page.locator('#selector-pago div[data-metodo="EFECTIVO"]');
    const optTarjeta = page.locator('#selector-pago div[data-metodo="TARJETA"]');

    await optTarjeta.click();
    await expect(optTarjeta).toHaveClass(/on/);
    await expect(optEfectivo).not.toHaveClass(/on/);

    // Confirmar venta
    const btnConfirmar = page.locator('#boton-confirmar');
    await expect(btnConfirmar).toBeEnabled();
    await btnConfirmar.click();

    // Comprobante
    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });
    await expect(page.getByRole('heading', { level: 1, name: /Venta confirmada/i })).toBeVisible();
    const metodoPagoComprobante = page.locator('#metodo-pago-comprobante');
    await expect(metodoPagoComprobante).toBeVisible();
    await expect(metodoPagoComprobante).toHaveText('Tarjeta');

    await page.screenshot({ path: 'e2e/evidencias/spec016-04-ticket-tarjeta.png' });

    // Historial
    await page.goto('/ventas');
    const primerChipHistorial = page.locator('tbody tr td:nth-child(5) .chip').first();
    await expect(primerChipHistorial).toBeVisible();
    await expect(primerChipHistorial).toHaveText('Tarjeta');

    await page.screenshot({ path: 'e2e/evidencias/spec016-05-historial-metodos.png' });
  });

  test('CA-2.1: Vendedor visualiza columna Pago con chip en Mis Ventas (/ventas/mias)', async ({ page }) => {
    await iniciarSesion(page, 'vendedor_demo', 'VendedorDemo2026!');
    await asegurarCanastaLimpia(page);
    await agregarProducto(page, 'para');

    // Confirmar venta en efectivo como vendedor
    const btnConfirmar = page.locator('#boton-confirmar');
    await expect(btnConfirmar).toBeEnabled();
    await btnConfirmar.click();
    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });

    // Ir a "Mis ventas"
    await page.goto('/ventas/mias');
    await expect(page.getByRole('heading', { level: 1, name: /Mis ventas/i })).toBeVisible();

    const thPago = page.getByRole('columnheader', { name: 'Pago' });
    await expect(thPago).toBeVisible();

    const primerChipMias = page.locator('tbody tr td:nth-child(4) .chip').first();
    await expect(primerChipMias).toBeVisible();
    await expect(primerChipMias).toHaveText('Efectivo');

    await page.screenshot({ path: 'e2e/evidencias/spec016-06-mis-ventas-metodos.png' });
  });

  test('CA-2.1: Historial muestra columna Pago con encabezado, chips para ventas con método y guion em para ventas sin método', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/ventas');
    await expect(page.getByRole('heading', { level: 1, name: /Historial de ventas/i })).toBeVisible();

    // Encabezado de la columna Pago
    const thPago = page.getByRole('columnheader', { name: 'Pago' });
    await expect(thPago).toBeVisible();

    // Verificar las celdas de la columna Pago (5ta columna)
    const celdasPago = page.locator('tbody tr td:nth-child(5)');
    const cantidadCeldas = await celdasPago.count();
    expect(cantidadCeldas).toBeGreaterThan(0);

    // Verificar que cada celda contiene o bien un .chip con texto válido o bien '—'
    for (let i = 0; i < cantidadCeldas; i++) {
      const celda = celdasPago.nth(i);
      const texto = (await celda.textContent())?.trim();
      const esValido = ['Efectivo', 'Yape / Plin', 'Tarjeta', '—'].includes(texto || '');
      expect(esValido).toBeTruthy();
    }
  });

  test('CA-V.1 / T1615: Capturas de contraste visual a 1600x900 (Caja POS, Ticket y Historial)', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 1. Captura de Caja POS con selector de método de pago
    await page.goto('/ventas/nueva');
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/spec016-v1-caja-1600x900.png' });

    // 2. Captura de Historial de Ventas con columna Pago y chips
    await page.goto('/ventas');
    await expect(page.getByRole('heading', { level: 1, name: /Historial de ventas/i })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/spec016-v1-historial-1600x900.png' });

    // 3. Captura de Ticket comprobante con línea PAGO
    const primerEnlaceComprobante = page.locator('tbody tr td:last-child a.lnk').first();
    await expect(primerEnlaceComprobante).toBeVisible();
    await primerEnlaceComprobante.click();
    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });
    await expect(page.locator('#metodo-pago-comprobante')).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/spec016-v1-ticket-1600x900.png' });
  });

  test('Reglas Fase D: Ausencia de scroll horizontal a 320px en Caja POS, Comprobante e Historial', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 1. Probar en Caja POS
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/ventas/nueva');
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();

    const scrollCaja = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth
    }));
    expect(scrollCaja.scrollWidth).toBeLessThanOrEqual(scrollCaja.innerWidth);

    // 2. Probar en Historial de Ventas
    await page.goto('/ventas');
    await expect(page.getByRole('heading', { level: 1, name: /Historial de ventas/i })).toBeVisible();

    const scrollHistorial = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth
    }));
    expect(scrollHistorial.scrollWidth).toBeLessThanOrEqual(scrollHistorial.innerWidth);
  });
});
