import { test, expect } from '@playwright/test';

test.describe('B. Autenticación y Panel Principal', () => {
  test('debe autenticar al usuario administrador y acceder al panel de control', async ({ page }) => {
    // 1. Ir a login
    await page.goto('/acceso/login');

    // 2. Ingresar credenciales demo desde variables o fallback seguro
    const usuario = process.env.DEMO_ADMIN_USER || 'admin_demo';
    const clave = process.env.DEMO_ADMIN_PASSWORD || 'AdminDemo2026!';

    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();

    // 3. Esperar navegación post-login (debe llegar a /medicamentos)
    await page.waitForURL((url) => url.pathname.includes('/medicamentos'), { timeout: 10000 });

    // 4. Verificar encabezado semántico y marca
    const tituloPrincipal = page.getByRole('heading', { level: 1, name: 'Medicamentos' });
    await expect(tituloPrincipal).toBeVisible();

    // 5. Verificar presencia de la navegación lateral
    const navegacionLateral = page.locator('aside.inventario-lateral, .inventario-lateral');
    await expect(navegacionLateral).toBeVisible();

    // 6. Captura de pantalla de la sesión activa
    await page.screenshot({ path: 'e2e/evidencias/02-dashboard-admin.png', fullPage: true });
  });
});
