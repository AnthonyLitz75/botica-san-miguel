import { test, expect } from '@playwright/test';

/**
 * e2e/spec-023-detalle-movimiento.spec.js
 * Pruebas E2E de maquetación y enlaces estructurados en Detalle de Movimiento (/movimientos/{id})
 * Spec 023 — Botica San Miguel (AyE)
 */

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });
}

test.describe('Spec 023: Detalle de Movimiento de Inventario (/movimientos/{id})', () => {

  test('T2397: Maquetación de la tarjeta de datos: una sola tarjeta, alineación dt/dd y nombre completo del responsable', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // Obtenemos un movimiento de salida (venta) desde el listado
    await page.goto('/movimientos?tipo=SALIDA', { waitUntil: 'domcontentloaded' });
    const primerEnlaceDetalle = page.locator('table tbody tr a.lnk').first();
    await expect(primerEnlaceDetalle).toBeVisible();
    const href = await primerEnlaceDetalle.getAttribute('href');
    expect(href).toMatch(/\/movimientos\/\d+/);

    await page.goto(href, { waitUntil: 'domcontentloaded' });

    // 1. Cabecera presente
    await expect(page.getByRole('heading', { level: 1, name: /Movimiento #\d+/ })).toBeVisible();

    // 2. Una sola tarjeta: dl.card.resumen-registro y sin tarjeta anidada dentro
    const tarjeta = page.locator('dl.card.resumen-registro');
    await expect(tarjeta).toBeVisible();
    const tarjetasAnidadas = tarjeta.locator('.card');
    await expect(tarjetasAnidadas).toHaveCount(0);

    // 3. Responsable muestra el nombre completo (no "Usuario #")
    const responsableDd = tarjeta.locator('dt:has-text("Usuario responsable") + dd');
    await expect(responsableDd).toBeVisible();
    const responsableTexto = await responsableDd.textContent();
    expect(responsableTexto).not.toMatch(/Usuario\s*#\d+/i);
    expect(responsableTexto?.trim().length).toBeGreaterThan(0);

    // 4. Cada <dd> está alineado horizontalmente con su <dt> (mismo x de bounding box)
    const dtList = tarjeta.locator('> div > dt');
    const ddList = tarjeta.locator('> div > dd');
    const count = await dtList.count();
    expect(count).toBeGreaterThanOrEqual(4);

    for (let i = 0; i < count; i++) {
      const dtBox = await dtList.nth(i).boundingBox();
      const ddBox = await ddList.nth(i).boundingBox();
      expect(dtBox).not.toBeNull();
      expect(ddBox).not.toBeNull();
      if (dtBox && ddBox) {
        expect(Math.abs(dtBox.x - ddBox.x)).toBeLessThanOrEqual(1);
      }
    }

    // 5. Salida lleva signo '−' (U+2212)
    const cantidadBadge = page.locator('table tbody tr td .stock-minimo-badge').first();
    await expect(cantidadBadge).toBeVisible();
    const cantidadTexto = await cantidadBadge.textContent();
    expect(cantidadTexto).toContain('\u2212');

    // 6. Generar captura 22-detalle-movimiento.png a 1600x900
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.screenshot({ path: 'e2e/evidencias/visual/22-detalle-movimiento.png' });
  });

  test('T2398: Enlace estructurado al comprobante de venta cuando el movimiento proviene de venta', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/movimientos?tipo=SALIDA', { waitUntil: 'domcontentloaded' });
    const filaVenta = page.locator('table tbody tr:has-text("Venta")').first();
    await expect(filaVenta).toBeVisible();
    const enlaceDetalle = filaVenta.locator('a.lnk');
    const href = await enlaceDetalle.getAttribute('href');

    await page.goto(href, { waitUntil: 'domcontentloaded' });

    // Enlace al comprobante de venta en el motivo
    const motivoDd = page.locator('dl.resumen-registro dt:has-text("Motivo") + dd');
    await expect(motivoDd).toBeVisible();
    const enlaceVenta = motivoDd.locator('a[href^="/ventas/"]');
    await expect(enlaceVenta).toBeVisible();
    const hrefVenta = await enlaceVenta.getAttribute('href');
    expect(hrefVenta).toMatch(/\/ventas\/\d+/);

    // Al hacer clic, navega al comprobante
    await enlaceVenta.click();
    await page.waitForURL(/\/ventas\/\d+/, { timeout: 10000 });
    await expect(page.locator('#titulo-comprobante')).toBeVisible();
  });

  test('T2399: Movimiento de existencia inicial no contiene enlace en motivo / observación', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/movimientos?tipo=ENTRADA', { waitUntil: 'domcontentloaded' });
    const filaInicial = page.locator('table tbody tr:has-text("Existencia inicial")').first();
    await expect(filaInicial).toBeVisible();
    const enlaceDetalle = filaInicial.locator('a.lnk');
    const href = await enlaceDetalle.getAttribute('href');

    await page.goto(href, { waitUntil: 'domcontentloaded' });

    const motivoDd = page.locator('dl.resumen-registro dt:has-text("Motivo") + dd');
    await expect(motivoDd).toBeVisible();
    const enlaces = motivoDd.locator('a');
    await expect(enlaces).toHaveCount(0);
    await expect(motivoDd).toContainText(/Existencias?\s+iniciales?/i);
  });

  test('T2400: Responsividad a 320 px sin scroll horizontal en la página (scrollWidth <= clientWidth)', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.setViewportSize({ width: 320, height: 600 });
    await page.goto('/movimientos', { waitUntil: 'domcontentloaded' });
    const enlaceDetalle = page.locator('table tbody tr a.lnk').first();
    const href = await enlaceDetalle.getAttribute('href');

    await page.goto(href, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('dl.resumen-registro')).toBeVisible();

    const noHorizontalScroll = await page.evaluate(() => {
      return document.documentElement.scrollWidth <= document.documentElement.clientWidth;
    });
    expect(noHorizontalScroll).toBe(true);
  });

  test('T2401: Usuario ALMACENERO no ve enlace de comprobante de venta en motivo (sin permiso)', async ({ page }) => {
    await iniciarSesion(page, 'almacen_demo', 'FarmaciaDemo2026!');
    await page.goto('/movimientos?tipo=SALIDA', { waitUntil: 'domcontentloaded' });
    const filaVenta = page.locator('table tbody tr:has-text("Venta")').first();
    await expect(filaVenta).toBeVisible();
    const enlaceDetalle = filaVenta.locator('a.lnk');
    const href = await enlaceDetalle.getAttribute('href');

    await page.goto(href, { waitUntil: 'domcontentloaded' });

    const motivoDd = page.locator('dl.resumen-registro dt:has-text("Motivo") + dd');
    await expect(motivoDd).toBeVisible();
    const enlaceVenta = motivoDd.locator('a[href^="/ventas/"]');
    await expect(enlaceVenta).toHaveCount(0);
  });
});
