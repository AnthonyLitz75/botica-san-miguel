import { test, expect } from '@playwright/test';

/**
 * e2e/spec-022-historial.spec.js
 * Especificación 022: Historial de ventas alineado con Boceto 09
 * Botica San Miguel (AyE)
 */

async function capturarPantallaSegura(page, ruta, reintentos = 3) {
  for (let i = 0; i < reintentos; i++) {
    try {
      await page.screenshot({ path: ruta });
      return;
    } catch (e) {
      if (i === reintentos - 1) throw e;
      await new Promise(r => setTimeout(r, 250));
    }
  }
}

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });
}

test.describe('Spec 022: Historial de ventas', () => {

  test('T2297: KPIs (Monto total filtrado, Ticket promedio, Ventas realizadas), sin columna Estado, orden Cliente/Vendedor y captura 1600x900', async ({ page }) => {
    test.setTimeout(45000);

    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 1. Viewport desktop 1600x900
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.goto('/ventas', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: /Historial de ventas/i })).toBeVisible();

    // 2. Verificar las 3 tarjetas de KPI con sus textos exactos del boceto 09
    const kpis = page.locator('.kpis .kpi');
    await expect(kpis).toHaveCount(3);

    // Tarjeta 1: Monto total filtrado
    const tarjeta1 = kpis.nth(0);
    await expect(tarjeta1.locator('.t')).toHaveText('Monto total filtrado');
    await expect(tarjeta1.locator('.n')).toContainText('S/');
    await expect(tarjeta1.locator('.d')).toHaveText('Suma de las ventas del rango');

    // Tarjeta 2: Ticket promedio
    const tarjeta2 = kpis.nth(1);
    await expect(tarjeta2.locator('.t')).toHaveText('Ticket promedio');
    await expect(tarjeta2.locator('.n')).toContainText('S/');
    await expect(tarjeta2.locator('.d')).toHaveText('Por comprobante emitido');

    // Tarjeta 3: Ventas realizadas
    const tarjeta3 = kpis.nth(2);
    await expect(tarjeta3.locator('.t')).toHaveText('Ventas realizadas');
    await expect(tarjeta3.locator('.d')).toHaveText('En el rango filtrado');

    // Verificar que NO existe la tarjeta antigua "Comprobantes listados" ni "Anuladas"
    await expect(page.locator('.kpis')).not.toContainText('Comprobantes listados');
    await expect(page.locator('.kpis')).not.toContainText('Anuladas');

    // 3. Verificar ausencia de columna Estado y badge EMITIDA en la tabla
    await expect(page.locator('table thead th:has-text("Estado")')).toHaveCount(0);
    await expect(page.locator('table tbody .badge:has-text("EMITIDA")')).toHaveCount(0);
    await expect(page.locator('table tbody .badge:has-text("ANULADA")')).toHaveCount(0);

    // 4. Verificar orden de columnas: "Cliente" debe preceder a "Vendedor"
    const encabezados = await page.locator('table thead th').allTextContents();
    const idxCliente = encabezados.findIndex(t => t.trim().includes('Cliente'));
    const idxVendedor = encabezados.findIndex(t => t.trim().includes('Vendedor'));
    expect(idxCliente).toBeGreaterThan(-1);
    expect(idxVendedor).toBeGreaterThan(-1);
    expect(idxCliente).toBeLessThan(idxVendedor);

    // 5. Verificar que la tabla está dentro de un contenedor con scroll horizontal (patrón H-01)
    const contenedorTabla = page.locator('.overflow-x-auto, .inventario-tabla-responsive');
    await expect(contenedorTabla.first()).toBeVisible();

    // 6. Captura de pantalla a 1600x900 para contraste visual con boceto 09
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-022-01-historial-1600x900.png');
  });

  test('T2297: Responsive en móvil a 320 px sin scroll horizontal de página', async ({ page }) => {
    test.setTimeout(45000);

    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 1. Viewport ultra-estrecho de 320 px
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/ventas', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: /Historial de ventas/i })).toBeVisible();

    // 2. Comprobar que no existe scroll horizontal a nivel de página (document / window)
    const sinScrollHorizontal = await page.evaluate(() => {
      return document.documentElement.scrollWidth <= window.innerWidth;
    });
    expect(sinScrollHorizontal).toBe(true);

    // 3. Verificar que la navegación principal no está duplicada (regla Fase D)
    const navs = page.locator('nav.inventario-nav');
    await expect(navs).toHaveCount(1);

    // 4. Capturar evidencia móvil a 320 px
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-022-02-historial-320px.png');
  });

});
