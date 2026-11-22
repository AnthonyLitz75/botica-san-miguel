import { test, expect } from '@playwright/test';

/**
 * e2e/spec-023-marca.spec.js
 * Verificación del logo y pie del menú lateral:
 * a) Tras iniciar sesión, <img> visible, cargado (naturalWidth > 0) y boundingBox dentro del cuadro blanco.
 * b) Cuadro del logo con fondo claro (luminosidad alta).
 * c) Pie dice exactamente "Sistema de Inventario" y conserva el punto verde.
 * d) En login el logo carga (naturalWidth > 0) sin iniciar sesión.
 * e) A 320 px el menú móvil/cajón muestra el logo sin generar scroll horizontal del documento.
 */

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });
}

test.describe('Ajuste 023 (Paso 7): Logo y pie del menú lateral', () => {

  test('a y b) el logo en el menú es visible, cargado, dentro del cuadro blanco con fondo claro (captura 42)', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page);
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });

    const cuadro = page.locator('#sidebar-lateral .brand .lg');
    await expect(cuadro).toBeVisible();

    // b) El cuadro del logo tiene fondo claro (color de fondo computado con luminosidad alta)
    const bgRgb = await cuadro.evaluate((el) => window.getComputedStyle(el).backgroundColor);
    const nums = bgRgb.match(/\d+/g).map(Number);
    const luminosidad = (0.299 * nums[0] + 0.587 * nums[1] + 0.114 * nums[2]) / 255;
    expect(luminosidad).toBeGreaterThan(0.8);

    // a) El <img> del logo en el menú es visible, está cargado (naturalWidth > 0) y su boundingBox queda dentro del cuadro blanco
    const logoImg = cuadro.locator('img');
    await expect(logoImg).toBeVisible();
    await expect(logoImg).toHaveAttribute('alt', 'Botica San Miguel');

    const naturalWidth = await logoImg.evaluate((img) => img.naturalWidth);
    expect(naturalWidth).toBeGreaterThan(0);

    const boxCuadro = await cuadro.boundingBox();
    const boxImg = await logoImg.boundingBox();
    expect(boxCuadro).not.toBeNull();
    expect(boxImg).not.toBeNull();

    expect(boxImg.x).toBeGreaterThanOrEqual(boxCuadro.x - 0.5);
    expect(boxImg.y).toBeGreaterThanOrEqual(boxCuadro.y - 0.5);
    expect(boxImg.x + boxImg.width).toBeLessThanOrEqual(boxCuadro.x + boxCuadro.width + 0.5);
    expect(boxImg.y + boxImg.height).toBeLessThanOrEqual(boxCuadro.y + boxCuadro.height + 0.5);

    // Generar captura 42-marca-menu.png a 1600x900
    await page.screenshot({ path: 'e2e/evidencias/visual/42-marca-menu.png', fullPage: true });
  });

  test('c) el pie del menú lateral dice exactamente "Sistema de Inventario" y conserva el punto verde', async ({ page }) => {
    await iniciarSesion(page);
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });

    const pie = page.locator('#sidebar-lateral .foot');
    await expect(pie).toBeVisible();

    const puntoVerde = pie.locator('.dot');
    await expect(puntoVerde).toBeVisible();

    const spanTexto = pie.locator('span').last();
    const texto = await spanTexto.textContent();
    expect(texto.trim()).toBe('Sistema de Inventario');

    await expect(pie).not.toContainText('v1.0');
  });

  test('d) en la pantalla de login el logo carga (naturalWidth > 0) sin iniciar sesión', async ({ page }) => {
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });

    const logoLogin = page.locator('.login-emblema');
    await expect(logoLogin).toBeVisible();
    await expect(logoLogin).toHaveAttribute('alt', 'Botica San Miguel');

    const naturalWidth = await logoLogin.evaluate((img) => img.naturalWidth);
    expect(naturalWidth).toBeGreaterThan(0);
  });

  test('e) a 320 px el menú móvil/cajón muestra el logo sin generar scroll horizontal del documento', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await iniciarSesion(page);
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });

    const btnAbrir = page.locator('#btn-abrir-sidebar');
    await btnAbrir.click();

    const sidebar = page.locator('#sidebar-lateral');
    await expect(sidebar).toHaveClass(/sidebar-abierto/);

    const logoDrawer = sidebar.locator('.brand .lg img');
    await expect(logoDrawer).toBeVisible();

    const naturalWidth = await logoDrawer.evaluate((img) => img.naturalWidth);
    expect(naturalWidth).toBeGreaterThan(0);

    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
    const clientWidth = await page.evaluate(() => document.documentElement.clientWidth);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });

});
