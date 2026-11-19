import { test, expect } from '@playwright/test';

/**
 * e2e/spec-019-clientes-compras.spec.js
 * Especificación 019: Compras reales por cliente (caja y listado)
 * Botica San Miguel (AyE)
 */
test.describe('Spec 019: Compras reales por cliente (caja y listado)', () => {

  test.beforeEach(async ({ page }) => {
    // 1. Iniciar sesión como administrador
    await page.goto('/acceso/login');
    const usuario = process.env.DEMO_ADMIN_USER || 'admin_demo';
    const clave = process.env.DEMO_ADMIN_PASSWORD || 'AdminDemo2026!';
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login'));
  });

  test('CA-1.4 y CA-1.2: Caja POS muestra Mostrador por defecto y "Cliente nuevo" sin badge Frecuente al elegir cliente con 0 compras', async ({ page }) => {
    await page.goto('/ventas/nueva');
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();

    const nombreMostrado = page.locator('#nombre-cliente-mostrado');
    const docMostrado = page.locator('#doc-cliente-mostrado');
    const badgeMostrado = page.locator('#badge-cliente-mostrado');

    // 1. Estado inicial sin cliente (Mostrador)
    await expect(nombreMostrado).toHaveText('Público general');
    await expect(docMostrado).toHaveText('Sin registro o cliente eventual');
    await expect(badgeMostrado).toBeVisible();
    await expect(badgeMostrado).toContainText('Mostrador');

    await page.screenshot({ path: 'e2e/evidencias/spec019-01-caja-mostrador.png' });

    // 2. Seleccionar cliente con 0 compras (Alberto)
    const inputCliente = page.locator('#cliente-caja');
    const sugerenciasCliente = page.locator('#sugerencias-cliente');

    await inputCliente.fill('Alberto');
    await expect(sugerenciasCliente).toBeVisible();

    const opcionAlberto = sugerenciasCliente.locator('.o').first();
    await expect(opcionAlberto).toBeVisible();
    await opcionAlberto.click();
    await expect(sugerenciasCliente).toBeHidden();

    // Verificaciones CA-1.1 y CA-1.2
    await expect(nombreMostrado).toContainText('Alberto');
    await expect(docMostrado).toContainText('Cliente nuevo');
    await expect(docMostrado).not.toContainText('14 compras');
    await expect(badgeMostrado).toBeHidden();

    await page.screenshot({ path: 'e2e/evidencias/spec019-02-caja-cliente-nuevo.png' });
  });

  test('CA-1.1, CA-1.3 y CA-1.5: Caja POS muestra compras reales (12 compras) y badge Frecuente sin dato falso 14 compras', async ({ page }) => {
    await page.goto('/ventas/nueva');
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();

    const nombreMostrado = page.locator('#nombre-cliente-mostrado');
    const docMostrado = page.locator('#doc-cliente-mostrado');
    const badgeMostrado = page.locator('#badge-cliente-mostrado');

    const inputCliente = page.locator('#cliente-caja');
    const sugerenciasCliente = page.locator('#sugerencias-cliente');

    // Seleccionar cliente frecuente (Juan Pérez Quispe con 12 compras)
    await inputCliente.fill('Juan');
    await expect(sugerenciasCliente).toBeVisible();

    const opcionJuan = sugerenciasCliente.locator('.o').first();
    await expect(opcionJuan).toBeVisible();
    await opcionJuan.click();
    await expect(sugerenciasCliente).toBeHidden();

    // Verificaciones CA-1.1, CA-1.3 y CA-1.5
    await expect(nombreMostrado).toContainText('Juan');
    await expect(docMostrado).toContainText('12 compras');
    await expect(docMostrado).not.toContainText('14 compras');
    await expect(badgeMostrado).toBeVisible();
    await expect(badgeMostrado).toContainText('Frecuente');

    await page.screenshot({ path: 'e2e/evidencias/spec019-03-caja-cliente-frecuente.png' });
  });

  test('CA-2.1 a CA-2.3: Listado de clientes muestra columna Compras, avatar con iniciales y responsive a 320 px', async ({ page }) => {
    await page.goto('/clientes');
    await expect(page.getByRole('heading', { level: 1, name: 'Clientes' })).toBeVisible();

    // 1. Verificar encabezado "Compras"
    const encabezadoCompras = page.locator('table th:has-text("Compras")');
    await expect(encabezadoCompras).toBeVisible();

    // 2. Verificar datos de filas y avatar con iniciales
    const filas = page.locator('table tbody tr');
    await expect(filas.first()).toBeVisible();

    // Comprobar que existe Juan con 12 compras y avatar con iniciales "JP"
    const filaJuan = page.locator('table tbody tr', { hasText: 'Juan Pérez Quispe' });
    if (await filaJuan.count() > 0) {
      await expect(filaJuan.locator('.ini')).toHaveText('JP');
      await expect(filaJuan.locator('.num')).toHaveText('12');
    }

    // Comprobar que existe Alberto con 0 compras
    const filaAlberto = page.locator('table tbody tr', { hasText: 'Alberto' });
    if (await filaAlberto.count() > 0) {
      await expect(filaAlberto.locator('.num')).toHaveText('0');
    }

    await page.screenshot({ path: 'e2e/evidencias/spec019-04-clientes-listado.png' });

    // 3. CA-2.3: Búsqueda preserva el conteo de compras
    const inputBuscar = page.locator('#buscar');
    await inputBuscar.fill('Juan');
    await page.getByRole('button', { name: /Buscar/i }).click();
    await expect(page.locator('table tbody tr')).toHaveCount(1);
    await expect(page.locator('table tbody tr .num')).toHaveText('12');

    // 4. Regla Fase D: sin scroll horizontal a 320 px
    await page.setViewportSize({ width: 320, height: 600 });
    await page.goto('/clientes');
    await expect(page.getByRole('heading', { level: 1, name: 'Clientes' })).toBeVisible();

    const scrollHorizontal = await page.evaluate(() => {
      return document.documentElement.scrollWidth > window.innerWidth;
    });
    expect(scrollHorizontal).toBe(false);

    // Tabla dentro de contenedor desplazable
    const contenedorTabla = page.locator('.tabla-contenedor');
    await expect(contenedorTabla).toBeVisible();

    await page.screenshot({ path: 'e2e/evidencias/spec019-05-clientes-listado-320px.png' });
  });

});
