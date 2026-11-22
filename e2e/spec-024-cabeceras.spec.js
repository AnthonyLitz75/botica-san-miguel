import { test, expect } from '@playwright/test';

/**
 * e2e/spec-024-cabeceras.spec.js
 * Verificación de seguridad de cabeceras, cookie SameSite y favicon institucional:
 * (1) la respuesta de /acceso/login trae Referrer-Policy esperado (strict-origin-when-cross-origin).
 * (2) tras cargar el login la cookie JSESSIONID tiene sameSite "Lax".
 * (3) el login tiene link[rel=icon] y su href responde 200 con image/png.
 * (4) una pantalla interior (admin, /medicamentos) también tiene link[rel=icon] respondiendo 200 con image/png.
 */

async function iniciarSesionAdmin(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
}

test.describe('Ajuste 024: Cabeceras de seguridad, cookie SameSite y Favicon', () => {

  test('(1) la respuesta de /acceso/login trae Referrer-Policy esperado', async ({ page, request }) => {
    // Verificamos tanto la respuesta directa 302 de /acceso/login como la página final /login
    const respDirecta = await request.get('/acceso/login', { maxRedirects: 0 });
    expect(respDirecta.headers()['referrer-policy']).toBe('strict-origin-when-cross-origin');

    const response = await page.goto('/acceso/login');
    expect(response).not.toBeNull();
    expect(response.headers()['referrer-policy']).toBe('strict-origin-when-cross-origin');
  });

  test('(2) tras cargar el login la cookie JSESSIONID tiene sameSite "Lax"', async ({ page }) => {
    await page.goto('/acceso/login');

    const cookies = await page.context().cookies();
    const jsessionCookie = cookies.find((c) => c.name === 'JSESSIONID');

    expect(jsessionCookie).toBeDefined();
    expect(jsessionCookie.sameSite).toBe('Lax');
  });

  test('(3) el login tiene link[rel=icon] y su href responde 200 con image/png', async ({ page, request }) => {
    await page.goto('/acceso/login');

    const favicon = page.locator('link[rel="icon"]');
    await expect(favicon).toBeAttached();

    const href = await favicon.getAttribute('href');
    expect(href).toBeTruthy();

    const iconResponse = await request.get(href);
    expect(iconResponse.status()).toBe(200);
    expect(iconResponse.headers()['content-type']).toContain('image/png');
  });

  test('(4) una pantalla interior (admin, /medicamentos) también lo tiene', async ({ page, request }) => {
    await iniciarSesionAdmin(page);
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });

    const favicon = page.locator('link[rel="icon"]');
    await expect(favicon).toBeAttached();

    const href = await favicon.getAttribute('href');
    expect(href).toBeTruthy();

    const iconResponse = await request.get(href);
    expect(iconResponse.status()).toBe(200);
    expect(iconResponse.headers()['content-type']).toContain('image/png');
  });

});
