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
 * e2e/spec-018-datos-legibles.spec.js
 * Especificación 018: Datos legibles en listados (órdenes de compra y auditoría)
 * Botica San Miguel (AyE)
 */
test.describe('Spec 018: Datos legibles en listados', () => {

  test.beforeEach(async ({ page }) => {
    test.setTimeout(45000);
    await page.setViewportSize({ width: 1600, height: 900 });

    // Iniciar sesión como administrador
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    const usuario = process.env.DEMO_ADMIN_USER || 'admin_demo';
    const clave = process.env.DEMO_ADMIN_PASSWORD || 'AdminDemo2026!';
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => url.pathname.includes('/medicamentos'), { timeout: 15000 });
  });

  test('CA-2.1 a CA-2.5: Auditoría muestra etiquetas legibles, badges con ÉXITO y select con códigos en value', async ({ page }) => {
    await page.goto('/auditoria');
    await expect(page.getByRole('heading', { level: 1, name: 'Auditoría' })).toBeVisible();

    // 1. Verificar select de acciones: texto legible y value con código en mayúsculas
    const selectAccion = page.locator('#accion');
    await expect(selectAccion).toBeVisible();
    const opcionInicioSesion = selectAccion.locator('option[value="INICIO_SESION"]');
    await expect(opcionInicioSesion).toHaveText('Inicio de sesión');
    const opcionVentaConfirmada = selectAccion.locator('option[value="CONFIRMAR_VENTA"]');
    await expect(opcionVentaConfirmada).toHaveText('Venta confirmada');
    const opcionAccesoDenegado = selectAccion.locator('option[value="ACCESO_DENEGADO"]');
    await expect(opcionAccesoDenegado).toHaveText('Acceso denegado');

    // 2. Verificar que en la tabla no existen códigos crudos en mayúsculas con guion bajo
    const tablaAuditoria = page.locator('table');
    await expect(tablaAuditoria).toBeVisible();
    const textoTabla = await tablaAuditoria.innerText();
    expect(textoTabla).not.toMatch(/\bINICIO_SESION\b/);
    expect(textoTabla).not.toMatch(/\bACCESO_DENEGADO\b/);
    expect(textoTabla).not.toMatch(/\bCONFIRMAR_VENTA\b/);
    expect(textoTabla).not.toMatch(/\bINICIO_SESION_FALLIDO\b/);

    // 3. Si hay filas de eventos, verificar badges de resultado con ÉXITO (con tilde)
    const badgesExito = page.locator('.badge.t-ok');
    const cantidadExito = await badgesExito.count();
    if (cantidadExito > 0) {
      await expect(badgesExito.first()).toHaveText('ÉXITO');
    }
  });

  test('CA-1.1 a CA-1.3 y CA-3.1 / CA-3.2: Órdenes de compra muestra razón social, sin "Proveedor #" y con chips por estado', async ({ page }) => {
    await page.goto('/ordenes-compra');
    await expect(page.getByRole('heading', { level: 1, name: 'Órdenes de compra' })).toBeVisible();

    // 1. Verificar que NO aparece el texto literal 'Proveedor #'
    const contenidoListado = await page.content();
    expect(contenidoListado).not.toContain('Proveedor #');

    // 2. Verificar que las filas muestran la razón social
    const tablaOrdenes = page.locator('table');
    await expect(tablaOrdenes).toBeVisible();
    const nombresProveedores = page.locator('.nombre-proveedor');
    const cantidadOrdenes = await nombresProveedores.count();
    expect(cantidadOrdenes).toBeGreaterThan(0);
    const primerProveedor = await nombresProveedores.first().innerText();
    expect(primerProveedor.trim().length).toBeGreaterThan(0);
    expect(primerProveedor).not.toMatch(/^Proveedor #\d+/);

    // 3. Verificar chips de estado con conteos (CA-3.1)
    const chipTodas = page.getByRole('link', { name: /Todas ·/i });
    const chipPendientes = page.getByRole('link', { name: /Pendientes ·/i });
    const chipParciales = page.getByRole('link', { name: /Parciales ·/i });
    const chipRecibidas = page.getByRole('link', { name: /Recibidas ·/i });

    await expect(chipTodas).toBeVisible();
    await expect(chipPendientes).toBeVisible();
    await expect(chipParciales).toBeVisible();
    await expect(chipRecibidas).toBeVisible();

    // 4. Filtrar por ?estado=PENDIENTE al pulsar el chip Pendientes (CA-3.2)
    await chipPendientes.click();
    await page.waitForURL((url) => url.search.includes('estado=PENDIENTE'), { timeout: 10000 });
    expect(page.url()).toContain('estado=PENDIENTE');

    // 5. Volver a Todas
    await page.getByRole('link', { name: /Todas ·/i }).click();
    await page.waitForURL((url) => !url.search.includes('estado=PENDIENTE'), { timeout: 10000 });

    // 6. Verificar detalle de orden (CA-1.1)
    const enlaceDetalle = page.locator('.enlace-lotes').first();
    await enlaceDetalle.click();
    await expect(page.getByRole('heading', { level: 1, name: /Orden OC-/i })).toBeVisible();
    const proveedorDetalle = page.locator('dt:has-text("Proveedor asignado") + dd');
    await expect(proveedorDetalle).toBeVisible();
    const textoProveedorDetalle = await proveedorDetalle.innerText();
    expect(textoProveedorDetalle.trim().length).toBeGreaterThan(0);
    expect(textoProveedorDetalle).not.toContain('Proveedor #');
  });

  test('CA-V.1 / T1813: Capturas de contraste visual a 1600x900 (Órdenes de compra y Auditoría)', async ({ page }) => {
    // 1. Captura de Órdenes de compra a 1600x900 con chips y razón social de proveedor
    await page.goto('/ordenes-compra');
    await expect(page.getByRole('heading', { level: 1, name: 'Órdenes de compra' })).toBeVisible();
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-018-05-ordenes-compra-1600x900.png');

    // 2. Captura de Auditoría a 1600x900 con etiquetas legibles y badges con tilde
    await page.goto('/auditoria');
    await expect(page.getByRole('heading', { level: 1, name: 'Auditoría' })).toBeVisible();
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-018-06-auditoria-1600x900.png');
  });

  test('Reglas Fase D: Ausencia de scroll horizontal a 320 px en Auditoría y Órdenes de compra', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 600 });

    // 1. Auditoría a 320 px
    await page.goto('/auditoria');
    const scrollAuditoria = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
    expect(scrollAuditoria).toBeTruthy();

    // 2. Órdenes de compra a 320 px
    await page.goto('/ordenes-compra');
    const scrollOrdenes = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
    expect(scrollOrdenes).toBeTruthy();
  });
});
