import { test, expect } from '@playwright/test';

/**
 * e2e/spec-023-visual.spec.js
 * Pruebas de estilos computados, tokens y accesibilidad visual
 * Especificación 023: Alineación visual con los bocetos
 * Botica San Miguel (AyE)
 */

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });
}

test.describe('Spec 023: Fase 1 — Pruebas de estilos computados y tokens', () => {

  test('T2301: estilos computados del shell a 1600x900 (sidebar 248 px, cabecera 64 px, fondo #f3f5f4, fuente base 14 px)', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });

    // 1. Sidebar width a 1600px debe ser 248px
    const sidebar = page.locator('aside.inventario-lateral');
    await expect(sidebar).toBeVisible();
    const sidebarWidth = await sidebar.evaluate((el) => window.getComputedStyle(el).width);
    expect(sidebarWidth).toBe('248px');

    // 2. Cabecera fija a 1600px debe medir 64px de alto
    const header = page.locator('header.inventario-cabecera');
    await expect(header).toBeVisible();
    const headerHeight = await header.evaluate((el) => window.getComputedStyle(el).height);
    expect(headerHeight).toBe('64px');

    // 3. Fondo de página debe ser #f3f5f4 -> rgb(243, 245, 244)
    const bodyBg = await page.evaluate(() => window.getComputedStyle(document.body).backgroundColor);
    expect(bodyBg).toBe('rgb(243, 245, 244)');

    // 4. Fuente base del documento debe ser 14px
    const bodyFontSize = await page.evaluate(() => window.getComputedStyle(document.body).fontSize);
    expect(bodyFontSize).toBe('14px');
  });

});
