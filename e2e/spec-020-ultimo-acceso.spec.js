import { test, expect } from '@playwright/test';

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

/**
 * e2e/spec-020-ultimo-acceso.spec.js
 * Especificación 020: Último acceso de los usuarios
 * Botica San Miguel (AyE)
 */
test.describe('Spec 020: Último acceso de los usuarios', () => {

  test('CA-1.1, CA-2.1 a CA-2.3: Iniciar sesión actualiza último acceso ("Hoy"), muestra title completo y usuario nuevo muestra "Nunca"', async ({ browser, page }) => {
    test.setTimeout(60000);

    // 1. En una sesión independiente, iniciar sesión como vendedor_demo para registrar último acceso
    const contextVendedor = await browser.newContext();
    const pageVendedor = await contextVendedor.newPage();
    await pageVendedor.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await pageVendedor.getByLabel('Usuario', { exact: true }).fill('vendedor_demo');
    await pageVendedor.getByLabel('Contraseña', { exact: true }).fill('VendedorDemo2026!');
    await pageVendedor.getByRole('button', { name: /Iniciar sesión/i }).click();
    await pageVendedor.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });
    await contextVendedor.close();

    // 2. Iniciar sesión como administrador para inspeccionar el listado de usuarios
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Usuario', { exact: true }).fill('admin_demo');
    await page.getByLabel('Contraseña', { exact: true }).fill('AdminDemo2026!');
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });

    // 3. Ir a Gestión de usuarios (/usuarios) con viewport desktop a 1600x900
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.goto('/usuarios', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: /Gestión de usuarios/i })).toBeVisible();

    // 5. Verificar encabezado "Último acceso" en la tabla
    const thUltimoAcceso = page.locator('table thead th:has-text("Último acceso")');
    await expect(thUltimoAcceso).toBeVisible();

    // 6. Verificar fila de vendedor_demo: debe mostrar "Hoy HH:mm"
    const filaVendedor = page.locator('table tbody tr:has(.mono:text("vendedor_demo"))');
    await expect(filaVendedor).toBeVisible();
    const celdaAccesoVendedor = filaVendedor.locator('td.lbl');
    await expect(celdaAccesoVendedor).toBeVisible();
    await expect(celdaAccesoVendedor).toContainText(/^Hoy \d{2}:\d{2}$/);

    // CA-2.2: Verificar que el atributo title contiene la fecha completa dd/MM/yyyy HH:mm
    const titleVendedor = await celdaAccesoVendedor.getAttribute('title');
    expect(titleVendedor).not.toBeNull();
    expect(titleVendedor).toMatch(/^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/);

    // 7. Crear un usuario nuevo para comprobar CA-2.3 (usuario sin accesos muestra "Nunca")
    const sufijo = Date.now().toString().slice(-4);
    const usuarioNuevo = `user_test_${sufijo}`;

    const btnNuevo = page.locator('a[data-modal-open="modal-nuevo-usuario"]');
    await btnNuevo.click();

    const modal = page.locator('#modal-nuevo-usuario');
    await expect(modal).toBeVisible();

    await page.locator('#modal-nombre-user').fill('Usuario Prueba Nunca');
    await page.locator('#modal-nombreUsuario').fill(usuarioNuevo);
    await page.locator('#modal-clave-user').fill('ClaveTest2026!');
    await modal.locator('button[type="submit"]').click();

    await page.waitForURL((url) => url.pathname.endsWith('/usuarios'), { timeout: 15000 });

    // 8. Verificar que el usuario recién creado muestra "Nunca" en Último acceso
    const filaNuevo = page.locator(`table tbody tr:has(.mono:text("${usuarioNuevo}"))`);
    await expect(filaNuevo).toBeVisible();
    const celdaAccesoNuevo = filaNuevo.locator('td.lbl');
    await expect(celdaAccesoNuevo).toHaveText('Nunca');

    // Capturar evidencias visuales
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-020-01-usuarios-listado.png');
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-020-12-usuarios-1600x900.png');
  });

  test('Reglas Fase D y Responsivo: /usuarios a 320 px sin scroll horizontal de página, contenedor con scroll y nav único', async ({ page }) => {
    // 1. Iniciar sesión como administrador
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Usuario', { exact: true }).fill('admin_demo');
    await page.getByLabel('Contraseña', { exact: true }).fill('AdminDemo2026!');
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });

    // 2. Probar viewport móvil a 320 px
    await page.setViewportSize({ width: 320, height: 600 });
    await page.goto('/usuarios', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: /Gestión de usuarios/i })).toBeVisible();

    // 3. Regla 12: Sin scroll horizontal de página
    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
    const innerWidth = await page.evaluate(() => window.innerWidth);
    expect(scrollWidth).toBeLessThanOrEqual(innerWidth);

    // 4. Regla 12: Tabla dentro de un contenedor con scroll (.overflow-x-auto)
    const contenedorTabla = page.locator('div.overflow-x-auto:has(table)');
    await expect(contenedorTabla).toBeVisible();

    // 5. Regla 12: Único <nav class="inventario-nav">
    const navs = page.locator('nav.inventario-nav');
    await expect(navs).toHaveCount(1);
  });
});
