import { test, expect } from '@playwright/test';

/**
 * e2e/spec-023-drawer-a11y.spec.js
 * Verificación de accesibilidad del Drawer móvil (#btn-abrir-sidebar):
 * aria-controls, aria-expanded (false cerrado, true abierto) al abrir,
 * cerrar con botón X, cerrar con backdrop y cerrar con Escape.
 */

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });
}

test.describe('Spec 023: Accesibilidad del Drawer móvil (H-05 / aria-expanded)', () => {
  test.use({ viewport: { width: 768, height: 1024 } });

  test('debe mantener aria-controls y alternar aria-expanded al abrir y cerrar (botón X, backdrop, Escape)', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });

    const btnAbrir = page.locator('#btn-abrir-sidebar');
    const sidebar = page.locator('#sidebar-lateral');
    const btnCerrar = page.locator('#btn-cerrar-sidebar');
    const backdrop = page.locator('#sidebar-backdrop');

    // 1. Estado inicial cerrado
    await expect(btnAbrir).toBeVisible();
    await expect(btnAbrir).toHaveAttribute('aria-controls', 'sidebar-lateral');
    await expect(btnAbrir).toHaveAttribute('aria-expanded', 'false');
    await expect(sidebar).not.toHaveClass(/sidebar-abierto/);

    // 2. Apertura mediante clic en disparador
    await btnAbrir.click();
    await expect(btnAbrir).toHaveAttribute('aria-expanded', 'true');
    await expect(sidebar).toHaveClass(/sidebar-abierto/);
    await expect(sidebar).toHaveAttribute('role', 'dialog');

    // 3. Cierre mediante botón X (#btn-cerrar-sidebar)
    await btnCerrar.click();
    await expect(btnAbrir).toHaveAttribute('aria-expanded', 'false');
    await expect(sidebar).not.toHaveClass(/sidebar-abierto/);

    // 4. Apertura y cierre mediante clic exterior en backdrop
    await btnAbrir.click();
    await expect(btnAbrir).toHaveAttribute('aria-expanded', 'true');
    await expect(sidebar).toHaveClass(/sidebar-abierto/);

    // Clic en backdrop (forzar clic dado que cubre la pantalla)
    await backdrop.click({ position: { x: 500, y: 500 }, force: true });
    await expect(btnAbrir).toHaveAttribute('aria-expanded', 'false');
    await expect(sidebar).not.toHaveClass(/sidebar-abierto/);

    // 5. Apertura y cierre mediante tecla Escape
    await btnAbrir.click();
    await expect(btnAbrir).toHaveAttribute('aria-expanded', 'true');
    await expect(sidebar).toHaveClass(/sidebar-abierto/);

    await page.keyboard.press('Escape');
    await expect(btnAbrir).toHaveAttribute('aria-expanded', 'false');
    await expect(sidebar).not.toHaveClass(/sidebar-abierto/);
  });
});
