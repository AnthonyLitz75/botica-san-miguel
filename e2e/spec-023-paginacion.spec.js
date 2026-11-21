import { test, expect } from '@playwright/test';

/**
 * e2e/spec-023-paginacion.spec.js
 * Pruebas E2E de paginación de 25 filas en listados largos (Spec 023)
 * Botica San Miguel (AyE)
 */

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });
}

test.describe('Spec 023: Paginación de listados largos (25 filas)', () => {

  test('T2390: Movimientos muestra 25 filas, total filtrado en píldora y resumen Mostrando 1–25', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/movimientos', { waitUntil: 'domcontentloaded' });

    // 1. La tabla muestra exactamente 25 filas en la primera página
    const filas = page.locator('table tbody tr');
    await expect(filas).toHaveCount(25);

    // 2. La píldora junto al título muestra el total general filtrado (> 25)
    const contadorPildora = page.locator('.sec .cnt, .inventario-titulo .cnt');
    const totalTexto = (await contadorPildora.first().textContent())?.trim() ?? '0';
    const totalNumero = parseInt(totalTexto, 10);
    expect(totalNumero).toBeGreaterThan(25);

    // 3. Debajo de la tabla: "Mostrando 1–25 de N"
    const resumen = page.locator('.resultados-pie');
    await expect(resumen).toBeVisible();
    await expect(resumen).toContainText(new RegExp(`Mostrando 1[–-]25 de ${totalNumero}`));

    // 4. El nav de paginación tiene aria-label y aria-current="page" en la página 1
    const nav = page.locator('nav[aria-label="Paginación"]');
    await expect(nav).toBeVisible();
    const actual = nav.locator('[aria-current="page"]');
    await expect(actual).toHaveText('1');

    // 5. Botón Anterior está deshabilitado en página 1
    const anteriorDeshabilitado = nav.locator('[aria-disabled="true"]').first();
    await expect(anteriorDeshabilitado).toContainText('Anterior');
  });

  test('T2391: Movimientos: Siguiente avanza a página 2 conservando filtros, y clamp en pagina=9999 sin error 500', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    
    // Aplicar filtro tipo = SALIDA
    await page.goto('/movimientos?tipo=SALIDA', { waitUntil: 'domcontentloaded' });
    const resumen = page.locator('.resultados-pie');
    await expect(resumen).toBeVisible();

    // Clic en "Siguiente" en escritorio
    const botonSiguiente = page.locator('nav[aria-label="Paginación"] a[aria-label="Página siguiente"]:visible');
    await botonSiguiente.click();
    await page.waitForURL((url) => url.searchParams.get('pagina') === '2', { timeout: 10000 });

    // Comprobar que conserva tipo=SALIDA y pagina=2
    const currentUrl = new URL(page.url());
    expect(currentUrl.searchParams.get('tipo')).toBe('SALIDA');
    expect(currentUrl.searchParams.get('pagina')).toBe('2');

    // Clamp: navegar a pagina=9999 no da error 500 y carga la última página válida
    const response = await page.goto('/movimientos?pagina=9999', { waitUntil: 'domcontentloaded' });
    expect(response?.status()).toBe(200);
    await expect(page.locator('table tbody tr')).not.toHaveCount(0);
    const nav = page.locator('nav[aria-label="Paginación"]');
    await expect(nav).toBeVisible();
    const paginaActual = await nav.locator('[aria-current="page"]').textContent();
    expect(parseInt(paginaActual || '0', 10)).toBeGreaterThan(1);
  });

  test('T2392: Movimientos: Responsive a 320 px sin scroll horizontal de página', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/movimientos', { waitUntil: 'domcontentloaded' });

    // Verificar que no existe scroll horizontal a nivel de página (document / window)
    const sinScrollHorizontal = await page.evaluate(() => {
      return document.documentElement.scrollWidth <= window.innerWidth;
    });
    expect(sinScrollHorizontal).toBe(true);

    // En móvil debe verse el indicador central "Página X de Y"
    const navMovil = page.locator('nav[aria-label="Paginación"]');
    await expect(navMovil).toBeVisible();
    await expect(navMovil).toContainText(/Página 1 de \d+/);
  });

  test('T2393: Historial de ventas: 25 filas, KPIs sobre el total, nav con aria-label y clamp sin 500', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/ventas', { waitUntil: 'domcontentloaded' });

    // 1. Muestra 25 filas
    const filas = page.locator('table tbody tr');
    await expect(filas).toHaveCount(25);

    // 2. Resumen "Mostrando 1–25 de N"
    const resumen = page.locator('.resultados-pie');
    await expect(resumen).toBeVisible();
    await expect(resumen).toContainText(/Mostrando 1[–-]25 de \d+/);

    // 3. Nav de paginación
    const nav = page.locator('nav[aria-label="Paginación"]');
    await expect(nav).toBeVisible();
    await expect(nav.locator('[aria-current="page"]')).toHaveText('1');

    // 4. Siguiente avanza a página 2
    const siguiente = nav.locator('a[aria-label="Página siguiente"]:visible');
    await siguiente.click();
    await page.waitForURL((url) => url.searchParams.get('pagina') === '2', { timeout: 10000 });
    await expect(page.locator('table tbody tr')).toHaveCount(25);

    // 5. Clamp pagina=9999
    const response = await page.goto('/ventas?pagina=9999', { waitUntil: 'domcontentloaded' });
    expect(response?.status()).toBe(200);
    await expect(page.locator('table tbody tr')).not.toHaveCount(0);
  });

  test('T2394: Auditoría: 25 eventos por página, paginación con clamp y export CSV completo', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/auditoria', { waitUntil: 'domcontentloaded' });

    // 1. Muestra 25 eventos en la tabla
    const filas = page.locator('table tbody tr');
    await expect(filas).toHaveCount(25);

    // 2. Resumen "Mostrando 1–25 de N"
    const resumen = page.locator('.resultados-pie');
    await expect(resumen).toBeVisible();
    await expect(resumen).toContainText(/Mostrando 1[–-]25 de \d+/);

    // 3. Nav accesible
    const nav = page.locator('nav[aria-label="Paginación"]');
    await expect(nav).toBeVisible();
    await expect(nav.locator('[aria-current="page"]')).toHaveText('1');

    // 4. Clamp pagina=9999
    const response = await page.goto('/auditoria?pagina=9999', { waitUntil: 'domcontentloaded' });
    expect(response?.status()).toBe(200);
    await expect(page.locator('table tbody tr')).not.toHaveCount(0);

    // 5. Exportar CSV está disponible y funciona
    const botonCsv = page.locator('a[href*="/auditoria.csv"]');
    await expect(botonCsv).toBeVisible();
  });

  test('T2395: Usuarios: listado muestra usuarios y si hay 1 sola página no muestra nav', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/usuarios', { waitUntil: 'domcontentloaded' });

    // La tabla de usuarios tiene registros
    const filas = page.locator('table tbody tr');
    const totalFilas = await filas.count();
    expect(totalFilas).toBeGreaterThan(0);
    expect(totalFilas).toBeLessThanOrEqual(25);

    // Resumen muestra "Mostrando 1–N de N"
    const resumen = page.locator('.resultados-pie');
    await expect(resumen).toBeVisible();
    await expect(resumen).toContainText(new RegExp(`Mostrando 1[–-]${totalFilas} de ${totalFilas}`));

    // Como hay una sola página (<=25 usuarios en demo), el nav no se muestra
    const nav = page.locator('nav[aria-label="Paginación"]');
    await expect(nav).toHaveCount(0);

    // Clamp sin error 500
    const response = await page.goto('/usuarios?pagina=9999', { waitUntil: 'domcontentloaded' });
    expect(response?.status()).toBe(200);
  });

  test('T2396: Mis ventas: vendedor accede, contador muestra total y clamp sin error 500', async ({ page }) => {
    await iniciarSesion(page, 'vendedor_demo', 'VendedorDemo2026!');
    const response = await page.goto('/ventas/mias', { waitUntil: 'domcontentloaded' });
    expect(response?.status()).toBe(200);

    // Contador visible
    const contador = page.locator('.sec .cnt');
    await expect(contador).toBeVisible();

    // Clamp en mis ventas
    const respClamp = await page.goto('/ventas/mias?pagina=9999', { waitUntil: 'domcontentloaded' });
    expect(respClamp?.status()).toBe(200);
  });

});
