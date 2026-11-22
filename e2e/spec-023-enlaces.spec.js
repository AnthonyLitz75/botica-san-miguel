import { test, expect } from '@playwright/test';

/**
 * e2e/spec-023-enlaces.spec.js
 * Verificación de navegación, botón Historial por rol en comprobante y barrido de enlaces sin 403/404.
 * Botica San Miguel (AyE)
 */
test.describe('Spec 023: Enlaces y botón Historial en comprobante', () => {

  test.beforeEach(async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1440, height: 900 });
  });

  async function iniciarSesion(page, usuario, clave) {
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
  }

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

  async function agregarProducto(page, termino = 'amox') {
    const inputBuscador = page.locator('#buscar-caja');
    await inputBuscador.fill(termino);
    const sugerencia = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
    await expect(sugerencia).toBeVisible();
    await sugerencia.click();
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
  }

  test('Flujo venta ADMINISTRADOR: botón Historial en comprobante navega a /ventas (200 OK)', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await asegurarCanastaLimpia(page);
    await agregarProducto(page, 'amox');

    const btnConfirmar = page.locator('#boton-confirmar');
    await expect(btnConfirmar).toBeEnabled();
    await btnConfirmar.click();

    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });
    await expect(page.getByRole('heading', { level: 1, name: /Venta confirmada/i })).toBeVisible();

    // Botón Historial para ADMINISTRADOR
    const btnHistorial = page.locator('a.btn.s', { hasText: 'Historial' });
    await expect(btnHistorial).toBeVisible();
    await expect(btnHistorial).toHaveAttribute('href', '/ventas');

    // No debe existir el botón "Mis ventas" ni enlaces a /ventas/historial
    await expect(page.locator('a[href="/ventas/historial"]')).toHaveCount(0);
    await expect(page.locator('a.btn.s', { hasText: 'Mis ventas' })).toHaveCount(0);

    // Clic en Historial
    const [response] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/ventas') && !res.url().includes('/ventas/'), { timeout: 10000 }),
      btnHistorial.click(),
    ]);

    await page.waitForURL((url) => url.pathname === '/ventas', { timeout: 10000 });
    expect(response.status()).toBe(200);
    await expect(page.getByRole('heading', { level: 1, name: /Historial de ventas/i })).toBeVisible();
  });

  test('Flujo venta VENDEDOR: botón Mis ventas en comprobante navega a /ventas/mias (200 OK)', async ({ page }) => {
    await iniciarSesion(page, 'vendedor_demo', 'VendedorDemo2026!');
    await asegurarCanastaLimpia(page);
    await agregarProducto(page, 'amox');

    const btnConfirmar = page.locator('#boton-confirmar');
    await expect(btnConfirmar).toBeEnabled();
    await btnConfirmar.click();

    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });
    await expect(page.getByRole('heading', { level: 1, name: /Venta confirmada/i })).toBeVisible();

    // Botón Mis ventas para VENDEDOR
    const btnMisVentas = page.locator('a.btn.s', { hasText: 'Mis ventas' });
    await expect(btnMisVentas).toBeVisible();
    await expect(btnMisVentas).toHaveAttribute('href', '/ventas/mias');

    // No debe existir el botón "Historial" ni enlaces a /ventas/historial
    await expect(page.locator('a[href="/ventas/historial"]')).toHaveCount(0);
    await expect(page.locator('a[href="/ventas"]').filter({ hasText: 'Historial' })).toHaveCount(0);

    // Clic en Mis ventas
    const [response] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/ventas/mias'), { timeout: 10000 }),
      btnMisVentas.click(),
    ]);

    await page.waitForURL((url) => url.pathname === '/ventas/mias', { timeout: 10000 });
    expect(response.status()).toBe(200);
    await expect(page.getByRole('heading', { level: 1, name: /Mis ventas/i })).toBeVisible();
  });

  test('Ruta inexistente /ventas/historial devuelve pantalla 403 Forbidden por seguridad por defecto', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    const response = await page.goto('/ventas/historial');
    expect(response.status()).toBe(403);
    await expect(page.locator('body')).toContainText('403');
    await expect(page.locator('body')).toContainText(/Acceso denegado|ERROR 403/i);
  });

  const roles = [
    { nombre: 'ADMINISTRADOR', user: 'admin_demo', pass: 'AdminDemo2026!' },
    { nombre: 'VENDEDOR', user: 'vendedor_demo', pass: 'VendedorDemo2026!' },
    { nombre: 'ALMACENERO', user: 'almacen_demo', pass: 'FarmaciaDemo2026!' },
    { nombre: 'COMPRAS', user: 'compras_demo', pass: 'ComprasDemo2026!' }
  ];

  for (const r of roles) {
    test(`Barrido de enlaces: rol ${r.nombre} no devuelve 403 ni 404 en sus rutas de menú`, async ({ page }) => {
      await iniciarSesion(page, r.user, r.pass);

      // Obtener todos los enlaces del menú lateral generados para este rol
      const enlacesMenu = await page.$$eval('aside nav a.it[href]', (els) =>
        Array.from(new Set(els.map((a) => a.getAttribute('href')).filter((h) => h && h.startsWith('/') && !h.includes('logout'))))
      );
      expect(enlacesMenu.length, `Rol ${r.nombre} debe tener enlaces en el menú lateral`).toBeGreaterThan(0);

      // Visitar cada ruta y verificar 200 OK (sin 403 ni 404)
      for (const ruta of enlacesMenu) {
        const resp = await page.goto(ruta, { waitUntil: 'domcontentloaded' });
        expect(resp.status(), `Ruta ${ruta} para rol ${r.nombre} no debe ser 403 ni 404`).toBe(200);
      }
    });
  }

  test('Pantallas detalle movimiento y comprobante tienen enlaces internos válidos', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // Detalle movimiento /movimientos/1 (si existe)
    const respMov = await page.goto('/movimientos/1');
    if (respMov.status() === 200) {
      const enlaces = await page.$$eval('main a[href]', (els) => els.map((a) => a.getAttribute('href')));
      for (const href of enlaces) {
        if (href && href.startsWith('/') && !href.startsWith('/acceso/logout')) {
          const res = await page.request.get(href);
          expect(res.status()).not.toBe(403);
          expect(res.status()).not.toBe(404);
        }
      }
    }

    // Comprobante /ventas/1
    const respVenta = await page.goto('/ventas/1');
    if (respVenta.status() === 200) {
      const enlaces = await page.$$eval('main a[href]', (els) => els.map((a) => a.getAttribute('href')));
      for (const href of enlaces) {
        if (href && href.startsWith('/') && !href.startsWith('/acceso/logout')) {
          const res = await page.request.get(href);
          expect(res.status()).not.toBe(403);
          expect(res.status()).not.toBe(404);
        }
      }
    }
  });
});
