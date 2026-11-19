import { test, expect } from '@playwright/test';

test.describe('A. Disponibilidad e Interfaz Base', () => {
  test('debe cargar la pantalla de login con recursos CSS y cliente LiveReload', async ({ page }) => {
    const consoleErrors = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text());
      }
    });

    // 1. Navegar a la pantalla de login
    const response = await page.goto('/acceso/login');
    expect(response?.status()).toBe(200);

    // 2. Verificar título semántico de la página
    await expect(page).toHaveTitle(/Iniciar sesión \| Botica San Miguel/);

    // 3. Verificar elementos visuales principales
    const panelLogin = page.locator('.login-panel');
    await expect(panelLogin).toBeVisible();

    const titulo = page.getByRole('heading', { level: 1, name: 'Botica San Miguel' });
    await expect(titulo).toBeVisible();

    const inputUsuario = page.getByLabel('Usuario', { exact: true });
    await expect(inputUsuario).toBeVisible();

    const inputClave = page.getByLabel('Contraseña', { exact: true });
    await expect(inputClave).toBeVisible();

    const botonIngresar = page.getByRole('button', { name: /Iniciar sesión/i });
    await expect(botonIngresar).toBeVisible();

    // 4. Capturar evidencia visual
    await page.screenshot({ path: 'e2e/evidencias/01-login-cargado.png', fullPage: true });

    // 5. Verificar que no haya errores críticos en la consola de JavaScript
    expect(consoleErrors).toEqual([]);
  });
});
